package xyz.metiq.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt
import xyz.metiq.R
import xyz.metiq.TinnitusLogEntry
import xyz.metiq.TinnitusScheduleSlot
import xyz.metiq.audio.TinnitusScheduler
import xyz.metiq.audio.TinnitusWaveform
import xyz.metiq.ui.theme.Inter
import xyz.metiq.ui.theme.LocalMetiqColors

internal const val MIN_TINNITUS_HZ = 100f
internal const val MAX_TINNITUS_HZ = 16000f
private const val FREQ_RATIO = MAX_TINNITUS_HZ / MIN_TINNITUS_HZ // 160.0

internal fun hzToFraction(hz: Float): Float {
    val clamped = hz.coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
    return (ln((clamped / MIN_TINNITUS_HZ).toDouble()) / ln(FREQ_RATIO.toDouble()))
        .toFloat()
        .coerceIn(0f, 1f)
}

internal fun fractionToHz(fraction: Float): Float {
    val p = fraction.coerceIn(0f, 1f).toDouble()
    return (MIN_TINNITUS_HZ * FREQ_RATIO.toDouble().pow(p))
        .toFloat()
        .coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
}

@Composable
internal fun TinnitusTabSelector(
    selectedTab: Int,
    calibrated: Boolean,
    onSelectTab: (Int) -> Unit,
) {
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.cellBackground)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tabs = listOf(
            stringResource(R.string.tinnitus_tab_profile),
            stringResource(R.string.tinnitus_tab_therapies),
        )
        tabs.forEachIndexed { index, title ->
            val selected = selectedTab == index
            val bg by animateColorAsState(
                targetValue = if (selected) accent else Color.Transparent,
                animationSpec = tween(200),
                label = "tinnitusTabBg",
            )
            val fg by animateColorAsState(
                targetValue = if (selected) tokens.accentShade else tokens.textPrimary,
                animationSpec = tween(200),
                label = "tinnitusTabFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable { onSelectTab(index) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (index == 0 && calibrated) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = title,
                        color = fg,
                        maxLines = 1,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Tab 1: Guided 5-Step Clinical Calibration Wizard & Locked Profile View.
 *
 * Per clinical protocol and user design requirements:
 * - Users determine their tinnitus profile through a structured 5-step wizard:
 *   1. Ear Location & Sound Character (Pure Tone vs. Narrowband Hiss)
 *   2. Convergent A/B Pitch Bracketing (repeatedly pits the winning sound against narrower
 *      challenger frequencies until a coherent, consistent frequency result is achieved)
 *   3. Pulsed Silent-Gap Micro-Lock (0.6s ON / 0.4s OFF to prevent auditory adaptation)
 *   4. Octave Confusion Challenge (÷2 vs. 1× vs. ×2 harmonic verification)
 *   5. Hearing Threshold (T_hear) & Minimum Masking Level (MML) calibration
 * - Once calibrated, the profile is LOCKED. There is no exposed manual FM slider that could
 *   accidentally overwrite the calibrated frequency. To change the tinnitus sound later, the
 *   user clicks "Re-run 5-Step Calibration".
 */
@Composable
internal fun TinnitusProfileTabContent(
    hz: Float,
    waveform: String,
    bandwidth: Float,
    pan: Float,
    threshold: Float,
    mml: Float,
    calibrated: Boolean,
    history: List<TinnitusLogEntry>,
    auditioning: Boolean,
    onAuditionPlay: (overrideHz: Float, pulsed: Boolean, volume: Float, wf: String, bw: Float, p: Float) -> Unit,
    onAuditionStop: () -> Unit,
    onSaveCalibration: (hz: Float, waveform: String, bandwidth: Float, pan: Float, threshold: Float, mml: Float) -> Unit,
    onOpenTherapiesTab: () -> Unit,
) {
    var wizardActive by remember(calibrated) { mutableStateOf(!calibrated) }

    if (!wizardActive && calibrated) {
        LockedTinnitusProfileView(
            hz = hz,
            waveform = waveform,
            bandwidth = bandwidth,
            pan = pan,
            threshold = threshold,
            mml = mml,
            history = history,
            auditioning = auditioning,
            onTogglePreview = {
                if (auditioning) {
                    onAuditionStop()
                } else {
                    onAuditionPlay(hz, false, mml, waveform, bandwidth, pan)
                }
            },
            onRerunCalibration = {
                onAuditionStop()
                wizardActive = true
            },
            onOpenTherapiesTab = {
                onAuditionStop()
                onOpenTherapiesTab()
            },
        )
    } else {
        CalibrationWizardCard(
            initialHz = hz,
            initialWaveform = waveform,
            initialBandwidth = bandwidth,
            initialPan = pan,
            initialThreshold = threshold,
            initialMml = mml,
            canCancel = calibrated,
            auditioning = auditioning,
            onAuditionPlay = onAuditionPlay,
            onAuditionStop = onAuditionStop,
            onCancel = {
                onAuditionStop()
                wizardActive = false
            },
            onComplete = { finalHz, finalWf, finalBw, finalPan, finalThr, finalMml ->
                onAuditionStop()
                onSaveCalibration(finalHz, finalWf, finalBw, finalPan, finalThr, finalMml)
                wizardActive = false
                onOpenTherapiesTab()
            },
        )
    }
}

@Composable
private fun LockedTinnitusProfileView(
    hz: Float,
    waveform: String,
    bandwidth: Float,
    pan: Float,
    threshold: Float,
    mml: Float,
    history: List<TinnitusLogEntry>,
    auditioning: Boolean,
    onTogglePreview: () -> Unit,
    onRerunCalibration: () -> Unit,
    onOpenTherapiesTab: () -> Unit,
) {
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus
    val roundedHz = hz.roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.cellBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "CALIBRATED & LOCKED PROFILE",
                        color = accent,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                    )
                }
                AuditionButton(
                    auditioning = auditioning,
                    accent = accent,
                    labelPlay = "Preview tone",
                    labelStop = "Stop preview",
                    onClick = onTogglePreview,
                )
            }

            // Locked Frequency Display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(tokens.foreground)
                    .padding(vertical = 12.dp, horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tinnitus_hz_format, roundedHz),
                        color = tokens.textPrimary,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    )
                    if (roundedHz >= 1000) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.US, "(%.2f kHz)", roundedHz / 1000f),
                            color = tokens.textSecondary,
                            style = TextStyle(
                                fontFamily = Inter,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Locked against accidental slider changes",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
                )
            }

            // Summary metrics grid
            val isTone = TinnitusWaveform.fromKey(waveform) == TinnitusWaveform.TONE
            val characterText = if (isTone) {
                "Pure tone (whistle/ring)"
            } else {
                "Narrowband hiss (${(bandwidth * 100).roundToInt()}% sharpness)"
            }
            val earText = when {
                abs(pan) < 0.04f -> "Both ears (Center)"
                pan < -0.85f -> "Left ear only"
                pan > 0.85f -> "Right ear only"
                pan < 0f -> "Left ear (${(-pan * 100).roundToInt()}%)"
                else -> "Right ear (${(pan * 100).roundToInt()}%)"
            }

            ProfileParameterRow("Sound character", characterText)
            ProfileParameterRow("Ear location", earText)
            ProfileParameterRow(
                "Hearing threshold (T_hear)",
                "${(threshold * 100).roundToInt()}% (auto-calibrates Stochastic Resonance)",
            )
            ProfileParameterRow(
                "Minimum masking level (MML)",
                "${(mml * 100).roundToInt()}% (auto-calibrates active therapies)",
            )

            HorizontalDivider(color = tokens.divider)

            Text(
                text = "Why is there no manual frequency slider here? Clinical tinnitus therapies (such as ISEC Mexican-hat Notch and Coordinated Reset) require an exact, uncorrupted pitch match. A manual dial can easily be bumped, shifting the notch away from your true tinnitus neurons. If your tinnitus changes, re-run the 5-step guided calibration below.",
                color = tokens.textSecondary,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.foreground)
                        .border(1.dp, tokens.subtleFill, RoundedCornerShape(12.dp))
                        .clickable(onClick = onRerunCalibration),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Re-calibrate (5 Steps)",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable(onClick = onOpenTherapiesTab),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Open Therapies →",
                        color = tokens.accentShade,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }

        // Offline Pitch & MML Progress Log Card
        if (history.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(tokens.cellBackground)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "CLINICAL PROGRESS LOG (PITCH & MML)",
                        color = tokens.textSecondary,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
                val baselineMml = history.first().mmlPercent
                val latestMml = history.last().mmlPercent
                val mmlDelta = latestMml - baselineMml
                val deltaText = when {
                    history.size < 2 -> "Baseline recorded. Re-calibrate weekly to track MML reduction."
                    mmlDelta < 0 -> "MML improved by ${-mmlDelta}% since first calibration (less volume needed to mask)."
                    mmlDelta == 0 -> "MML stable at $latestMml% compared to baseline."
                    else -> "MML +$mmlDelta% vs. baseline."
                }
                Text(
                    text = deltaText,
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                val df = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
                history.asReversed().take(5).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(tokens.foreground)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = df.format(Date(item.timestampMillis)),
                            color = tokens.textSecondary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                        )
                        Text(
                            text = "${item.hz} Hz • Thr ${item.thresholdPercent}% • MML ${item.mmlPercent}%",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileParameterRow(label: String, value: String) {
    val tokens = LocalMetiqColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokens.textSecondary,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
        )
        Text(
            text = value,
            color = tokens.textPrimary,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
    }
}

/**
 * 5-Step Guided Psychoacoustic Calibration Wizard with Convergent A/B Pitch Bracketing.
 */
@Composable
private fun CalibrationWizardCard(
    initialHz: Float,
    initialWaveform: String,
    initialBandwidth: Float,
    initialPan: Float,
    initialThreshold: Float,
    initialMml: Float,
    canCancel: Boolean,
    auditioning: Boolean,
    onAuditionPlay: (overrideHz: Float, pulsed: Boolean, volume: Float, wf: String, bw: Float, p: Float) -> Unit,
    onAuditionStop: () -> Unit,
    onCancel: () -> Unit,
    onComplete: (hz: Float, waveform: String, bandwidth: Float, pan: Float, threshold: Float, mml: Float) -> Unit,
) {
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus

    var step by remember { mutableIntStateOf(1) }
    var wf by remember { mutableStateOf(initialWaveform) }
    var bw by remember { mutableFloatStateOf(initialBandwidth) }
    var pan by remember { mutableFloatStateOf(initialPan) }
    var thr by remember { mutableFloatStateOf(initialThreshold.coerceIn(0.02f, 0.9f)) }
    var mml by remember { mutableFloatStateOf(initialMml.coerceIn(thr, 1f)) }

    // Step 2: Convergent A/B Pitch Bracketing state
    // We maintain a lower bound, upper bound, the current Winning Hz (Champion), and a Challenger Hz.
    // Every time the user selects a winner between Sound A and Sound B, the winning sound stays and
    // is pitted against a narrower challenger until the interval converges to <= 3% coherence!
    var bracketLowHz by remember { mutableFloatStateOf(400f) }
    var bracketHighHz by remember { mutableFloatStateOf(12000f) }
    var winnerHz by remember { mutableFloatStateOf(initialHz.coerceIn(1000f, 8000f)) }
    var challengerHz by remember { mutableFloatStateOf(if (initialHz < 4000f) 6000f else 2500f) }
    var abRound by remember { mutableIntStateOf(1) }
    var activeAbSlot by remember { mutableIntStateOf(0) } // 0 = none, 1 = Winner (A), 2 = Challenger (B)
    var consistentWins by remember { mutableIntStateOf(0) }

    // Compute how coherent/converged the bracket is (100% when relative span <= 3.5% or >= 7 consistent rounds)
    val relativeSpan = ((bracketHighHz - bracketLowHz) / winnerHz.coerceAtLeast(100f)).coerceAtLeast(0f)
    val spanCoherence = ((1f - (relativeSpan / 1.5f)).coerceIn(0f, 1f) * 100f).roundToInt()
    val roundCoherence = ((abRound - 1) * 16).coerceIn(0, 100)
    val coherencePercent = maxOf(spanCoherence, roundCoherence).coerceIn(0, 100)
    val isConverged = relativeSpan <= 0.045f || abRound >= 7 || consistentWins >= 3

    var lockedHz by remember { mutableFloatStateOf(initialHz) }

    // Automatically stop audition when changing steps
    LaunchedEffect(step) {
        activeAbSlot = 0
        if (step == 3) {
            // Step 3 uses Pulsed Silent-Gap tone (0.6s ON / 0.4s OFF) automatically
            onAuditionPlay(lockedHz, true, mml.coerceAtLeast(0.25f), wf, bw, pan)
        } else {
            onAuditionStop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.cellBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Step progress header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "STEP $step OF 5 • GUIDED CALIBRATION",
                color = accent,
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                ),
            )
            if (canCancel) {
                Text(
                    text = "Cancel",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // 5-segment progress bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            for (s in 1..5) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (s <= step) accent else tokens.subtleFill),
                )
            }
        }

        when (step) {
            1 -> {
                Text(
                    text = "1. Ear Location & Sound Texture",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "First, tell us where you hear your tinnitus and whether it sounds more like a musical whistle/ringing (Pure Tone) or like steam/cicadas/static (Narrowband Hiss).",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )

                // Ear Quick Selector
                Text(
                    text = "Where is your tinnitus loudest?",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WaveformChip(
                        label = "Left Ear",
                        selected = pan < -0.35f,
                        accent = accent,
                        onClick = {
                            pan = -0.8f
                            if (auditioning) onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    WaveformChip(
                        label = "Both / Center",
                        selected = abs(pan) <= 0.35f,
                        accent = accent,
                        onClick = {
                            pan = 0f
                            if (auditioning) onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    WaveformChip(
                        label = "Right Ear",
                        selected = pan > 0.35f,
                        accent = accent,
                        onClick = {
                            pan = 0.8f
                            if (auditioning) onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Fine Ear Balance Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Fine ear balance",
                        color = tokens.textSecondary,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                    )
                    val panText = when {
                        abs(pan) < 0.04f -> "Center"
                        pan < 0f -> "L ${(-pan * 100).roundToInt()}%"
                        else -> "R ${(pan * 100).roundToInt()}%"
                    }
                    Text(
                        text = panText,
                        color = tokens.textPrimary,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                    )
                }
                CompactParamSlider(
                    fraction = ((pan + 1f) / 2f).coerceIn(0f, 1f),
                    accent = accent,
                    bipolarCenter = true,
                    onFraction = { f ->
                        val p = (f * 2f - 1f).coerceIn(-1f, 1f)
                        pan = if (abs(p) < 0.03f) 0f else p
                        if (auditioning) onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                    },
                    onSettled = {},
                )

                // Sound Character Selector
                Text(
                    text = "What texture sounds closest to your tinnitus?",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                val isTone = TinnitusWaveform.fromKey(wf) == TinnitusWaveform.TONE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WaveformChip(
                        label = "Pure Tone (Ring)",
                        selected = isTone,
                        accent = accent,
                        onClick = {
                            wf = TinnitusWaveform.TONE.key
                            onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                        },
                        modifier = Modifier.weight(1f),
                    )
                    WaveformChip(
                        label = "Narrowband Hiss",
                        selected = !isTone,
                        accent = accent,
                        onClick = {
                            wf = TinnitusWaveform.NARROWBAND.key
                            onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (!isTone) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Hiss sharpness (wide static vs. narrow whistle)",
                            color = tokens.textSecondary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                        )
                        Text(
                            text = "${(bw * 100).roundToInt()}%",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                        )
                    }
                    CompactParamSlider(
                        fraction = bw,
                        accent = accent,
                        onFraction = { v ->
                            bw = v
                            if (auditioning) onAuditionPlay(winnerHz, false, 0.3f, wf, bw, pan)
                        },
                        onSettled = {},
                    )
                }

                WizardNavRow(
                    backLabel = null,
                    nextLabel = "Next: A/B Pitch Bracketing →",
                    onBack = {},
                    onNext = {
                        // Initialize convergent A/B tournament around a clean starting bracket
                        bracketLowHz = 500f
                        bracketHighHz = 12000f
                        winnerHz = 2200f
                        challengerHz = 6500f
                        abRound = 1
                        consistentWins = 0
                        step = 2
                    },
                )
            }

            2 -> {
                Text(
                    text = "2. Convergent A/B Pitch Bracketing",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Instead of sweeping a confusing radio dial, tap Sound A and Sound B below. Pick whichever sound is closer in pitch to your tinnitus. Your winning sound stays and competes against a closer challenger until your choices converge to a coherent, constant frequency.",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )

                // Convergence status bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.foreground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Round $abRound • Bracket: ${bracketLowHz.roundToInt()}–${bracketHighHz.roundToInt()} Hz",
                            color = tokens.textSecondary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                        )
                        Text(
                            text = if (isConverged) "Coherent Match Locked!" else "$coherencePercent% Coherent",
                            color = if (isConverged) accent else tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(tokens.subtleFill),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isConverged) 1f else (coherencePercent / 100f).coerceIn(0.1f, 1f))
                                .height(6.dp)
                                .clip(RoundedCornerShape(100.dp))
                                .background(accent),
                        )
                    }
                }

                // Sound A (Current Winner) vs Sound B (Challenger) Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AbToneCard(
                        title = if (abRound == 1) "Sound A" else "Sound A (Winner ★)",
                        hz = winnerHz.roundToInt(),
                        playing = activeAbSlot == 1 && auditioning,
                        accent = accent,
                        onPlay = {
                            if (activeAbSlot == 1 && auditioning) {
                                activeAbSlot = 0
                                onAuditionStop()
                            } else {
                                activeAbSlot = 1
                                onAuditionPlay(winnerHz, false, 0.32f, wf, bw, pan)
                            }
                        },
                        onChoose = {
                            // User kept the current Winner!
                            // Narrow the bracket toward winnerHz from challengerHz's side
                            if (challengerHz > winnerHz) {
                                bracketHighHz = sqrt(winnerHz * challengerHz).coerceAtMost(bracketHighHz)
                            } else {
                                bracketLowHz = sqrt(winnerHz * challengerHz).coerceAtLeast(bracketLowHz)
                            }
                            consistentWins += 1
                            abRound += 1
                            // Pick the opposite side of the bracket for the next Challenger
                            val distLow = winnerHz - bracketLowHz
                            val distHigh = bracketHighHz - winnerHz
                            challengerHz = if (distHigh >= distLow) {
                                sqrt(winnerHz * bracketHighHz)
                            } else {
                                sqrt(winnerHz * bracketLowHz)
                            }.coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
                            lockedHz = winnerHz
                            activeAbSlot = 1
                            onAuditionPlay(winnerHz, false, 0.32f, wf, bw, pan)
                        },
                        chooseLabel = "A is closer",
                        modifier = Modifier.weight(1f),
                    )

                    AbToneCard(
                        title = "Sound B (Challenger)",
                        hz = challengerHz.roundToInt(),
                        playing = activeAbSlot == 2 && auditioning,
                        accent = accent,
                        onPlay = {
                            if (activeAbSlot == 2 && auditioning) {
                                activeAbSlot = 0
                                onAuditionStop()
                            } else {
                                activeAbSlot = 2
                                onAuditionPlay(challengerHz, false, 0.32f, wf, bw, pan)
                            }
                        },
                        onChoose = {
                            // Challenger won! Challenger becomes the new Winner
                            if (challengerHz > winnerHz) {
                                bracketLowHz = sqrt(winnerHz * challengerHz).coerceAtLeast(bracketLowHz)
                            } else {
                                bracketHighHz = sqrt(winnerHz * challengerHz).coerceAtMost(bracketHighHz)
                            }
                            winnerHz = challengerHz
                            consistentWins = 1
                            abRound += 1
                            val distLow = winnerHz - bracketLowHz
                            val distHigh = bracketHighHz - winnerHz
                            challengerHz = if (distHigh >= distLow) {
                                sqrt(winnerHz * bracketHighHz)
                            } else {
                                sqrt(winnerHz * bracketLowHz)
                            }.coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
                            lockedHz = winnerHz
                            activeAbSlot = 1
                            onAuditionPlay(winnerHz, false, 0.32f, wf, bw, pan)
                        },
                        chooseLabel = "B is closer",
                        modifier = Modifier.weight(1f),
                    )
                }

                // Reset bracket if user wants to restart A/B search
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Restart A/B bracket (100–16k Hz)",
                        color = tokens.textSecondary,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                bracketLowHz = 250f
                                bracketHighHz = 14000f
                                winnerHz = 2000f
                                challengerHz = 7000f
                                abRound = 1
                                consistentWins = 0
                                activeAbSlot = 0
                                onAuditionStop()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                    Text(
                        text = "Winning pitch: ${winnerHz.roundToInt()} Hz",
                        color = accent,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                    )
                }

                WizardNavRow(
                    backLabel = "← Step 1",
                    nextLabel = if (isConverged) {
                        "Next: Pulsed Silent-Gap Lock →"
                    } else {
                        "Use ${winnerHz.roundToInt()} Hz →"
                    },
                    onBack = { step = 1 },
                    onNext = {
                        lockedHz = winnerHz.roundToInt().toFloat()
                        step = 3
                    },
                )
            }

            3 -> {
                Text(
                    text = "3. Pulsed Silent-Gap Verification",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                // Clinical explanation answering why Step 3 is distinct from Step 2
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.foreground)
                        .padding(12.dp),
                ) {
                    Text(
                        text = "Why is this step needed after A/B bracketing? Continuous tones cause 'auditory adaptation'—your brain blends the app's tone with your tinnitus within seconds. By pulsing your winning ${lockedHz.roundToInt()} Hz tone (0.6s ON / 0.4s SILENT GAP), you hear the app during the pulse and your raw internal tinnitus in the silent gap, allowing a true side-by-side lock.",
                        color = tokens.textSecondary,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.foreground)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "${lockedHz.roundToInt()} Hz",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 24.sp, fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Pulsed 0.6s ON / 0.4s OFF",
                            color = accent,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                    AuditionButton(
                        auditioning = auditioning,
                        accent = accent,
                        labelPlay = "Play pulsed",
                        labelStop = "Pulsing…",
                        onClick = {
                            if (auditioning) {
                                onAuditionStop()
                            } else {
                                onAuditionPlay(lockedHz, true, mml.coerceAtLeast(0.28f), wf, bw, pan)
                            }
                        },
                    )
                }

                Text(
                    text = "Optional micro-nudge while listening to the silent gaps:",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val deltas = listOf(-25, -5, -1, 1, 5, 25)
                    deltas.forEach { d ->
                        NudgePill(
                            label = if (d > 0) "+$d" else "$d",
                            onClick = {
                                lockedHz = (lockedHz + d).coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
                                onAuditionPlay(lockedHz, true, mml.coerceAtLeast(0.28f), wf, bw, pan)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                WizardNavRow(
                    backLabel = "← A/B Bracket",
                    nextLabel = "Next: Octave Check →",
                    onBack = { step = 2 },
                    onNext = { step = 4 },
                )
            }

            4 -> {
                Text(
                    text = "4. Octave Confusion Challenge",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "In clinical audiology trials, ~40% of people match a harmonic 1 octave below (÷2) or 1 octave above (×2) their true tinnitus—like two C notes on a piano. Tap each card below to test all three harmonics and confirm your true octave.",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )

                val baseCandidate = remember { lockedHz }
                val lowOct = (baseCandidate / 2f).coerceAtLeast(MIN_TINNITUS_HZ)
                val highOct = (baseCandidate * 2f).coerceAtMost(MAX_TINNITUS_HZ)

                val options = listOf(
                    Triple("Lower Octave (÷2)", lowOct, "Sub-harmonic check"),
                    Triple("Converged Pitch (1×)", baseCandidate, "Your Step 2–3 match"),
                    Triple("Higher Octave (×2)", highOct, "Upper-harmonic check"),
                )

                options.forEach { (label, candidateHz, sub) ->
                    val selected = abs(lockedHz - candidateHz) < 1f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) accent.copy(alpha = 0.16f) else tokens.foreground)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accent else tokens.subtleFill,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable {
                                lockedHz = candidateHz
                                onAuditionPlay(candidateHz, true, mml.coerceAtLeast(0.28f), wf, bw, pan)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = label,
                                color = tokens.textPrimary,
                                style = TextStyle(fontFamily = Inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = sub,
                                color = tokens.textSecondary,
                                style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                            )
                        }
                        Text(
                            text = "${candidateHz.roundToInt()} Hz",
                            color = if (selected) accent else tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }

                WizardNavRow(
                    backLabel = "← Step 3",
                    nextLabel = "Next: Threshold & MML →",
                    onBack = { step = 3 },
                    onNext = { step = 5 },
                )
            }

            5 -> {
                Text(
                    text = "5. Hearing Threshold & Masking Level",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    text = "Different frontier therapies require different clinical doses. Set both markers below so the app can automatically calibrate every therapy for your ears.",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp),
                )

                // 1. Hearing Threshold (T_hear)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.foreground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "1. Hearing Threshold (T_hear)",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "${(thr * 100).roundToInt()}%",
                            color = accent,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(
                        text = "Slide up from zero until you can BARELY detect the ${lockedHz.roundToInt()} Hz tone. Used for Stochastic Resonance.",
                        color = tokens.textSecondary,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
                    )
                    CompactParamSlider(
                        fraction = thr,
                        accent = accent,
                        onFraction = { v ->
                            thr = v.coerceIn(0.01f, 0.90f)
                            if (mml < thr) mml = thr
                            onAuditionPlay(lockedHz, false, thr, wf, bw, pan)
                        },
                        onSettled = {},
                    )
                }

                // 2. Minimum Masking Level (MML)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(tokens.foreground)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "2. Minimum Masking Level (MML)",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        )
                        Text(
                            text = "${(mml * 100).roundToInt()}%",
                            color = accent,
                            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                    Text(
                        text = "Slide up until the sound JUST covers your internal tinnitus. Used for AM Residual Inhibition, Bimodal STDP, ISEC Notch, and CR Neuromodulation.",
                        color = tokens.textSecondary,
                        style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp),
                    )
                    CompactParamSlider(
                        fraction = mml,
                        accent = accent,
                        onFraction = { v ->
                            mml = v.coerceIn(thr, 1f)
                            onAuditionPlay(lockedHz, false, mml, wf, bw, pan)
                        },
                        onSettled = {},
                    )
                }

                WizardNavRow(
                    backLabel = "← Step 4",
                    nextLabel = "Save & Lock Profile ✓",
                    onBack = { step = 4 },
                    onNext = {
                        onComplete(
                            lockedHz.roundToInt().toFloat(),
                            wf,
                            bw,
                            pan,
                            thr,
                            mml,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AbToneCard(
    title: String,
    hz: Int,
    playing: Boolean,
    accent: Color,
    onPlay: () -> Unit,
    onChoose: () -> Unit,
    chooseLabel: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMetiqColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tokens.foreground)
            .border(
                width = if (playing) 1.5.dp else 1.dp,
                color = if (playing) accent else tokens.subtleFill,
                shape = RoundedCornerShape(14.dp),
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = if (playing) accent else tokens.textSecondary,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(
            text = "$hz Hz",
            color = tokens.textPrimary,
            style = TextStyle(fontFamily = Inter, fontSize = 20.sp, fontWeight = FontWeight.Bold),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (playing) accent else tokens.cellBackground)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = if (playing) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = if (playing) tokens.accentShade else tokens.textPrimary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (playing) "Playing…" else "Listen",
                    color = if (playing) tokens.accentShade else tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f))
                .border(1.dp, accent, RoundedCornerShape(10.dp))
                .clickable(onClick = onChoose),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = chooseLabel,
                color = accent,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}

@Composable
private fun WizardNavRow(
    backLabel: String?,
    nextLabel: String,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (backLabel != null) {
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.foreground)
                    .border(1.dp, tokens.subtleFill, RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = backLabel,
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = nextLabel,
                color = tokens.accentShade,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * Tab 2 Header Cards: Proposed Daily Therapy Schedule & Reminders (with Snooze) +
 * Frontier Algorithm Parameter Controls.
 */
@Composable
internal fun TinnitusTherapiesHeader(
    calibratedHz: Float,
    threshold: Float,
    mml: Float,
    amHz: Float,
    edgeBoost: Float,
    stdpDelayMs: Float,
    antiHabituation: Boolean,
    autoTimer: Boolean,
    remindersEnabled: Boolean,
    scheduleSlots: List<TinnitusScheduleSlot>,
    onAmHzChange: (Float) -> Unit,
    onEdgeBoostChange: (Float) -> Unit,
    onStdpDelayChange: (Float) -> Unit,
    onAntiHabituationChange: (Boolean) -> Unit,
    onAutoTimerChange: (Boolean) -> Unit,
    onRemindersEnabledChange: (Boolean) -> Unit,
    onScheduleSlotsChange: (List<TinnitusScheduleSlot>) -> Unit,
    onStartScheduledSession: (modeId: String, durationMinutes: Int) -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus
    var editingSlot by remember { mutableStateOf<TinnitusScheduleSlot?>(null) }
    var snoozeFeedback by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            onRemindersEnabledChange(true)
            TinnitusScheduler.syncAlarms(context, true, scheduleSlots)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. Proposed Clinical Schedule & Reminders Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.cellBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "PROPOSED DAILY SCHEDULE & REMINDERS",
                        color = accent,
                        style = TextStyle(
                            fontFamily = Inter,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.4.sp,
                        ),
                    )
                }
                Switch(
                    checked = remindersEnabled,
                    onCheckedChange = { enable ->
                        if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPerm = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!hasPerm) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@Switch
                            }
                        }
                        onRemindersEnabledChange(enable)
                        TinnitusScheduler.syncAlarms(context, enable, scheduleSlots)
                    },
                )
            }

            Text(
                text = "Based on 2024–2025 clinical trials: 30m Morning Bimodal STDP, 60m Afternoon ISEC Notch, and 30m Bedtime Stochastic Resonance. Tap any slot to customize time, therapy, or duration.",
                color = tokens.textSecondary,
                style = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp),
            )

            // Auto-arm clinical timer toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.foreground)
                    .clickable { onAutoTimerChange(!autoTimer) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-arm clinical session timer",
                        color = tokens.textPrimary,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        text = "Automatically sets timer (3m AM-RI, 30m Bimodal, 60m Notch/CR) when started",
                        color = tokens.textSecondary,
                        style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = if (autoTimer) accent else tokens.textSecondary.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp),
                )
            }

            // 3 Customizable Schedule Slots
            scheduleSlots.forEach { slot ->
                val timeStr = String.format(Locale.US, "%02d:%02d", slot.hour, slot.minute)
                val modeName = TinnitusScheduler.modeLabel(context, slot.modeId)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(tokens.foreground)
                        .border(
                            width = 1.dp,
                            color = if (slot.enabled) accent.copy(alpha = 0.45f) else tokens.subtleFill,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .clickable { editingSlot = slot }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (slot.enabled) accent.copy(alpha = 0.2f) else tokens.cellBackground)
                                .clickable {
                                    val updated = scheduleSlots.map {
                                        if (it.id == slot.id) it.copy(enabled = !it.enabled) else it
                                    }
                                    onScheduleSlotsChange(updated)
                                    TinnitusScheduler.syncAlarms(context, remindersEnabled, updated)
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = timeStr,
                                color = if (slot.enabled) accent else tokens.textSecondary,
                                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                        Column {
                            Text(
                                text = modeName,
                                color = if (slot.enabled) tokens.textPrimary else tokens.textSecondary,
                                style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            )
                            Text(
                                text = "${slot.durationMinutes} min session • Tap to edit",
                                color = tokens.textSecondary,
                                style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .height(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(accent)
                            .clickable { onStartScheduledSession(slot.modeId, slot.durationMinutes) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PlayArrow,
                                contentDescription = null,
                                tint = tokens.accentShade,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "${slot.durationMinutes}m",
                                color = tokens.accentShade,
                                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }

            // Notification Test & Snooze Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.foreground)
                        .border(1.dp, tokens.subtleFill, RoundedCornerShape(10.dp))
                        .clickable {
                            val first = scheduleSlots.firstOrNull { it.enabled } ?: scheduleSlots.first()
                            TinnitusScheduler.postNotification(
                                context = context,
                                slotId = first.id,
                                modeId = first.modeId,
                                durationMinutes = first.durationMinutes,
                            )
                            snoozeFeedback = "Sent test notification (with Snooze 15m / 1h buttons)."
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = tokens.textPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Test Reminder",
                            color = tokens.textPrimary,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.foreground)
                        .border(1.dp, tokens.subtleFill, RoundedCornerShape(10.dp))
                        .clickable {
                            val first = scheduleSlots.firstOrNull { it.enabled } ?: scheduleSlots.first()
                            TinnitusScheduler.scheduleSnooze(
                                context = context,
                                slotId = first.id,
                                modeId = first.modeId,
                                durationMinutes = first.durationMinutes,
                                snoozeMinutes = 15,
                            )
                            snoozeFeedback = "Snoozed reminder: will notify you in 15 minutes."
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Snooze,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = "Snooze 15m",
                            color = accent,
                            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }
            if (snoozeFeedback != null) {
                Text(
                    text = snoozeFeedback!!,
                    color = accent,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                )
            }
        }

        // 2. Frontier Algorithm Parameters Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(tokens.cellBackground)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "FRONTIER DSP PARAMETERS",
                    color = tokens.textSecondary,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp,
                    ),
                )
                Text(
                    text = "Locked @ ${calibratedHz.roundToInt()} Hz (MML ${(mml * 100).roundToInt()}%)",
                    color = accent,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                )
            }

            // AM Residual Inhibition Rate (10 Hz Alpha vs 40 Hz Gamma)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AM Residual Inhibition pulse",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val is40 = amHz >= 25f
                    NudgePill(
                        label = "10 Hz (Alpha)",
                        onClick = { onAmHzChange(10f) },
                        modifier = Modifier.width(96.dp),
                    )
                    NudgePill(
                        label = "40 Hz (Gamma)",
                        onClick = { onAmHzChange(40f) },
                        modifier = Modifier.width(102.dp),
                    )
                }
            }
            Text(
                text = if (amHz >= 25f) {
                    "Active: 40 Hz Gamma envelope modulation (auditory thalamocortical synchrony)"
                } else {
                    "Active: 10 Hz Alpha envelope modulation (deepest post-stimulus suppression)"
                },
                color = accent,
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
            )

            // ISEC Mexican-Hat Flanking Boost Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "ISEC Notch flanking boost (+6 dB edges)",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
                Text(
                    text = "${(edgeBoost * 100).roundToInt()}%",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            CompactParamSlider(
                fraction = edgeBoost,
                accent = accent,
                onFraction = onEdgeBoostChange,
                onSettled = {},
            )

            // Bimodal STDP Haptic-Audio Delay (5–20 ms)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Bimodal STDP audio-haptic interval",
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp),
                )
                Text(
                    text = "${stdpDelayMs.roundToInt()} ms",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            CompactParamSlider(
                fraction = ((stdpDelayMs - 5f) / 15f).coerceIn(0f, 1f),
                accent = accent,
                onFraction = { f -> onStdpDelayChange(5f + f * 15f) },
                onSettled = {},
            )

            // TENT-A2 Anti-Habituation Micro-Drift Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(tokens.foreground)
                    .clickable { onAntiHabituationChange(!antiHabituation) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "TENT-A2 Anti-Habituation micro-drift (±3%)",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    text = if (antiHabituation) "ON" else "OFF",
                    color = if (antiHabituation) accent else tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
            }
        }
    }

    editingSlot?.let { slot ->
        EditScheduleSlotDialog(
            slot = slot,
            onDismiss = { editingSlot = null },
            onSave = { updatedSlot ->
                editingSlot = null
                val next = scheduleSlots.map { if (it.id == updatedSlot.id) updatedSlot else it }
                onScheduleSlotsChange(next)
                TinnitusScheduler.syncAlarms(context, remindersEnabled, next)
            },
        )
    }
}

