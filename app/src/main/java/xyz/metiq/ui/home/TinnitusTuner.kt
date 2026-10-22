package xyz.metiq.ui.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import xyz.metiq.R
import xyz.metiq.audio.TinnitusWaveform
import xyz.metiq.ui.theme.Inter
import xyz.metiq.ui.theme.LocalMetiqColors

internal const val MIN_TINNITUS_HZ = 100f
internal const val MAX_TINNITUS_HZ = 16000f
private const val FREQ_RATIO = MAX_TINNITUS_HZ / MIN_TINNITUS_HZ // 160.0

private val MAJOR_TICKS_HZ = listOf(100f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
private val MAJOR_TICK_LABELS = listOf("100", "250", "500", "1k", "2k", "4k", "8k", "16k")

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
internal fun TinnitusTunerCard(
    hz: Float,
    waveform: String,
    bandwidth: Float,
    pan: Float,
    auditioning: Boolean,
    hasActiveReliefMode: Boolean,
    onHzPreview: (Float) -> Unit,
    onHzCommit: (Float) -> Unit,
    onWaveformChange: (String) -> Unit,
    onBandwidthPreview: (Float) -> Unit,
    onBandwidthCommit: (Float) -> Unit,
    onPanPreview: (Float) -> Unit,
    onPanCommit: (Float) -> Unit,
    onToggleAudition: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMetiqColors.current
    val accent = tokens.categoryTinnitus
    var showEditHzDialog by remember { mutableStateOf(false) }
    var localHz by remember(hz) { mutableFloatStateOf(hz) }
    var localBandwidth by remember(bandwidth) { mutableFloatStateOf(bandwidth) }
    var localPan by remember(pan) { mutableFloatStateOf(pan) }

    val applyInstantHz: (Float) -> Unit = { target ->
        val clamped = target.roundToInt().toFloat().coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
        localHz = clamped
        onHzPreview(clamped)
        onHzCommit(clamped)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(tokens.cellBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header row + Audition ("Test tone") button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.tinnitus_tuner_title),
                color = tokens.textSecondary,
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.4.sp,
                ),
            )
            if (!hasActiveReliefMode) {
                AuditionButton(
                    auditioning = auditioning,
                    accent = accent,
                    onClick = onToggleAudition,
                )
            }
        }

        // Digital Frequency Readout (tappable to type exact Hz)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.foreground)
                .clickable { showEditHzDialog = true }
                .padding(vertical = 10.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val roundedHz = localHz.roundToInt()
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.tinnitus_hz_format, roundedHz),
                    color = tokens.textPrimary,
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = 28.sp,
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.tinnitus_tap_to_edit_hz),
                color = tokens.textSecondary,
                style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
            )
        }

        // FM-Radio Logarithmic Scale Scrubber
        FmDialScrubber(
            hz = localHz,
            accent = accent,
            onHzDrag = { newHz ->
                val rounded = newHz.roundToInt().toFloat().coerceIn(MIN_TINNITUS_HZ, MAX_TINNITUS_HZ)
                localHz = rounded
                onHzPreview(rounded)
            },
            onHzSettled = {
                onHzCommit(localHz)
            },
        )

        // Fine-Tune (±1 Hz, ±10 Hz) & Anti-Octave-Confusion (÷2 Oct, ×2 Oct) Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NudgePill(
                label = stringResource(R.string.tinnitus_octave_half),
                onClick = { applyInstantHz(localHz / 2f) },
                modifier = Modifier.weight(1.25f),
            )
            NudgePill(
                label = stringResource(R.string.tinnitus_nudge_minus_10),
                onClick = { applyInstantHz(localHz - 10f) },
                modifier = Modifier.weight(1f),
            )
            NudgePill(
                label = stringResource(R.string.tinnitus_nudge_minus_1),
                onClick = { applyInstantHz(localHz - 1f) },
                modifier = Modifier.weight(0.9f),
            )
            NudgePill(
                label = stringResource(R.string.tinnitus_nudge_plus_1),
                onClick = { applyInstantHz(localHz + 1f) },
                modifier = Modifier.weight(0.9f),
            )
            NudgePill(
                label = stringResource(R.string.tinnitus_nudge_plus_10),
                onClick = { applyInstantHz(localHz + 10f) },
                modifier = Modifier.weight(1f),
            )
            NudgePill(
                label = stringResource(R.string.tinnitus_octave_double),
                onClick = { applyInstantHz(localHz * 2f) },
                modifier = Modifier.weight(1.25f),
            )
        }

        // Sound Character Selector: Pure Tone vs Narrowband Hiss
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(R.string.tinnitus_waveform_label),
                color = tokens.textSecondary,
                style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isTone = TinnitusWaveform.fromKey(waveform) == TinnitusWaveform.TONE
                WaveformChip(
                    label = stringResource(R.string.tinnitus_waveform_tone),
                    selected = isTone,
                    accent = accent,
                    onClick = { onWaveformChange(TinnitusWaveform.TONE.key) },
                    modifier = Modifier.weight(1f),
                )
                WaveformChip(
                    label = stringResource(R.string.tinnitus_waveform_narrowband),
                    selected = !isTone,
                    accent = accent,
                    onClick = { onWaveformChange(TinnitusWaveform.NARROWBAND.key) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Band Sharpness (Q) Slider — used by Narrowband Hiss and Notch Therapy
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tinnitus_bandwidth_label),
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
                )
                Text(
                    text = "${(localBandwidth * 100).roundToInt()}%",
                    color = tokens.textPrimary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
                )
            }
            CompactParamSlider(
                fraction = localBandwidth,
                accent = accent,
                onFraction = { v ->
                    localBandwidth = v
                    onBandwidthPreview(v)
                },
                onSettled = { onBandwidthCommit(localBandwidth) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tinnitus_bandwidth_wide),
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
                )
                Text(
                    text = stringResource(R.string.tinnitus_bandwidth_sharp),
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 11.sp),
                )
            }
        }

        // Left / Right Ear Balance Slider
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tinnitus_balance_label),
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val panText = when {
                        abs(localPan) < 0.04f -> stringResource(R.string.tinnitus_balance_center)
                        localPan < 0f -> stringResource(R.string.tinnitus_balance_left, (-localPan * 100).roundToInt())
                        else -> stringResource(R.string.tinnitus_balance_right, (localPan * 100).roundToInt())
                    }
                    Text(
                        text = panText,
                        color = tokens.textPrimary,
                        style = TextStyle(fontFamily = Inter, fontSize = 13.sp),
                    )
                    if (abs(localPan) >= 0.04f) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.tinnitus_balance_reset),
                            color = accent,
                            style = TextStyle(
                                fontFamily = Inter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    localPan = 0f
                                    onPanPreview(0f)
                                    onPanCommit(0f)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            CompactParamSlider(
                fraction = ((localPan + 1f) / 2f).coerceIn(0f, 1f),
                accent = accent,
                bipolarCenter = true,
                onFraction = { f ->
                    val p = (f * 2f - 1f).coerceIn(-1f, 1f)
                    localPan = if (abs(p) < 0.03f) 0f else p
                    onPanPreview(localPan)
                },
                onSettled = { onPanCommit(localPan) },
            )
        }
    }

    if (showEditHzDialog) {
        EditFrequencyDialog(
            initialHz = localHz.roundToInt(),
            onDismiss = { showEditHzDialog = false },
            onConfirm = { newHz ->
                showEditHzDialog = false
                applyInstantHz(newHz.toFloat())
            },
        )
    }
}

