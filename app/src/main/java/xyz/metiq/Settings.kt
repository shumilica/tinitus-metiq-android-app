package xyz.metiq

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import kotlin.math.roundToInt

data class CustomMix(
    val name: String,
    val layers: Map<String, Float>,
)

data class TinnitusLogEntry(
    val timestampMillis: Long,
    val hz: Int,
    val thresholdPercent: Int,
    val mmlPercent: Int,
)

data class TinnitusScheduleSlot(
    val id: Int,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val modeId: String,
    val durationMinutes: Int,
)

val DEFAULT_TINNITUS_SCHEDULE_SLOTS = listOf(
    TinnitusScheduleSlot(
        id = 1,
        enabled = true,
        hour = 9,
        minute = 0,
        modeId = "tinnitus_bimodal",
        durationMinutes = 30,
    ),
    TinnitusScheduleSlot(
        id = 2,
        enabled = true,
        hour = 15,
        minute = 0,
        modeId = "tinnitus_notch",
        durationMinutes = 60,
    ),
    TinnitusScheduleSlot(
        id = 3,
        enabled = true,
        hour = 21,
        minute = 30,
        modeId = "tinnitus_sr",
        durationMinutes = 30,
    ),
)

enum class ThemePreference { SYSTEM, LIGHT, DARK }

enum class StartupScreen { HOME, NOISE, AMBIENT, BINAURAL, TINNITUS }

data class Settings(
    val dynamicColorsEnabled: Boolean,
    val timerPresetsSeconds: List<Long>,
    val languageTag: String?,
    val customMixes: List<CustomMix>,
    val warmth: Float,
    val fadeSeconds: Float,
    val timerFadeSeconds: Float,
    val requestAudioFocus: Boolean,
    val themePreference: ThemePreference,
    val binauralVolume: Float,
    val binauralBand: String?,
    val tinnitusHz: Float,
    val tinnitusWaveform: String,
    val tinnitusBandwidth: Float,
    val tinnitusPan: Float,
    val tinnitusVolume: Float,
    val tinnitusMode: String?,
    val tinnitusCalibrated: Boolean,
    val tinnitusThreshold: Float,
    val tinnitusMml: Float,
    val tinnitusAmHz: Float,
    val tinnitusEdgeBoost: Float,
    val tinnitusStdpDelayMs: Float,
    val tinnitusAntiHabituation: Boolean,
    val tinnitusAutoTimer: Boolean,
    val tinnitusHistory: List<TinnitusLogEntry>,
    val tinnitusRemindersEnabled: Boolean,
    val tinnitusScheduleSlots: List<TinnitusScheduleSlot>,
    val startupScreen: StartupScreen,
)

val DEFAULT_SETTINGS = Settings(
    dynamicColorsEnabled = false,
    timerPresetsSeconds = listOf(
        15L * 60, 30L * 60, 45L * 60, 60L * 60
    ),
    languageTag = null,
    customMixes = emptyList(),
    warmth = 0f,
    fadeSeconds = 0.5f,
    timerFadeSeconds = 2f,
    requestAudioFocus = false,
    themePreference = ThemePreference.SYSTEM,
    binauralVolume = 0.5f,
    binauralBand = null,
    tinnitusHz = 6000f,
    tinnitusWaveform = "tone",
    tinnitusBandwidth = 0.5f,
    tinnitusPan = 0f,
    tinnitusVolume = 0.35f,
    tinnitusMode = null,
    tinnitusCalibrated = false,
    tinnitusThreshold = 0.12f,
    tinnitusMml = 0.35f,
    tinnitusAmHz = 10f,
    tinnitusEdgeBoost = 0.5f,
    tinnitusStdpDelayMs = 12f,
    tinnitusAntiHabituation = true,
    tinnitusAutoTimer = true,
    tinnitusHistory = emptyList(),
    tinnitusRemindersEnabled = false,
    tinnitusScheduleSlots = DEFAULT_TINNITUS_SCHEDULE_SLOTS,
    startupScreen = StartupScreen.HOME,
)

const val MAX_TIMER_PRESETS = 4
const val MAX_CUSTOM_MIXES = 6
const val MAX_CUSTOM_MIX_NAME_LENGTH = 24
const val MAX_FADE_SECONDS = 30f
const val MAX_TINNITUS_HISTORY = 12

fun clampFadeSeconds(value: Float): Float {
    val rounded = Math.round(value.coerceIn(0f, MAX_FADE_SECONDS) * 10f) / 10f
    return rounded.coerceIn(0f, MAX_FADE_SECONDS)
}