@Composable
private fun EditScheduleSlotDialog(
    slot: TinnitusScheduleSlot,
    onDismiss: () -> Unit,
    onSave: (TinnitusScheduleSlot) -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus

    var enabled by remember { mutableStateOf(slot.enabled) }
    var hourText by remember { mutableStateOf(slot.hour.toString()) }
    var minuteText by remember { mutableStateOf(String.format(Locale.US, "%02d", slot.minute)) }
    var modeId by remember { mutableStateOf(slot.modeId) }
    var durationMin by remember { mutableIntStateOf(slot.durationMinutes) }

    val availableModes = listOf(
        "tinnitus_am_ri",
        "tinnitus_bimodal",
        "tinnitus_notch",
        "tinnitus_modulate",
        "tinnitus_sr",
        "tinnitus_invert",
        "tinnitus_match",
    )
    val availableDurations = listOf(3, 15, 30, 45, 60, 90)

    val parsedHour = hourText.toIntOrNull()
    val parsedMinute = minuteText.toIntOrNull()
    val valid = parsedHour != null && parsedHour in 0..23 && parsedMinute != null && parsedMinute in 0..59

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize Schedule Slot #${slot.id}", fontFamily = Inter) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = hourText,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) hourText = it },
                        label = { Text("Hour (0-23)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minuteText,
                        onValueChange = { if (it.length <= 2 && it.all(Char::isDigit)) minuteText = it },
                        label = { Text("Min (0-59)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                Text(
                    text = "Select Therapy Mode:",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
                availableModes.forEach { mId ->
                    val selected = modeId == mId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) accent.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { modeId = mId }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = TinnitusScheduler.modeLabel(context, mId),
                            style = TextStyle(
                                fontFamily = Inter,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            ),
                        )
                        if (selected) {
                            Text("✓", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Text(
                    text = "Session Duration (minutes):",
                    style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableDurations.forEach { d ->
                        val sel = durationMin == d
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) accent else tokens.cellBackground)
                                .clickable { durationMin = d },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "${d}m",
                                color = if (sel) tokens.accentShade else tokens.textPrimary,
                                style = TextStyle(fontFamily = Inter, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    if (parsedHour != null && parsedMinute != null) {
                        onSave(
                            slot.copy(
                                enabled = enabled,
                                hour = parsedHour,
                                minute = parsedMinute,
                                modeId = modeId,
                                durationMinutes = durationMin,
                            )
                        )
                    }
                },
            ) {
                Text("Save Slot", fontFamily = Inter)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), fontFamily = Inter)
            }
        },
    )
}