@Composable
private fun AuditionButton(
    auditioning: Boolean,
    accent: Color,
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
            text = stringResource(if (auditioning) R.string.tinnitus_audition_stop else R.string.tinnitus_audition_play),
            color = fg,
            style = TextStyle(fontFamily = Inter, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun FmDialScrubber(
    hz: Float,
    accent: Color,
    onHzDrag: (Float) -> Unit,
    onHzSettled: () -> Unit,
) {
    val tokens = LocalMetiqColors.current
    val fraction = hzToFraction(hz)

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tokens.foreground)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val pad = 12.dp.toPx()
                        val usable = (size.width - 2f * pad).coerceAtLeast(1f)
                        val f = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                        onHzDrag(fractionToHz(f))
                        onHzSettled()
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragEnd = onHzSettled) { change, _ ->
                        change.consume()
                        val pad = 12.dp.toPx()
                        val usable = (size.width - 2f * pad).coerceAtLeast(1f)
                        val f = ((change.position.x - pad) / usable).coerceIn(0f, 1f)
                        onHzDrag(fractionToHz(f))
                    }
                },
        ) {
            val tickColor = tokens.textSecondary
            val majorTickColor = tokens.textPrimary
            Canvas(modifier = Modifier.matchParentSize()) {
                val pad = 12.dp.toPx()
                val usable = (size.width - 2f * pad).coerceAtLeast(1f)
                val cy = size.height / 2f

                // Minor FM dial ticks (40 evenly spaced logarithmic ticks)
                val minorCount = 40
                for (i in 0..minorCount) {
                    val x = pad + (i.toFloat() / minorCount) * usable
                    val h = if (i % 5 == 0) 14.dp.toPx() else 8.dp.toPx()
                    drawLine(
                        color = tickColor.copy(alpha = if (i % 5 == 0) 0.55f else 0.30f),
                        start = Offset(x, cy - h / 2f),
                        end = Offset(x, cy + h / 2f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Major frequency ticks
                MAJOR_TICKS_HZ.forEach { tickHz ->
                    val tf = hzToFraction(tickHz)
                    val x = pad + tf * usable
                    val h = 22.dp.toPx()
                    drawLine(
                        color = majorTickColor.copy(alpha = 0.8f),
                        start = Offset(x, cy - h / 2f),
                        end = Offset(x, cy + h / 2f),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }

                // Glowing FM tuning needle at current frequency
                val needleX = pad + fraction * usable
                drawLine(
                    color = accent.copy(alpha = 0.35f),
                    start = Offset(needleX, 4.dp.toPx()),
                    end = Offset(needleX, size.height - 4.dp.toPx()),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent,
                    start = Offset(needleX, 4.dp.toPx()),
                    end = Offset(needleX, size.height - 4.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = accent,
                    radius = 5.dp.toPx(),
                    center = Offset(needleX, cy),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MAJOR_TICK_LABELS.forEach { label ->
                Text(
                    text = label,
                    color = tokens.textSecondary,
                    style = TextStyle(fontFamily = Inter, fontSize = 10.sp),
                )
            }
        }
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
                fontSize = 14.sp,
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

@Composable
private fun EditFrequencyDialog(
    initialHz: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(initialHz.toString()) }
    val parsed = text.toIntOrNull()
    val valid = parsed != null && parsed in MIN_TINNITUS_HZ.toInt()..MAX_TINNITUS_HZ.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tinnitus_edit_hz_title), fontFamily = Inter) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input ->
                    if (input.length <= 5 && input.all { it.isDigit() }) {
                        text = input
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { parsed?.let(onConfirm) },
            ) {
                Text(stringResource(R.string.tinnitus_edit_hz_confirm), fontFamily = Inter)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), fontFamily = Inter)
            }
        },
    )
}