// One mix per line as "name|id:volume,id:volume". The name is sanitized of the
// separator characters on save, so a plain split round-trips safely.
private fun encodeCustomMixes(mixes: List<CustomMix>): String =
    mixes.take(MAX_CUSTOM_MIXES).joinToString("\n") { mix ->
        val name = mix.name.replace('\n', ' ').replace('|', ' ')
            .trim().take(MAX_CUSTOM_MIX_NAME_LENGTH)
        val layers = mix.layers.entries.joinToString(",") { (id, vol) ->
            "$id:${vol.coerceIn(0f, 1f)}"
        }
        "$name|$layers"
    }

// Maps sound ids that once shipped but were later removed to their closest
// current replacement, so old saved mixes keep playing something sensible.
private val LEGACY_LAYER_IDS = mapOf("thunderstorm" to "rain")

private fun decodeCustomMixes(encoded: String): List<CustomMix> =
    encoded.lineSequence().mapNotNull { line ->
        val sep = line.indexOf('|')
        if (sep <= 0) return@mapNotNull null
        val name = line.substring(0, sep).trim()
        val layers = line.substring(sep + 1).split(',').mapNotNull { entry ->
            val id = entry.substringBefore(':').takeIf { it.isNotBlank() }
                ?.let { LEGACY_LAYER_IDS[it] ?: it }
            val vol = entry.substringAfter(':', "").toFloatOrNull()
            if (id != null && vol != null && vol > 0f) id to vol.coerceIn(0f, 1f) else null
        }.toMap()
        if (name.isNotEmpty() && layers.isNotEmpty()) CustomMix(name, layers) else null
    }.take(MAX_CUSTOM_MIXES).toList()

private fun encodeTinnitusHistory(entries: List<TinnitusLogEntry>): String =
    entries.takeLast(MAX_TINNITUS_HISTORY).joinToString("\n") { e ->
        "${e.timestampMillis}|${e.hz}|${e.thresholdPercent}|${e.mmlPercent}"
    }

private fun decodeTinnitusHistory(encoded: String): List<TinnitusLogEntry> =
    encoded.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 4) return@mapNotNull null
        val ts = parts[0].toLongOrNull() ?: return@mapNotNull null
        val hz = parts[1].toIntOrNull() ?: return@mapNotNull null
        val thr = parts[2].toIntOrNull() ?: return@mapNotNull null
        val mml = parts[3].toIntOrNull() ?: return@mapNotNull null
        TinnitusLogEntry(ts, hz, thr, mml)
    }.toList().takeLast(MAX_TINNITUS_HISTORY)

private fun encodeScheduleSlots(slots: List<TinnitusScheduleSlot>): String =
    slots.joinToString("\n") { s ->
        "${s.id}|${if (s.enabled) 1 else 0}|${s.hour.coerceIn(0, 23)}|${s.minute.coerceIn(0, 59)}|${s.modeId}|${s.durationMinutes.coerceIn(1, 480)}"
    }

internal fun decodeScheduleSlots(encoded: String?): List<TinnitusScheduleSlot> {
    if (encoded.isNullOrBlank()) return DEFAULT_TINNITUS_SCHEDULE_SLOTS
    val parsed = encoded.lineSequence().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size != 6) return@mapNotNull null
        val id = parts[0].toIntOrNull() ?: return@mapNotNull null
        val enabled = parts[1] == "1"
        val hour = parts[2].toIntOrNull()?.coerceIn(0, 23) ?: return@mapNotNull null
        val minute = parts[3].toIntOrNull()?.coerceIn(0, 59) ?: return@mapNotNull null
        val modeId = parts[4].takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val duration = parts[5].toIntOrNull()?.coerceIn(1, 480) ?: return@mapNotNull null
        TinnitusScheduleSlot(id, enabled, hour, minute, modeId, duration)
    }.toList()
    return if (parsed.size == DEFAULT_TINNITUS_SCHEDULE_SLOTS.size) parsed else DEFAULT_TINNITUS_SCHEDULE_SLOTS
}

val SUPPORTED_LANGUAGE_TAGS: List<String> = listOf("en", "it", "es", "fr", "pt", "pl", "zh", "ar")

