package xyz.metiq

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import android.os.SystemClock
import android.view.View
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import xyz.metiq.audio.PcmStore
import xyz.metiq.audio.TinnitusReminderReceiver
import xyz.metiq.audio.TinnitusScheduler
import xyz.metiq.ui.home.HomeScreen
import xyz.metiq.ui.theme.MetiqTheme

private const val SPLASH_MAX_HOLD_MS = 2000L

class MainActivity : AppCompatActivity() {
    private var systemConfig by mutableStateOf<Configuration?>(null)
    private var pendingScheduledMode by mutableStateOf<String?>(null)
    private var pendingScheduledDurationMin by mutableStateOf<Int?>(null)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        systemConfig = Configuration(newConfig)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractScheduledLaunch(intent)
    }

    private fun extractScheduledLaunch(intent: Intent?) {
        val mode = intent?.getStringExtra(TinnitusReminderReceiver.EXTRA_START_TINNITUS_MODE)
        if (!mode.isNullOrBlank()) {
            val dur = intent.getIntExtra(TinnitusReminderReceiver.EXTRA_START_TINNITUS_DURATION_MIN, 30)
            pendingScheduledMode = mode
            pendingScheduledDurationMin = dur
            intent.removeExtra(TinnitusReminderReceiver.EXTRA_START_TINNITUS_MODE)
            intent.removeExtra(TinnitusReminderReceiver.EXTRA_START_TINNITUS_DURATION_MIN)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashShownAt = SystemClock.uptimeMillis()
        var contentStarted = false
        installSplashScreen().setKeepOnScreenCondition {
            (!contentStarted || !PcmStore.noiseReady) &&
                SystemClock.uptimeMillis() - splashShownAt < SPLASH_MAX_HOLD_MS
        }
        super.onCreate(savedInstanceState)
        extractScheduledLaunch(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val app = application as MetiqApp

        lifecycleScope.launch {
            val initialSettings = app.settings.flow.first()
            TinnitusScheduler.syncAlarms(
                this@MainActivity,
                initialSettings.tinnitusRemindersEnabled,
                initialSettings.tinnitusScheduleSlots,
            )
            contentStarted = true
            setContent {
                val settings by app.settings.flow.collectAsState(initial = initialSettings)
                val ratePromptVisible by app.settings.ratePromptVisible.collectAsState(initial = false)
                val scope = rememberCoroutineScope()
                val repo = app.settings
                val darkTheme = when (settings.themePreference) {
                    ThemePreference.DARK -> true
                    ThemePreference.LIGHT -> false
                    ThemePreference.SYSTEM -> isSystemInDarkTheme()
                }
                LaunchedEffect(darkTheme) {
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        isAppearanceLightStatusBars = !darkTheme
                        isAppearanceLightNavigationBars = !darkTheme
                    }
                }
                val baseConfig = systemConfig
                val localizedContext = remember(settings.languageTag, baseConfig) {
                    val config = Configuration(baseConfig ?: resources.configuration)
                    if (settings.languageTag != null) {
                        config.setLocales(LocaleList.forLanguageTags(settings.languageTag))
                    }
                    createConfigurationContext(config)
                }
                val localizedConfig = localizedContext.resources.configuration
                CompositionLocalProvider(
                    LocalContext provides localizedContext,
                    LocalConfiguration provides localizedConfig,
                    LocalLayoutDirection provides
                        if (localizedConfig.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            LayoutDirection.Rtl
                        } else {
                            LayoutDirection.Ltr
                        },
                ) {
                    MetiqTheme(darkTheme = darkTheme, dynamicColors = settings.dynamicColorsEnabled) {
                        HomeScreen(
                            settings = settings,
                            onDynamicColors = { scope.launch { repo.setDynamicColorsEnabled(it) } },
                            onWarmth = { scope.launch { repo.setWarmth(it) } },
                            onFadeSeconds = { scope.launch { repo.setFadeSeconds(it) } },
                            onTimerFadeSeconds = { scope.launch { repo.setTimerFadeSeconds(it) } },
                            onRequestAudioFocus = { scope.launch { repo.setRequestAudioFocus(it) } },
                            onThemePreference = { scope.launch { repo.setThemePreference(it) } },
                            onStartupScreen = { scope.launch { repo.setStartupScreen(it) } },
                            onBinauralVolume = { scope.launch { repo.setBinauralVolume(it) } },
                            onBinauralBand = { scope.launch { repo.setBinauralBand(it) } },
                            onTinnitusVolume = { scope.launch { repo.setTinnitusVolume(it) } },
                            onTinnitusMode = { scope.launch { repo.setTinnitusMode(it) } },
                            onSaveTinnitusCalibration = { hz, wf, bw, pan, thr, mml ->
                                scope.launch {
                                    repo.saveTinnitusCalibration(hz, wf, bw, pan, thr, mml)
                                }
                            },
                            onTinnitusAmHz = { scope.launch { repo.setTinnitusAmHz(it) } },
                            onTinnitusEdgeBoost = { scope.launch { repo.setTinnitusEdgeBoost(it) } },
                            onTinnitusStdpDelay = { scope.launch { repo.setTinnitusStdpDelayMs(it) } },
                            onTinnitusAntiHabituation = { scope.launch { repo.setTinnitusAntiHabituation(it) } },
                            onTinnitusAutoTimer = { scope.launch { repo.setTinnitusAutoTimer(it) } },
                            onTinnitusRemindersEnabled = { scope.launch { repo.setTinnitusRemindersEnabled(it) } },
                            onTinnitusScheduleSlots = { scope.launch { repo.setTinnitusScheduleSlots(it) } },
                            pendingScheduledMode = pendingScheduledMode,
                            pendingScheduledDurationMin = pendingScheduledDurationMin,
                            onConsumeScheduledLaunch = {
                                pendingScheduledMode = null
                                pendingScheduledDurationMin = null
                            },
                            onTimerPresets = { scope.launch { repo.setTimerPresetsSeconds(it) } },
                            onCustomMixes = { scope.launch { repo.setCustomMixes(it) } },
                            onLanguageTag = { scope.launch { repo.setLanguageTag(it) } },
                            ratePromptVisible = ratePromptVisible,
                            onRatePromptDismiss = { scope.launch { repo.snoozeRatePrompt() } },
                        )
                    }
                }
            }
        }
    }
}
