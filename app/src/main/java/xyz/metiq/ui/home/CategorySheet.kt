package xyz.metiq.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.metiq.DEFAULT_TINNITUS_SCHEDULE_SLOTS
import xyz.metiq.R
import xyz.metiq.TinnitusLogEntry
import xyz.metiq.TinnitusScheduleSlot
import xyz.metiq.ui.theme.Inter
import xyz.metiq.ui.theme.LocalMetiqColors

internal const val DEFAULT_SOUND_LEVEL = 0.5f

@Composable
internal fun CategorySheet(
    category: SoundCategory,
    levels: Map<String, Float>,
    onToggle: (String) -> Unit,
    onLevel: (String, Float) -> Unit,
    onLevelSettled: (String) -> Unit,
    onDismiss: () -> Unit,
    tinnitusHz: Float = 6000f,
    tinnitusWaveform: String = "tone",
    tinnitusBandwidth: Float = 0.5f,
    tinnitusPan: Float = 0f,
    tinnitusThreshold: Float = 0.12f,
    tinnitusMml: Float = 0.35f,
    tinnitusCalibrated: Boolean = false,
    tinnitusAmHz: Float = 10f,
    tinnitusEdgeBoost: Float = 0.5f,
    tinnitusStdpDelayMs: Float = 12f,
    tinnitusAntiHabituation: Boolean = true,
    tinnitusAutoTimer: Boolean = true,
    tinnitusHistory: List<TinnitusLogEntry> = emptyList(),
    tinnitusRemindersEnabled: Boolean = false,
    tinnitusScheduleSlots: List<TinnitusScheduleSlot> = DEFAULT_TINNITUS_SCHEDULE_SLOTS,
    tinnitusAuditioning: Boolean = false,
    onTinnitusAuditionPlay: (overrideHz: Float, pulsed: Boolean, volume: Float, wf: String, bw: Float, p: Float) -> Unit = { _, _, _, _, _, _ -> },
    onTinnitusAuditionStop: () -> Unit = {},
    onSaveTinnitusCalibration: (hz: Float, waveform: String, bandwidth: Float, pan: Float, threshold: Float, mml: Float) -> Unit = { _, _, _, _, _, _ -> },
    onTinnitusAmHzChange: (Float) -> Unit = {},
    onTinnitusEdgeBoostChange: (Float) -> Unit = {},
    onTinnitusStdpDelayChange: (Float) -> Unit = {},
    onTinnitusAntiHabituationChange: (Boolean) -> Unit = {},
    onTinnitusAutoTimerChange: (Boolean) -> Unit = {},
    onTinnitusRemindersEnabledChange: (Boolean) -> Unit = {},
    onTinnitusScheduleSlotsChange: (List<TinnitusScheduleSlot>) -> Unit = {},
    onStartScheduledTinnitusSession: (modeId: String, durationMinutes: Int) -> Unit = { _, _ -> },
) {
    val ids = category.soundIds()
    val rows: @Composable ColumnScope.() -> Unit = {
        val selectedBand = levels.keys.firstOrNull { it in BINAURAL_BAND_IDS }
        val selectedTinnitusMode = levels.keys.firstOrNull { it in TINNITUS_MODE_IDS }
        orderedSoundIds().filter { it in ids }.forEach { id ->
            SoundRow(
                id = id,
                level = levels[id],
                onToggle = onToggle,
                onLevel = onLevel,
                onLevelSettled = onLevelSettled,
                dimmed = (category == SoundCategory.BINAURAL && selectedBand != null && selectedBand != id) ||
                    (category == SoundCategory.TINNITUS && selectedTinnitusMode != null && selectedTinnitusMode != id),
            )
        }
    }
    when (category) {
        SoundCategory.NOISE -> SoundSheet(
            title = stringResource(R.string.category_noise),
            subtitle = countSubtitle(ids, levels),
            onDismiss = onDismiss,
            content = rows,
        )
        SoundCategory.AMBIENT -> SoundSheet(
            title = stringResource(R.string.sheet_title_ambient),
            subtitle = countSubtitle(ids, levels),
            onDismiss = onDismiss,
            content = rows,
        )
        SoundCategory.BINAURAL -> SoundSheet(
            title = stringResource(R.string.category_binaural),
            subtitle = stringResource(R.string.sheet_binaural_single),
            onDismiss = onDismiss,
            notice = {
                SheetNotice(
                    icon = Icons.Outlined.Headphones,
                    text = stringResource(R.string.binaural_headphone_notice),
                )
            },
            content = rows,
        )
        SoundCategory.TINNITUS -> {
            // Default to Tab 1 ("1. My Tinnitus Profile") if not yet calibrated,
            // or Tab 2 ("2. Therapies & Schedule") if already calibrated!
            var selectedTab by remember(tinnitusCalibrated) {
                mutableIntStateOf(if (tinnitusCalibrated) 1 else 0)
            }
            SoundSheet(
                title = stringResource(R.string.category_tinnitus),
                subtitle = stringResource(R.string.sheet_tinnitus_subtitle),
                onDismiss = onDismiss,
                notice = {
                    SheetNotice(
                        icon = Icons.Outlined.Headphones,
                        text = stringResource(R.string.tinnitus_headphone_notice),
                    )
                },
                content = {
                    TinnitusTabSelector(
                        selectedTab = selectedTab,
                        calibrated = tinnitusCalibrated,
                        onSelectTab = { tab ->
                            onTinnitusAuditionStop()
                            selectedTab = tab
                        },
                    )
                    if (selectedTab == 0) {
                        TinnitusProfileTabContent(
                            hz = tinnitusHz,
                            waveform = tinnitusWaveform,
                            bandwidth = tinnitusBandwidth,
                            pan = tinnitusPan,
                            threshold = tinnitusThreshold,
                            mml = tinnitusMml,
                            calibrated = tinnitusCalibrated,
                            history = tinnitusHistory,
                            auditioning = tinnitusAuditioning,
                            onAuditionPlay = onTinnitusAuditionPlay,
                            onAuditionStop = onTinnitusAuditionStop,
                            onSaveCalibration = onSaveTinnitusCalibration,
                            onOpenTherapiesTab = { selectedTab = 1 },
                        )
                    } else {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.tinnitus_modes_header),
                            color = LocalMetiqColors.current.textSecondary,
                            style = TextStyle(
                                fontFamily = Inter,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        )
                        rows()
                        Spacer(Modifier.height(8.dp))
                        TinnitusTherapiesHeader(
                            calibratedHz = tinnitusHz,
                            threshold = tinnitusThreshold,
                            mml = tinnitusMml,
                            amHz = tinnitusAmHz,
                            edgeBoost = tinnitusEdgeBoost,
                            stdpDelayMs = tinnitusStdpDelayMs,
                            antiHabituation = tinnitusAntiHabituation,
                            autoTimer = tinnitusAutoTimer,
                            remindersEnabled = tinnitusRemindersEnabled,
                            scheduleSlots = tinnitusScheduleSlots,
                            onAmHzChange = onTinnitusAmHzChange,
                            onEdgeBoostChange = onTinnitusEdgeBoostChange,
                            onStdpDelayChange = onTinnitusStdpDelayChange,
                            onAntiHabituationChange = onTinnitusAntiHabituationChange,
                            onAutoTimerChange = onTinnitusAutoTimerChange,
                            onRemindersEnabledChange = onTinnitusRemindersEnabledChange,
                            onScheduleSlotsChange = onTinnitusScheduleSlotsChange,
                            onStartScheduledSession = onStartScheduledTinnitusSession,
                        )
                    }
                },
            )
        }
    }
}