// Rate-prompt policy: only nudge once the user has clearly stuck around, and
// re-nudge sparingly after a swipe-away so it never feels nagging.
private const val RATE_MIN_LAUNCHES = 4
private const val RATE_MIN_AGE_MILLIS = 3L * 24 * 60 * 60 * 1000 // 3 days since first launch
private const val RATE_REPROMPT_MILLIS = 21L * 24 * 60 * 60 * 1000 // 3 weeks after a dismissal

private val Context.dataStore by preferencesDataStore(name = "metiq_settings")

private object Keys {
    val DYNAMIC_COLORS_ENABLED = booleanPreferencesKey("dynamic_colors_enabled")
    val WARMTH = floatPreferencesKey("warmth")
    val FADE_SECONDS = floatPreferencesKey("fade_seconds")
    val TIMER_FADE_SECONDS = floatPreferencesKey("timer_fade_seconds")
    val REQUEST_AUDIO_FOCUS = booleanPreferencesKey("request_audio_focus")
    val THEME_PREFERENCE = stringPreferencesKey("theme_preference")
    val STARTUP_SCREEN = stringPreferencesKey("startup_screen")
    val BINAURAL_VOLUME = floatPreferencesKey("binaural_volume")
    val BINAURAL_BAND = stringPreferencesKey("binaural_band")
    val TINNITUS_HZ = floatPreferencesKey("tinnitus_hz")
    val TINNITUS_WAVEFORM = stringPreferencesKey("tinnitus_waveform")
    val TINNITUS_BANDWIDTH = floatPreferencesKey("tinnitus_bandwidth")
    val TINNITUS_PAN = floatPreferencesKey("tinnitus_pan")
    val TINNITUS_VOLUME = floatPreferencesKey("tinnitus_volume")
    val TINNITUS_MODE = stringPreferencesKey("tinnitus_mode")
    val TINNITUS_CALIBRATED = booleanPreferencesKey("tinnitus_calibrated")
    val TINNITUS_THRESHOLD = floatPreferencesKey("tinnitus_threshold")
    val TINNITUS_MML = floatPreferencesKey("tinnitus_mml")
    val TINNITUS_AM_HZ = floatPreferencesKey("tinnitus_am_hz")
    val TINNITUS_EDGE_BOOST = floatPreferencesKey("tinnitus_edge_boost")
    val TINNITUS_STDP_DELAY_MS = floatPreferencesKey("tinnitus_stdp_delay_ms")
    val TINNITUS_ANTI_HABITUATION = booleanPreferencesKey("tinnitus_anti_habituation")
    val TINNITUS_AUTO_TIMER = booleanPreferencesKey("tinnitus_auto_timer")
    val TINNITUS_HISTORY = stringPreferencesKey("tinnitus_history")
    val TINNITUS_REMINDERS_ENABLED = booleanPreferencesKey("tinnitus_reminders_enabled")
    val TINNITUS_SCHEDULE_SLOTS = stringPreferencesKey("tinnitus_schedule_slots")
    val TIMER_PRESETS = stringPreferencesKey("timer_presets")
    val CUSTOM_MIXES = stringPreferencesKey("custom_mixes")
    val LANGUAGE_TAG = stringPreferencesKey("language_tag")
    val RATE_FIRST_LAUNCH = longPreferencesKey("rate_first_launch_millis")
    val RATE_LAUNCH_COUNT = intPreferencesKey("rate_launch_count")
    val RATE_LAST_PROMPT = longPreferencesKey("rate_last_prompt_millis")
}

class SettingsRepository(context: Context) {
    private val store = context.applicationContext.dataStore