@Composable
private fun AuditionButton(
    auditioning: Boolean,
    accent: Color,
    labelPlay: String,
    labelStop: String,
    onClick: () -> Unit,
) {
    val tokens = LocalMetiqColors.current
    val bg by animateColorAsState(
        targetValue = if (auditioning) accent else tokens.foreground,
        animationSpec = tween(200),
        label = "auditionBg",
    )
    val fg by animateColorAsState(
        targetValue = if (auditioning) tokens.accentShade else tokens.textPrimary,
        animationSpec = tween(200),
        label = "auditionFg",
    )
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .border(1.dp, if (auditioning) accent else tokens.subtleFill, RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (auditioning) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = if (auditioning) labelStop else labelPlay,
            color = fg,
            style = TextStyle(fontFamily = Inter, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun NudgePill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMetiqColors.current
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tokens.foreground)
            .border(1.dp, tokens.subtleFill, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tokens.textPrimary,
            maxLines = 1,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            ),
        )
    }
}

@Composable
private fun WaveformChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMetiqColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) accent else tokens.foreground,
        animationSpec = tween(200),
        label = "waveformBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) tokens.accentShade else tokens.textPrimary,
        animationSpec = tween(200),
        label = "waveformFg",
    )
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = fg,
            maxLines = 1,
            style = TextStyle(
                fontFamily = Inter,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
        )
    }
}