@Composable
internal fun MixSheet(
    title: String?,
    levels: Map<String, Float>,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onToggle: (String) -> Unit,
    onLevel: (String, Float) -> Unit,
    onLevelSettled: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    SoundSheet(
        title = title ?: stringResource(R.string.mix_now_playing),
        subtitle = pluralStringResource(R.plurals.mix_sheet_count, levels.size, levels.size),
        onDismiss = onDismiss,
        footer = {
            SaveMixSheetButton(
                enabled = saveEnabled,
                onClick = onSave,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            )
        },
    ) {
        SoundCategory.entries.forEach { category ->
            val ids = orderedSoundIds().filter { it in category.soundIds() && it in levels }
            if (ids.isEmpty()) return@forEach
            MixSectionHeader(category = category, count = ids.size)
            ids.forEach { id ->
                SoundRow(
                    id = id,
                    level = levels[id],
                    onToggle = onToggle,
                    onLevel = onLevel,
                    onLevelSettled = onLevelSettled,
                    showBadge = false,
                )
            }
        }
    }
}

@Composable
private fun MixSectionHeader(category: SoundCategory, count: Int) {
    val tokens = LocalMetiqColors.current
    val locale = LocalConfiguration.current.locales[0]
    Row(
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(4.dp)
                .background(category.accent(), CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(category.labelRes).uppercase(locale),
            color = tokens.textPrimary,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
            ),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            color = tokens.textSecondary,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
        )
    }
}