    val flow: Flow<Settings> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs -> prefs.toSettings() }

    suspend fun setDynamicColorsEnabled(enabled: Boolean) {
        store.edit { it[Keys.DYNAMIC_COLORS_ENABLED] = enabled }
    }

    suspend fun setWarmth(warmth: Float) {
        store.edit { it[Keys.WARMTH] = warmth.coerceIn(0f, 1f) }
    }

    suspend fun setFadeSeconds(seconds: Float) {
        store.edit { it[Keys.FADE_SECONDS] = clampFadeSeconds(seconds) }
    }

    suspend fun setTimerFadeSeconds(seconds: Float) {
        store.edit { it[Keys.TIMER_FADE_SECONDS] = clampFadeSeconds(seconds) }
    }

    suspend fun setRequestAudioFocus(enabled: Boolean) {
        store.edit { it[Keys.REQUEST_AUDIO_FOCUS] = enabled }
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        store.edit { it[Keys.THEME_PREFERENCE] = preference.name }
    }

    suspend fun setStartupScreen(screen: StartupScreen) {
        store.edit { it[Keys.STARTUP_SCREEN] = screen.name }
    }

    suspend fun setBinauralVolume(volume: Float) {
        store.edit { it[Keys.BINAURAL_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setBinauralBand(band: String?) {
        store.edit {
            if (band == null) it.remove(Keys.BINAURAL_BAND)
            else it[Keys.BINAURAL_BAND] = band
        }
    }

    suspend fun setTinnitusHz(hz: Float) {
        store.edit { it[Keys.TINNITUS_HZ] = hz.coerceIn(100f, 16000f) }
    }

    suspend fun setTinnitusWaveform(waveform: String) {
        store.edit { it[Keys.TINNITUS_WAVEFORM] = waveform }
    }

    suspend fun setTinnitusBandwidth(bandwidth: Float) {
        store.edit { it[Keys.TINNITUS_BANDWIDTH] = bandwidth.coerceIn(0f, 1f) }
    }

    suspend fun setTinnitusPan(pan: Float) {
        store.edit { it[Keys.TINNITUS_PAN] = pan.coerceIn(-1f, 1f) }
    }

    suspend fun setTinnitusVolume(volume: Float) {
        store.edit { it[Keys.TINNITUS_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setTinnitusMode(mode: String?) {
        store.edit {
            if (mode == null) it.remove(Keys.TINNITUS_MODE)
            else it[Keys.TINNITUS_MODE] = mode
        }
    }

    suspend fun saveTinnitusCalibration(
        hz: Float,
        waveform: String,
        bandwidth: Float,
        pan: Float,
        threshold: Float,
        mml: Float,
    ) {
        val clampedHz = hz.coerceIn(100f, 16000f)
        val clampedThr = threshold.coerceIn(0.01f, 1f)
        val clampedMml = mml.coerceIn(clampedThr, 1f)
        store.edit { prefs ->
            prefs[Keys.TINNITUS_HZ] = clampedHz
            prefs[Keys.TINNITUS_WAVEFORM] = waveform
            prefs[Keys.TINNITUS_BANDWIDTH] = bandwidth.coerceIn(0f, 1f)
            prefs[Keys.TINNITUS_PAN] = pan.coerceIn(-1f, 1f)
            prefs[Keys.TINNITUS_THRESHOLD] = clampedThr
            prefs[Keys.TINNITUS_MML] = clampedMml
            prefs[Keys.TINNITUS_VOLUME] = clampedMml
            prefs[Keys.TINNITUS_CALIBRATED] = true
            val existing = prefs[Keys.TINNITUS_HISTORY]?.let(::decodeTinnitusHistory).orEmpty()
            val entry = TinnitusLogEntry(
                timestampMillis = System.currentTimeMillis(),
                hz = clampedHz.roundToInt(),
                thresholdPercent = (clampedThr * 100f).roundToInt(),
                mmlPercent = (clampedMml * 100f).roundToInt(),
            )
            prefs[Keys.TINNITUS_HISTORY] = encodeTinnitusHistory(existing + entry)
        }
    }

    suspend fun setTinnitusAmHz(amHz: Float) {
        store.edit { it[Keys.TINNITUS_AM_HZ] = if (amHz >= 25f) 40f else 10f }
    }

    suspend fun setTinnitusEdgeBoost(edgeBoost: Float) {
        store.edit { it[Keys.TINNITUS_EDGE_BOOST] = edgeBoost.coerceIn(0f, 1f) }
    }

    suspend fun setTinnitusStdpDelayMs(delayMs: Float) {
        store.edit { it[Keys.TINNITUS_STDP_DELAY_MS] = delayMs.coerceIn(5f, 20f) }
    }

    suspend fun setTinnitusAntiHabituation(enabled: Boolean) {
        store.edit { it[Keys.TINNITUS_ANTI_HABITUATION] = enabled }
    }

    suspend fun setTinnitusAutoTimer(enabled: Boolean) {
        store.edit { it[Keys.TINNITUS_AUTO_TIMER] = enabled }
    }

    suspend fun setTinnitusRemindersEnabled(enabled: Boolean) {
        store.edit { it[Keys.TINNITUS_REMINDERS_ENABLED] = enabled }
    }

    suspend fun setTinnitusScheduleSlots(slots: List<TinnitusScheduleSlot>) {
        store.edit { it[Keys.TINNITUS_SCHEDULE_SLOTS] = encodeScheduleSlots(slots) }
    }

    suspend fun setTimerPresetsSeconds(presets: List<Long>) {
        val capped = presets.take(MAX_TIMER_PRESETS).filter { it > 0L }
        store.edit { it[Keys.TIMER_PRESETS] = capped.joinToString(",") }
    }

    suspend fun setCustomMixes(mixes: List<CustomMix>) {
        store.edit { it[Keys.CUSTOM_MIXES] = encodeCustomMixes(mixes) }
    }

    suspend fun setLanguageTag(tag: String?) {
        store.edit {
            if (tag == null) it.remove(Keys.LANGUAGE_TAG)
            else it[Keys.LANGUAGE_TAG] = tag
        }
        applyLanguageTag(tag)
    }

    // Recomputed on every emission (each launch increment) against the wall clock.
    val ratePromptVisible: Flow<Boolean> = store.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }.map { prefs ->
        val now = System.currentTimeMillis()
        val firstLaunch = prefs[Keys.RATE_FIRST_LAUNCH] ?: now
        val launchCount = prefs[Keys.RATE_LAUNCH_COUNT] ?: 0
        val lastPrompt = prefs[Keys.RATE_LAST_PROMPT] ?: 0L
        val minLaunches = if (BuildConfig.DEBUG) 0 else RATE_MIN_LAUNCHES
        val minAgeMillis = if (BuildConfig.DEBUG) 0L else RATE_MIN_AGE_MILLIS
        launchCount >= minLaunches &&
            now - firstLaunch >= minAgeMillis &&
            (lastPrompt == 0L || now - lastPrompt >= RATE_REPROMPT_MILLIS)
    }

    suspend fun registerLaunch() {
        val now = System.currentTimeMillis()
        store.edit { prefs ->
            if (prefs[Keys.RATE_FIRST_LAUNCH] == null) prefs[Keys.RATE_FIRST_LAUNCH] = now
            prefs[Keys.RATE_LAUNCH_COUNT] = (prefs[Keys.RATE_LAUNCH_COUNT] ?: 0) + 1
        }
    }

    // Dismissing or acting on the note snoozes it until the reprompt interval, so it
    // always resurfaces later rather than being permanently opted out.
    suspend fun snoozeRatePrompt() {
        store.edit { it[Keys.RATE_LAST_PROMPT] = System.currentTimeMillis() }
    }

    private fun Preferences.toSettings(): Settings {
        val dynamicColors = this[Keys.DYNAMIC_COLORS_ENABLED]
            ?: DEFAULT_SETTINGS.dynamicColorsEnabled
        val warmth = (this[Keys.WARMTH] ?: DEFAULT_SETTINGS.warmth).coerceIn(0f, 1f)
        val presets = this[Keys.TIMER_PRESETS]?.split(',')?.mapNotNull { it.toLongOrNull() }
            ?.filter { it > 0L }?.take(MAX_TIMER_PRESETS)?.ifEmpty { null }
            ?: DEFAULT_SETTINGS.timerPresetsSeconds
        val languageTag = this[Keys.LANGUAGE_TAG]?.takeIf { it in SUPPORTED_LANGUAGE_TAGS }
        val customMixes = this[Keys.CUSTOM_MIXES]?.let(::decodeCustomMixes).orEmpty()
        val fadeSeconds = this[Keys.FADE_SECONDS]?.let(::clampFadeSeconds)
            ?: DEFAULT_SETTINGS.fadeSeconds
        val timerFadeSeconds = this[Keys.TIMER_FADE_SECONDS]?.let(::clampFadeSeconds)
            ?: DEFAULT_SETTINGS.timerFadeSeconds
        val requestAudioFocus = this[Keys.REQUEST_AUDIO_FOCUS]
            ?: DEFAULT_SETTINGS.requestAudioFocus
        val themePreference = this[Keys.THEME_PREFERENCE]
            ?.let { runCatching { ThemePreference.valueOf(it) }.getOrNull() }
            ?: DEFAULT_SETTINGS.themePreference
        val binauralVolume = (this[Keys.BINAURAL_VOLUME] ?: DEFAULT_SETTINGS.binauralVolume)
            .coerceIn(0f, 1f)
        val binauralBand = this[Keys.BINAURAL_BAND]
        val tinnitusHz = (this[Keys.TINNITUS_HZ] ?: DEFAULT_SETTINGS.tinnitusHz)
            .coerceIn(100f, 16000f)
        val tinnitusWaveform = this[Keys.TINNITUS_WAVEFORM] ?: DEFAULT_SETTINGS.tinnitusWaveform
        val tinnitusBandwidth = (this[Keys.TINNITUS_BANDWIDTH] ?: DEFAULT_SETTINGS.tinnitusBandwidth)
            .coerceIn(0f, 1f)
        val tinnitusPan = (this[Keys.TINNITUS_PAN] ?: DEFAULT_SETTINGS.tinnitusPan)
            .coerceIn(-1f, 1f)
        val tinnitusVolume = (this[Keys.TINNITUS_VOLUME] ?: DEFAULT_SETTINGS.tinnitusVolume)
            .coerceIn(0f, 1f)
        val tinnitusMode = this[Keys.TINNITUS_MODE]
        val tinnitusCalibrated = this[Keys.TINNITUS_CALIBRATED] ?: DEFAULT_SETTINGS.tinnitusCalibrated
        val tinnitusThreshold = (this[Keys.TINNITUS_THRESHOLD] ?: DEFAULT_SETTINGS.tinnitusThreshold)
            .coerceIn(0.01f, 1f)
        val tinnitusMml = (this[Keys.TINNITUS_MML] ?: DEFAULT_SETTINGS.tinnitusMml)
            .coerceIn(tinnitusThreshold, 1f)
        val tinnitusAmHz = this[Keys.TINNITUS_AM_HZ] ?: DEFAULT_SETTINGS.tinnitusAmHz
        val tinnitusEdgeBoost = (this[Keys.TINNITUS_EDGE_BOOST] ?: DEFAULT_SETTINGS.tinnitusEdgeBoost)
            .coerceIn(0f, 1f)
        val tinnitusStdpDelayMs = (this[Keys.TINNITUS_STDP_DELAY_MS] ?: DEFAULT_SETTINGS.tinnitusStdpDelayMs)
            .coerceIn(5f, 20f)
        val tinnitusAntiHabituation = this[Keys.TINNITUS_ANTI_HABITUATION]
            ?: DEFAULT_SETTINGS.tinnitusAntiHabituation
        val tinnitusAutoTimer = this[Keys.TINNITUS_AUTO_TIMER] ?: DEFAULT_SETTINGS.tinnitusAutoTimer
        val tinnitusHistory = this[Keys.TINNITUS_HISTORY]?.let(::decodeTinnitusHistory).orEmpty()
        val tinnitusRemindersEnabled = this[Keys.TINNITUS_REMINDERS_ENABLED]
            ?: DEFAULT_SETTINGS.tinnitusRemindersEnabled
        val tinnitusScheduleSlots = decodeScheduleSlots(this[Keys.TINNITUS_SCHEDULE_SLOTS])
        val startupScreen = this[Keys.STARTUP_SCREEN]
            ?.let { runCatching { StartupScreen.valueOf(it) }.getOrNull() }
            ?: DEFAULT_SETTINGS.startupScreen
        return Settings(
            dynamicColorsEnabled = dynamicColors,
            timerPresetsSeconds = presets,
            languageTag = languageTag,
            customMixes = customMixes,
            warmth = warmth,
            fadeSeconds = fadeSeconds,
            timerFadeSeconds = timerFadeSeconds,
            requestAudioFocus = requestAudioFocus,
            themePreference = themePreference,
            binauralVolume = binauralVolume,
            binauralBand = binauralBand,
            tinnitusHz = tinnitusHz,
            tinnitusWaveform = tinnitusWaveform,
            tinnitusBandwidth = tinnitusBandwidth,
            tinnitusPan = tinnitusPan,
            tinnitusVolume = tinnitusVolume,
            tinnitusMode = tinnitusMode,
            tinnitusCalibrated = tinnitusCalibrated,
            tinnitusThreshold = tinnitusThreshold,
            tinnitusMml = tinnitusMml,
            tinnitusAmHz = tinnitusAmHz,
            tinnitusEdgeBoost = tinnitusEdgeBoost,
            tinnitusStdpDelayMs = tinnitusStdpDelayMs,
            tinnitusAntiHabituation = tinnitusAntiHabituation,
            tinnitusAutoTimer = tinnitusAutoTimer,
            tinnitusHistory = tinnitusHistory,
            tinnitusRemindersEnabled = tinnitusRemindersEnabled,
            tinnitusScheduleSlots = tinnitusScheduleSlots,
            startupScreen = startupScreen,
        )
    }
}

fun applyLanguageTag(tag: String?) {
    val locales = if (tag.isNullOrBlank()) {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(tag)
    }
    AppCompatDelegate.setApplicationLocales(locales)
}