@Composable
private fun CompactParamSlider(
    fraction: Float,
    accent: Color,
    bipolarCenter: Boolean = false,
    onFraction: (Float) -> Unit,
    onSettled: () -> Unit,
) {
    val tokens = LocalMetiqColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(22.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val r = 9.dp.toPx()
                    val f = ((offset.x - r) / (size.width - 2f * r)).coerceIn(0f, 1f)
                    onFraction(f)
                    onSettled()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(onDragEnd = onSettled) { change, _ ->
                    change.consume()
                    val r = 9.dp.toPx()
                    val f = ((change.position.x - r) / (size.width - 2f * r)).coerceIn(0f, 1f)
                    onFraction(f)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val trackH = 6.dp.toPx()
            val cy = size.height / 2f
            val corner = CornerRadius(trackH / 2f, trackH / 2f)
            val r = 8.dp.toPx()
            val cx = r + fraction.coerceIn(0f, 1f) * (size.width - 2f * r)

            drawRoundRect(
                color = tokens.subtleFill,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = Size(size.width, trackH),
                cornerRadius = corner,
            )

            if (bipolarCenter) {
                val midX = size.width / 2f
                val startX = minOf(midX, cx)
                val endX = maxOf(midX, cx)
                if (endX - startX > 1f) {
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(startX, cy - trackH / 2f),
                        size = Size(endX - startX, trackH),
                        cornerRadius = corner,
                    )
                }
            } else {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(cx, trackH),
                    cornerRadius = corner,
                )
            }

            drawCircle(
                color = accent,
                radius = r,
                center = Offset(cx, cy),
            )
        }
    }
}