@Composable
private fun SaveMixSheetButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalMetiqColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .alpha(if (enabled) 1f else tokens.disabledAlpha)
            .clip(RoundedCornerShape(100.dp))
            .background(if (enabled) tokens.textPrimary else tokens.subtleFill)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.mix_save_chip),
            color = if (enabled) tokens.foreground else tokens.textPrimary,
            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Outlined.BookmarkAdd,
            contentDescription = null,
            tint = if (enabled) tokens.foreground else tokens.textPrimary,
            modifier = Modifier.size(20.dp),
        )
    }
}

private fun orderedSoundIds(): List<String> =
    NOISE_COLORS.map { it.id } +
        AMBIENT_SOUNDS.map { it.id } +
        BINAURAL_BANDS.map { it.id } +
        TINNITUS_MODES.map { it.id }

@Composable
private fun SoundRow(
    id: String,
    level: Float?,
    onToggle: (String) -> Unit,
    onLevel: (String, Float) -> Unit,
    onLevelSettled: (String) -> Unit,
    dimmed: Boolean = false,
    showBadge: Boolean = true,
) {
    val tokens = LocalMetiqColors.current

    NOISE_COLORS.firstOrNull { it.id == id }?.let { noise ->
        val fill = noiseColorFor(noise.id)
        SoundLevelRow(
            label = stringResource(noise.labelRes),
            orbColor = fill,
            trackColor = lerp(fill, tokens.foreground, 0.45f),
            level = level,
            onToggle = { onToggle(id) },
            onLevel = { onLevel(id, it) },
            onLevelSettled = { onLevelSettled(id) },
            dimmed = dimmed,
            showBadge = showBadge,
            orbBorder = if (noise.id == "white") tokens.subtleFill else null,
        )
        return
    }
    AMBIENT_SOUNDS.firstOrNull { it.id == id }?.let { sound ->
        val accent = ambientAccentFor(sound.id)
        SoundLevelRow(
            label = stringResource(sound.labelRes),
            orbColor = lerp(accent, tokens.accentHighlight, 0.40f),
            trackColor = accent,
            level = level,
            onToggle = { onToggle(id) },
            onLevel = { onLevel(id, it) },
            onLevelSettled = { onLevelSettled(id) },
            dimmed = dimmed,
            showBadge = showBadge,
            icon = { tint ->
                val iconModifier = Modifier.size(26.dp)
                when {
                    sound.iconVector != null ->
                        Icon(sound.iconVector, contentDescription = null, tint = tint, modifier = iconModifier)

                    sound.iconResId != null ->
                        Icon(painterResource(sound.iconResId), contentDescription = null, tint = tint, modifier = iconModifier)
                }
            },
        )
        return
    }
    BINAURAL_BANDS.firstOrNull { it.id == id }?.let { band ->
        val accent = binauralAccentFor(band.id)
        SoundLevelRow(
            label = stringResource(band.labelRes),
            detail = stringResource(band.feelRes),
            orbColor = lerp(accent, tokens.accentHighlight, 0.40f),
            trackColor = accent,
            level = level,
            onToggle = { onToggle(id) },
            onLevel = { onLevel(id, it) },
            onLevelSettled = { onLevelSettled(id) },
            dimmed = dimmed,
            showBadge = showBadge,
            icon = { tint ->
                Text(
                    text = band.glyph,
                    color = tint,
                    style = TextStyle(fontFamily = Inter, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )
        return
    }
    TINNITUS_MODES.firstOrNull { it.id == id }?.let { mode ->
        val accent = tinnitusAccentFor(mode.id)
        SoundLevelRow(
            label = stringResource(mode.labelRes),
            detail = stringResource(mode.feelRes),
            orbColor = lerp(accent, tokens.accentHighlight, 0.40f),
            trackColor = accent,
            level = level,
            onToggle = { onToggle(id) },
            onLevel = { onLevel(id, it) },
            onLevelSettled = { onLevelSettled(id) },
            dimmed = dimmed,
            showBadge = showBadge,
            icon = { tint ->
                Text(
                    text = mode.glyph,
                    color = tint,
                    style = TextStyle(fontFamily = Inter, fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )
    }
}

@Composable
private fun countSubtitle(ids: Set<String>, levels: Map<String, Float>): String =
    stringResource(R.string.sheet_count_in_mix, ids.count { it in levels }, ids.size)
