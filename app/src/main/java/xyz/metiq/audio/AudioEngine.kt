package xyz.metiq.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val FADE_STEP_MS = 10L
private const val MAX_FADE_MILLIS = 30_000L
private const val NOISE_BLOCK_FRAMES = 1024
private const val BUFFER_BLOCKS = 16
private const val WARMTH_GLIDE = 0.2f
private const val SYNTH_SAMPLE_RATE = 48000
private const val SYNTH_BLOCK_FRAMES = 1024
private const val SYNTH_GLIDE = 0.08
private const val SYNTH_AMPLITUDE = 0.5
private const val TINNITUS_AMPLITUDE = 0.35

const val BINAURAL_ID = "binaural"
const val TINNITUS_ID = "tinnitus"
const val TINNITUS_AUDITION_ID = "tinnitus_audition"

enum class TinnitusMode(val id: String, val defaultSessionSeconds: Long?) {
    AM_RI("tinnitus_am_ri", 3L * 60L),          // 3-minute Residual Inhibition trigger
    BIMODAL("tinnitus_bimodal", 30L * 60L),     // 30-minute Shore/Lenire bimodal STDP session
    NOTCH("tinnitus_notch", 60L * 60L),         // 60-minute ISEC Mexican-hat notched therapy
    MODULATE("tinnitus_modulate", 60L * 60L),   // 60-minute Acoustic CR neuromodulation
    STOCHASTIC("tinnitus_sr", null),            // Sleep-safe / continuous sub-threshold SR calmer
    INVERT("tinnitus_invert", null),            // Continuous phase-inverted masking at MML
    MATCH("tinnitus_match", null);              // Continuous matched masking at MML

    companion object {
        fun fromId(id: String): TinnitusMode? = entries.firstOrNull { it.id == id }
    }
}

enum class TinnitusWaveform(val key: String) {
    TONE("tone"),
    NARROWBAND("narrowband");

    companion object {
        fun fromKey(key: String?): TinnitusWaveform =
            entries.firstOrNull { it.key == key } ?: TONE
    }
}

private val CR_RATIOS = doubleArrayOf(0.766, 0.900, 1.100, 1.305)

@OptIn(ExperimentalCoroutinesApi::class)
class AudioEngine(private val context: Context) {
    private data class Layer(
        val track: AudioTrack,
        var volume: Float,
        val warmthEligible: Boolean,
        val feeder: Job? = null,
    ) {
        @Volatile
        var fadeFactor: Float = 1f
        var fadeJob: Job? = null
    }

    private val layers = ConcurrentHashMap<String, Layer>()
    private val startJobs = ConcurrentHashMap<String, Job>()
    private val feederScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var masterVolume: Float = 1f
    private val fadeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var fadeJob: Job? = null

    private val vibrator: Vibrator? by lazy {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    @Volatile
    private var warmth: Float = 0f

    @Volatile
    private var carrierHz: Float = 216f

    @Volatile
    private var beatHz: Float = 10f

    @Volatile
    private var tinnitusHz: Float = 6000f

    @Volatile
    private var tinnitusMode: TinnitusMode = TinnitusMode.AM_RI

    @Volatile
    private var tinnitusWaveform: TinnitusWaveform = TinnitusWaveform.TONE

    @Volatile
    private var tinnitusBandwidth: Float = 0.5f

    @Volatile
    private var tinnitusPan: Float = 0f

    @Volatile
    private var tinnitusAmHz: Float = 10f

    @Volatile
    private var tinnitusEdgeBoost: Float = 0.6f

    @Volatile
    private var tinnitusStdpDelayMs: Int = -8

    @Volatile
    private var tinnitusAntiHabituation: Boolean = true

    @Volatile
    private var auditionOverrideHz: Float? = null

    @Volatile
    private var auditionPulsed: Boolean = false

    @Volatile
    private var fadeMillis: Long = 0L

    @Volatile
    private var timerFadeMillis: Long = 0L

    private fun fadeSteps(ms: Long = fadeMillis): Int = (ms / FADE_STEP_MS).toInt()

    private fun fadeInGain(progress: Float): Float = sqrt(progress.coerceIn(0f, 1f))

    private fun fadeOutGain(progress: Float): Float =
        if (progress >= 1f) 0f else 10f.pow(-3f * progress.coerceAtLeast(0f))

    private fun appliedVolume(l: Layer): Float =
        (l.volume * masterVolume * l.fadeFactor).coerceIn(0f, 1f)

    private fun bandwidthToQ(norm: Float): Double {
        val clamped = norm.coerceIn(0f, 1f).toDouble()
        return 2.0 * (12.0).pow(clamped) // Q from 2.0 (wide) to 24.0 (sharp)
    }

    private fun isNotchActiveOnStreams(): Boolean =
        tinnitusMode == TinnitusMode.NOTCH &&
            (layers.containsKey(TINNITUS_ID) || startJobs.containsKey(TINNITUS_ID))

    private fun fireMicroHapticPulse() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        runCatching {
            v.vibrate(VibrationEffect.createOneShot(14L, 95))
        }
    }

    fun startLayer(
        id: String,
        assetPath: String,
        volume: Float = 1f,
        warmthEligible: Boolean = false,
    ) {
        if (layers.containsKey(id) || startJobs.containsKey(id)) return
        val job = feederScope.launch {
            val pcm = try {
                PcmStore.awaitPcm(context, id, assetPath)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                return@launch
            }
            val track = withContext(Dispatchers.IO) { buildStreamTrack(pcm, 0f) }
            if (!isActive) {
                runCatching { track.release() }
                return@launch
            }
            val feeder = launchFeeder(track, pcm, filtered = warmthEligible)
            val layer = Layer(track, volume, warmthEligible, feeder)
            layer.fadeFactor = 0f
            layers[id] = layer
            rampIn(layer)
        }
        job.invokeOnCompletion { startJobs.remove(id, job) }
        startJobs[id] = job
    }

    private fun rampIn(l: Layer): Job {
        l.fadeJob?.cancel()
        val job = fadeScope.launch {
            val from = l.fadeFactor
            val steps = fadeSteps()
            for (i in 1..steps) {
                l.fadeFactor = from + (1f - from) * fadeInGain(i.toFloat() / steps)
                runCatching { l.track.setVolume(appliedVolume(l)) }
                delay(FADE_STEP_MS)
            }
            l.fadeFactor = 1f
            runCatching { l.track.setVolume(appliedVolume(l)) }
        }
        l.fadeJob = job
        return job
    }

    private fun warmthCutoffHz(w: Float): Float {
        val fMax = 18000.0
        val fMin = 2200.0
        return (fMax * (fMin / fMax).pow(w.coerceIn(0f, 1f).toDouble())).toFloat()
    }

    private fun buildStreamTrack(pcm: Pcm, effective: Float): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            pcm.sampleRate, pcm.channelMask, AudioFormat.ENCODING_PCM_16BIT,
        )
        val desired = NOISE_BLOCK_FRAMES * pcm.channelCount * 2 * BUFFER_BLOCKS
        val t = buildTrack(pcm.sampleRate, pcm.channelMask, maxOf(minBuf, desired), stream = true)
        t.setVolume(effective)
        return t
    }

    private fun launchFeeder(track: AudioTrack, pcm: Pcm, filtered: Boolean): Job =
        feederScope.launch {
            val src = pcm.data
            val ch = pcm.channelCount
            val frames = src.size / ch
            if (frames == 0) {
                runCatching { track.release() }
                return@launch
            }
            val sr = pcm.sampleRate
            val fcMax = (sr * 0.45f)
            val block = NOISE_BLOCK_FRAMES
            val total = block * ch
            val buf = ShortArray(total)
            val x1 = DoubleArray(ch); val x2 = DoubleArray(ch)
            val y1 = DoubleArray(ch); val y2 = DoubleArray(ch)
            var fc = warmthCutoffHz(warmth).coerceIn(2200f, fcMax)
            var b0 = 0.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0

            // 3-Stage ISEC ("Mexican-Hat") Cascaded Biquad state on stream layers:
            // Stage 1: Lower shoulder peak boost, Stage 2: Upper shoulder peak boost, Stage 3: Center Notch
            val lx1 = DoubleArray(ch); val lx2 = DoubleArray(ch); val ly1 = DoubleArray(ch); val ly2 = DoubleArray(ch)
            val ux1 = DoubleArray(ch); val ux2 = DoubleArray(ch); val uy1 = DoubleArray(ch); val uy2 = DoubleArray(ch)
            val nx1 = DoubleArray(ch); val nx2 = DoubleArray(ch); val ny1 = DoubleArray(ch); val ny2 = DoubleArray(ch)
            var curNotchHz = tinnitusHz.toDouble().coerceIn(100.0, fcMax.toDouble())
            var lb0 = 1.0; var lb1 = 0.0; var lb2 = 0.0; var la1 = 0.0; var la2 = 0.0
            var ub0 = 1.0; var ub1 = 0.0; var ub2 = 0.0; var ua1 = 0.0; var ua2 = 0.0
            var nb0 = 1.0; var nb1 = 0.0; var nb2 = 0.0; var na1 = 0.0; var na2 = 0.0
            var notchBypassed = true

            fun recompute(f: Double) {
                val w0 = 2.0 * PI * f / sr
                val cw = cos(w0)
                val alpha = sin(w0) / (2.0 * 0.70710678)
                val a0 = 1.0 + alpha
                b0 = ((1.0 - cw) / 2.0) / a0
                b1 = (1.0 - cw) / a0
                b2 = b0
                a1 = (-2.0 * cw) / a0
                a2 = (1.0 - alpha) / a0
            }

            fun recomputeIsecNotch(f: Double, q: Double, edgeBoostNorm: Double) {
                // Center Band-Stop Notch at f
                val w0 = 2.0 * PI * f / sr
                val cw = cos(w0)
                val alpha = sin(w0) / (2.0 * q.coerceIn(0.7, 30.0))
                val a0 = 1.0 + alpha
                nb0 = 1.0 / a0
                nb1 = (-2.0 * cw) / a0
                nb2 = 1.0 / a0
                na1 = nb1
                na2 = (1.0 - alpha) / a0

                // Flanking Peaking EQ filters (Stein, Okamoto & Pantev 2015 ISEC-TMNM)
                // Boosts the spectral edges immediately adjacent to the notch by up to +9 dB
                val dBgain = edgeBoostNorm.coerceIn(0.0, 1.0) * 9.0
                val ampA = 10.0.pow(dBgain / 40.0)
                val fLow = (f * 0.78).coerceIn(80.0, fcMax.toDouble())
                val fHigh = (f * 1.28).coerceIn(100.0, fcMax.toDouble())
                val qPeak = 3.5

                val wL = 2.0 * PI * fLow / sr
                val cwL = cos(wL)
                val alphaL = sin(wL) / (2.0 * qPeak)
                val a0L = 1.0 + alphaL / ampA
                lb0 = (1.0 + alphaL * ampA) / a0L
                lb1 = (-2.0 * cwL) / a0L
                lb2 = (1.0 - alphaL * ampA) / a0L
                la1 = lb1
                la2 = (1.0 - alphaL / ampA) / a0L

                val wU = 2.0 * PI * fHigh / sr
                val cwU = cos(wU)
                val alphaU = sin(wU) / (2.0 * qPeak)
                val a0U = 1.0 + alphaU / ampA
                ub0 = (1.0 + alphaU * ampA) / a0U
                ub1 = (-2.0 * cwU) / a0U
                ub2 = (1.0 - alphaU * ampA) / a0U
                ua1 = ub1
                ua2 = (1.0 - alphaU / ampA) / a0U
            }

            var pos = 0
            var started = false
            var bypassed = false
            val previousPriority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

            fun copyBlock() {
                var f = 0
                while (f < block) {
                    val n = minOf(block - f, frames - pos)
                    System.arraycopy(src, pos * ch, buf, f * ch, n * ch)
                    pos += n
                    f += n
                    if (pos >= frames) pos = 0
                }
            }

            try {
                while (isActive) {
                    if (filtered) {
                        val target = warmthCutoffHz(warmth).coerceIn(2200f, fcMax)
                        fc += (target - fc) * WARMTH_GLIDE
                        if (warmth == 0f && target - fc < 1f) {
                            bypassed = true
                            copyBlock()
                        } else {
                            if (bypassed) {
                                val base = pos * ch
                                for (c in 0 until ch) {
                                    val x0 = src[base + c].toDouble()
                                    x1[c] = x0; x2[c] = x0; y1[c] = x0; y2[c] = x0
                                }
                                bypassed = false
                            }
                            recompute(fc.toDouble())
                            for (f in 0 until block) {
                                val base = pos * ch
                                for (c in 0 until ch) {
                                    val x0 = src[base + c].toDouble()
                                    val y0 = b0 * x0 + b1 * x1[c] + b2 * x2[c] - a1 * y1[c] - a2 * y2[c]
                                    x2[c] = x1[c]; x1[c] = x0; y2[c] = y1[c]; y1[c] = y0
                                    buf[f * ch + c] = y0.coerceIn(-32768.0, 32767.0).toInt().toShort()
                                }
                                pos++
                                if (pos >= frames) pos = 0
                            }
                        }
                    } else {
                        copyBlock()
                    }

                    // Apply 3-Stage ISEC ("Mexican-Hat") Notch Filter when Notch Therapy is active
                    if (isNotchActiveOnStreams()) {
                        if (notchBypassed) {
                            for (c in 0 until ch) {
                                lx1[c] = 0.0; lx2[c] = 0.0; ly1[c] = 0.0; ly2[c] = 0.0
                                ux1[c] = 0.0; ux2[c] = 0.0; uy1[c] = 0.0; uy2[c] = 0.0
                                nx1[c] = 0.0; nx2[c] = 0.0; ny1[c] = 0.0; ny2[c] = 0.0
                            }
                            notchBypassed = false
                        }
                        val targetNotch = tinnitusHz.toDouble().coerceIn(100.0, fcMax.toDouble())
                        curNotchHz += (targetNotch - curNotchHz) * SYNTH_GLIDE
                        val qNotch = (1.4 + tinnitusBandwidth.coerceIn(0f, 1f) * 6.6)
                        recomputeIsecNotch(curNotchHz, qNotch, tinnitusEdgeBoost.toDouble())
                        for (f in 0 until block) {
                            val base = f * ch
                            for (c in 0 until ch) {
                                val s0 = buf[base + c].toDouble() * 0.72 // headroom for flanking boost
                                val s1 = lb0 * s0 + lb1 * lx1[c] + lb2 * lx2[c] - la1 * ly1[c] - la2 * ly2[c]
                                lx2[c] = lx1[c]; lx1[c] = s0; ly2[c] = ly1[c]; ly1[c] = s1

                                val s2 = ub0 * s1 + ub1 * ux1[c] + ub2 * ux2[c] - ua1 * uy1[c] - ua2 * uy2[c]
                                ux2[c] = ux1[c]; ux1[c] = s1; uy2[c] = uy1[c]; uy1[c] = s2

                                val s3 = nb0 * s2 + nb1 * nx1[c] + nb2 * nx2[c] - na1 * ny1[c] - na2 * ny2[c]
                                nx2[c] = nx1[c]; nx1[c] = s2; ny2[c] = ny1[c]; ny1[c] = s3
                                buf[base + c] = s3.coerceIn(-32768.0, 32767.0).toInt().toShort()
                            }
                        }
                    } else {
                        notchBypassed = true
                    }

                    var off = 0
                    while (off < total && isActive) {
                        val n = track.write(buf, off, total - off, AudioTrack.WRITE_BLOCKING)
                        if (n < 0) return@launch
                        off += n
                        if (!started && off > 0) {
                            track.play()
                            started = true
                        }
                    }
                }
            } finally {
                Process.setThreadPriority(previousPriority)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }

    fun setWarmth(value: Float) {
        warmth = value.coerceIn(0f, 1f)
    }

    fun setFadeMillis(ms: Long) {
        fadeMillis = ms.coerceIn(0L, MAX_FADE_MILLIS)
    }

    fun setTimerFadeMillis(ms: Long) {
        timerFadeMillis = ms.coerceIn(0L, MAX_FADE_MILLIS)
    }

    fun setBinaural(carrierHz: Float, beatHz: Float) {
        this.carrierHz = carrierHz.coerceIn(20f, 1500f)
        this.beatHz = beatHz.coerceIn(0f, 50f)
    }

    fun setTinnitusParams(
        hz: Float = this.tinnitusHz,
        mode: TinnitusMode = this.tinnitusMode,
        waveform: TinnitusWaveform = this.tinnitusWaveform,
        bandwidth: Float = this.tinnitusBandwidth,
        pan: Float = this.tinnitusPan,
        amHz: Float = this.tinnitusAmHz,
        edgeBoost: Float = this.tinnitusEdgeBoost,
        stdpDelayMs: Int = this.tinnitusStdpDelayMs,
        antiHabituation: Boolean = this.tinnitusAntiHabituation,
    ) {
        this.tinnitusHz = hz.coerceIn(100f, 16000f)
        this.tinnitusMode = mode
        this.tinnitusWaveform = waveform
        this.tinnitusBandwidth = bandwidth.coerceIn(0f, 1f)
        this.tinnitusPan = pan.coerceIn(-1f, 1f)
        this.tinnitusAmHz = amHz.coerceIn(1f, 80f)
        this.tinnitusEdgeBoost = edgeBoost.coerceIn(0f, 1f)
        this.tinnitusStdpDelayMs = stdpDelayMs.coerceIn(-15, 15)
        this.tinnitusAntiHabituation = antiHabituation
    }

    fun startTinnitus(volume: Float = 0.35f) {
        stopTinnitusAudition()
        val id = TINNITUS_ID
        if (layers.containsKey(id) || startJobs.containsKey(id)) return
        val job = feederScope.launch {
            val track = withContext(Dispatchers.IO) { buildSynthTrack() }
            if (!isActive) {
                runCatching { track.release() }
                return@launch
            }
            val feeder = launchTinnitusFeeder(track, auditionOnly = false)
            val layer = Layer(track, volume, warmthEligible = false, feeder = feeder)
            layer.fadeFactor = 0f
            layers[id] = layer
            rampIn(layer)
        }
        job.invokeOnCompletion { startJobs.remove(id, job) }
        startJobs[id] = job
    }

    fun startTinnitusAudition(
        volume: Float = 0.25f,
        overrideHz: Float? = null,
        pulsed: Boolean = false,
    ) {
        auditionOverrideHz = overrideHz?.coerceIn(100f, 16000f)
        auditionPulsed = pulsed
        if (layers.containsKey(TINNITUS_ID) || startJobs.containsKey(TINNITUS_ID)) return
        val id = TINNITUS_AUDITION_ID
        layers[id]?.let { existing ->
            existing.volume = volume.coerceIn(0f, 1f)
            runCatching { existing.track.setVolume(appliedVolume(existing)) }
            return
        }
        if (startJobs.containsKey(id)) return
        val job = feederScope.launch {
            val track = withContext(Dispatchers.IO) { buildSynthTrack() }
            if (!isActive) {
                runCatching { track.release() }
                return@launch
            }
            val feeder = launchTinnitusFeeder(track, auditionOnly = true)
            val layer = Layer(track, volume, warmthEligible = false, feeder = feeder)
            layer.fadeFactor = 0f
            layers[id] = layer
            rampIn(layer)
        }
        job.invokeOnCompletion { startJobs.remove(id, job) }
        startJobs[id] = job
    }

    fun setAuditionState(
        overrideHz: Float? = this.auditionOverrideHz,
        pulsed: Boolean = this.auditionPulsed,
        volume: Float? = null,
    ) {
        this.auditionOverrideHz = overrideHz?.coerceIn(100f, 16000f)
        this.auditionPulsed = pulsed
        if (volume != null) {
            setLayerVolume(TINNITUS_AUDITION_ID, volume.coerceIn(0f, 1f))
        }
    }

    fun stopTinnitusAudition() {
        auditionOverrideHz = null
        auditionPulsed = false
        if (layers.containsKey(TINNITUS_AUDITION_ID) || startJobs.containsKey(TINNITUS_AUDITION_ID)) {
            stopLayer(TINNITUS_AUDITION_ID, fadeMs = 100L)
        }
    }

    fun isTinnitusAuditioning(): Boolean =
        layers.containsKey(TINNITUS_AUDITION_ID) || startJobs.containsKey(TINNITUS_AUDITION_ID)

    fun startBinaural(volume: Float = 1f) {
        val id = BINAURAL_ID
        if (layers.containsKey(id) || startJobs.containsKey(id)) return
        val job = feederScope.launch {
            val track = withContext(Dispatchers.IO) { buildSynthTrack() }
            if (!isActive) {
                runCatching { track.release() }
                return@launch
            }
            val feeder = launchSynthFeeder(track)
            val layer = Layer(track, volume, warmthEligible = false, feeder = feeder)
            layer.fadeFactor = 0f
            layers[id] = layer
            rampIn(layer)
        }
        job.invokeOnCompletion { startJobs.remove(id, job) }
        startJobs[id] = job
    }

    private fun buildSynthTrack(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(
            SYNTH_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val desired = SYNTH_BLOCK_FRAMES * 2 * 2 * BUFFER_BLOCKS // frames * stereo * 16-bit * headroom
        val t = buildTrack(
            SYNTH_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, maxOf(minBuf, desired), stream = true,
        )
        t.setVolume(0f)
        return t
    }

    private fun launchSynthFeeder(track: AudioTrack): Job =
        feederScope.launch {
            val sr = SYNTH_SAMPLE_RATE
            val block = SYNTH_BLOCK_FRAMES
            val total = block * 2
            val buf = ShortArray(total)
            val amp = SYNTH_AMPLITUDE * Short.MAX_VALUE
            val twoPi = 2.0 * PI
            var phaseL = 0.0
            var phaseR = 0.0
            var curCarrier = carrierHz.toDouble()
            var curBeat = beatHz.toDouble()
            var started = false
            val previousPriority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                while (isActive) {
                    curCarrier += (carrierHz - curCarrier) * SYNTH_GLIDE
                    curBeat += (beatHz - curBeat) * SYNTH_GLIDE
                    val incL = twoPi * curCarrier / sr
                    val incR = twoPi * (curCarrier + curBeat) / sr
                    for (f in 0 until block) {
                        buf[f * 2] = (sin(phaseL) * amp).toInt().toShort()
                        buf[f * 2 + 1] = (sin(phaseR) * amp).toInt().toShort()
                        phaseL += incL; if (phaseL >= twoPi) phaseL -= twoPi
                        phaseR += incR; if (phaseR >= twoPi) phaseR -= twoPi
                    }
                    var off = 0
                    while (off < total && isActive) {
                        val n = track.write(buf, off, total - off, AudioTrack.WRITE_BLOCKING)
                        if (n < 0) return@launch
                        off += n
                        if (!started && off > 0) {
                            track.play()
                            started = true
                        }
                    }
                }
            } finally {
                Process.setThreadPriority(previousPriority)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }

    private fun launchTinnitusFeeder(track: AudioTrack, auditionOnly: Boolean): Job =
        feederScope.launch {
            val sr = SYNTH_SAMPLE_RATE
            val block = SYNTH_BLOCK_FRAMES
            val total = block * 2
            val buf = ShortArray(total)
            val amp = TINNITUS_AMPLITUDE * Short.MAX_VALUE
            val twoPi = 2.0 * PI
            val fcMax = sr * 0.45

            var phase = 0.0
            var amPhase = 0.0
            var sweepPhase = 0.0
            var driftPhase = 0.0
            var curHz = (auditionOverrideHz ?: tinnitusHz).toDouble().coerceIn(100.0, 16000.0)
            var curPan = tinnitusPan.toDouble().coerceIn(-1.0, 1.0)
            var started = false

            // Biquad states for Band-Pass / Stochastic Resonance / 3-Stage ISEC Notch
            var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
            var lx1 = 0.0; var lx2 = 0.0; var ly1 = 0.0; var ly2 = 0.0
            var ux1 = 0.0; var ux2 = 0.0; var uy1 = 0.0; var uy2 = 0.0
            // Paul Kellet pink noise generator state
            var p0 = 0.0; var p1 = 0.0; var p2 = 0.0; var p3 = 0.0; var p4 = 0.0; var p5 = 0.0; var p6 = 0.0

            // Pulsed Audition state (600 ms ON / 400 ms OFF with 30 ms raised-cosine edges)
            var auditionSampleCounter = 0
            val pulsePeriodSamples = sr // 1.0 second total cycle
            val pulseOnSamples = (sr * 0.60).toInt()
            val pulseRampSamples = (sr * 0.03).toInt()

            // Coordinated Reset (CR) neuromodulation state
            val crOrder = intArrayOf(0, 1, 2, 3)
            var crStepIndex = 0
            var crSampleInSlot = 0
            val crBurstSamples = (sr * 0.160).toInt() // 160 ms tone burst
            val crSlotSamples = (sr * 0.250).toInt()  // 250 ms total slot
            var crCycleCount = 0

            // Bimodal Haptic-Audio STDP state (200 ms cycle = 5 Hz burst rate; 20 ms tone burst)
            var bimodalSampleInCycle = 0
            val bimodalCycleSamples = (sr * 0.200).toInt() // 9600 samples
            val bimodalBurstSamples = (sr * 0.020).toInt() // 960 samples
            var hapticFiredThisCycle = false

            val previousPriority = Process.getThreadPriority(Process.myTid())
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            try {
                val rng = ThreadLocalRandom.current()
                while (isActive) {
                    val baseTargetHz = (if (auditionOnly) (auditionOverrideHz ?: tinnitusHz) else tinnitusHz)
                        .toDouble()
                        .coerceIn(100.0, 16000.0)
                    curHz += (baseTargetHz - curHz) * SYNTH_GLIDE
                    val targetPan = tinnitusPan.toDouble().coerceIn(-1.0, 1.0)
                    curPan += (targetPan - curPan) * SYNTH_GLIDE

                    // Anti-Habituation Micro-Drift (TENT-A2 Trial, Conlon et al. 2022)
                    val driftFactor = if (!auditionOnly && tinnitusAntiHabituation) {
                        1.0 + 0.018 * sin(driftPhase)
                    } else {
                        1.0
                    }
                    driftPhase += twoPi * 0.07 * block / sr
                    if (driftPhase >= twoPi) driftPhase -= twoPi

                    val effectiveHz = (curHz * driftFactor).coerceIn(100.0, 16000.0)
                    val panAngle = (PI / 4.0) * (curPan + 1.0)
                    val gainL = cos(panAngle)
                    val gainR = sin(panAngle)

                    val mode = if (auditionOnly) TinnitusMode.MATCH else tinnitusMode
                    val waveform = tinnitusWaveform
                    val q = if (mode == TinnitusMode.STOCHASTIC) 1.4 else bandwidthToQ(tinnitusBandwidth)

                    // Precompute Band-Pass Biquad coefficients
                    val w0 = twoPi * effectiveHz / sr
                    val cw = cos(w0)
                    val sw = sin(w0)
                    val alphaBp = sw / (2.0 * q)
                    val a0Bp = 1.0 + alphaBp
                    val bpB0 = (alphaBp / a0Bp) * sqrt(q * 0.6)
                    val bpB2 = -bpB0
                    val bpA1 = (-2.0 * cw) / a0Bp
                    val bpA2 = (1.0 - alphaBp) / a0Bp

                    // Precompute 3-Stage ISEC ("Mexican-Hat") Notch + Flanking Boost coefficients
                    val qNotch = 1.4 + tinnitusBandwidth.coerceIn(0f, 1f) * 6.6
                    val alphaNotch = sw / (2.0 * qNotch)
                    val a0Notch = 1.0 + alphaNotch
                    val nB0 = 1.0 / a0Notch
                    val nB1 = (-2.0 * cw) / a0Notch
                    val nB2 = 1.0 / a0Notch
                    val nA1 = nB1
                    val nA2 = (1.0 - alphaNotch) / a0Notch

                    val dBgain = tinnitusEdgeBoost.coerceIn(0f, 1f).toDouble() * 9.0
                    val ampA = 10.0.pow(dBgain / 40.0)
                    val fLow = (effectiveHz * 0.78).coerceIn(80.0, fcMax)
                    val fHigh = (effectiveHz * 1.28).coerceIn(100.0, fcMax)
                    val qPeak = 3.5

                    val wL = twoPi * fLow / sr
                    val cwL = cos(wL)
                    val alphaL = sin(wL) / (2.0 * qPeak)
                    val a0L = 1.0 + alphaL / ampA
                    val lb0 = (1.0 + alphaL * ampA) / a0L
                    val lb1 = (-2.0 * cwL) / a0L
                    val lb2 = (1.0 - alphaL * ampA) / a0L
                    val la1 = lb1
                    val la2 = (1.0 - alphaL / ampA) / a0L

                    val wU = twoPi * fHigh / sr
                    val cwU = cos(wU)
                    val alphaU = sin(wU) / (2.0 * qPeak)
                    val a0U = 1.0 + alphaU / ampA
                    val ub0 = (1.0 + alphaU * ampA) / a0U
                    val ub1 = (-2.0 * cwU) / a0U
                    val ub2 = (1.0 - alphaU * ampA) / a0U
                    val ua1 = ub1
                    val ua2 = (1.0 - alphaU / ampA) / a0U

                    val inc = twoPi * effectiveHz / sr
                    val amInc = twoPi * (tinnitusAmHz.toDouble() * driftFactor) / sr
                    val sweepInc = twoPi * 0.25 / sr

                    // Bimodal STDP timing offset: audio burst starts at sample 2000 of the 9600-sample cycle;
                    // Haptic pulse fires at sample (2000 - stdpDelaySamples) so negative delay = haptic precedes/pairs with audio
                    val audioBurstStartSample = 2000
                    val stdpDelaySamples = (tinnitusStdpDelayMs * sr / 1000).coerceIn(-1500, 1500)
                    val hapticTriggerSample = (audioBurstStartSample + stdpDelaySamples).coerceIn(0, bimodalCycleSamples - 1)

                    for (f in 0 until block) {
                        var sampleL: Double
                        var sampleR: Double

                        when (mode) {
                            TinnitusMode.MATCH -> {
                                val raw = if (waveform == TinnitusWaveform.TONE) {
                                    val s = sin(phase) * amp
                                    phase += inc
                                    if (phase >= twoPi) phase -= twoPi
                                    s
                                } else {
                                    val white = (rng.nextDouble() * 2.0 - 1.0) * amp
                                    val y0 = bpB0 * white + bpB2 * x2 - bpA1 * y1 - bpA2 * y2
                                    x2 = x1; x1 = white; y2 = y1; y1 = y0
                                    y0
                                }
                                val pulseGain = if (auditionOnly && auditionPulsed) {
                                    val posInPulse = auditionSampleCounter % pulsePeriodSamples
                                    auditionSampleCounter = (auditionSampleCounter + 1) % pulsePeriodSamples
                                    when {
                                        posInPulse >= pulseOnSamples -> 0.0
                                        posInPulse < pulseRampSamples ->
                                            0.5 * (1.0 - cos(PI * posInPulse / pulseRampSamples))
                                        posInPulse > pulseOnSamples - pulseRampSamples ->
                                            0.5 * (1.0 - cos(PI * (pulseOnSamples - posInPulse) / pulseRampSamples))
                                        else -> 1.0
                                    }
                                } else {
                                    1.0
                                }
                                sampleL = raw * pulseGain * gainL
                                sampleR = raw * pulseGain * gainR
                            }

                            TinnitusMode.AM_RI -> {
                                // 10 Hz (Alpha) or 40 Hz (Gamma) 100% Sinusoidal Amplitude Modulation (Neff et al. 2017/2019)
                                val env = 0.5 * (1.0 - cos(amPhase))
                                amPhase += amInc
                                if (amPhase >= twoPi) amPhase -= twoPi
                                val raw = if (waveform == TinnitusWaveform.TONE) {
                                    val s = sin(phase) * amp
                                    phase += inc
                                    if (phase >= twoPi) phase -= twoPi
                                    s
                                } else {
                                    val white = (rng.nextDouble() * 2.0 - 1.0) * amp
                                    val y0 = bpB0 * white + bpB2 * x2 - bpA1 * y1 - bpA2 * y2
                                    x2 = x1; x1 = white; y2 = y1; y1 = y0
                                    y0
                                }
                                sampleL = raw * env * gainL
                                sampleR = raw * env * gainR
                            }

                            TinnitusMode.BIMODAL -> {
                                // Shore et al. (2023) & TENT-A3 (2024) Bimodal Auditory-Somatosensory STDP
                                if (!hapticFiredThisCycle && bimodalSampleInCycle >= hapticTriggerSample) {
                                    hapticFiredThisCycle = true
                                    fireMicroHapticPulse()
                                }
                                val inAudioBurst = bimodalSampleInCycle in audioBurstStartSample until (audioBurstStartSample + bimodalBurstSamples)
                                val raw = if (inAudioBurst) {
                                    val burstIdx = bimodalSampleInCycle - audioBurstStartSample
                                    val env = 0.5 * (1.0 - cos(twoPi * burstIdx.toDouble() / bimodalBurstSamples))
                                    val s = sin(phase) * env * amp * 0.95
                                    phase += inc
                                    if (phase >= twoPi) phase -= twoPi
                                    s
                                } else {
                                    0.0
                                }
                                bimodalSampleInCycle++
                                if (bimodalSampleInCycle >= bimodalCycleSamples) {
                                    bimodalSampleInCycle = 0
                                    phase = 0.0
                                    hapticFiredThisCycle = false
                                }
                                sampleL = raw * gainL
                                sampleR = raw * gainR
                            }

                            TinnitusMode.INVERT -> {
                                if (waveform == TinnitusWaveform.TONE) {
                                    val invShift = PI + 0.35 * sin(sweepPhase)
                                    val sL = -sin(phase) * amp
                                    val sR = sin(phase + invShift) * amp
                                    phase += inc
                                    if (phase >= twoPi) phase -= twoPi
                                    sweepPhase += sweepInc
                                    if (sweepPhase >= twoPi) sweepPhase -= twoPi
                                    sampleL = sL * gainL
                                    sampleR = sR * gainR
                                } else {
                                    val white = (rng.nextDouble() * 2.0 - 1.0) * amp
                                    val y0 = bpB0 * white + bpB2 * x2 - bpA1 * y1 - bpA2 * y2
                                    x2 = x1; x1 = white; y2 = y1; y1 = y0
                                    sampleL = -y0 * gainL
                                    sampleR = y0 * gainR
                                }
                            }

                            TinnitusMode.NOTCH -> {
                                // 3-Stage ISEC ("Mexican-Hat") Notched Pink Noise Bed
                                val white = rng.nextDouble() * 2.0 - 1.0
                                p0 = 0.99886 * p0 + white * 0.0555179
                                p1 = 0.99332 * p1 + white * 0.0750759
                                p2 = 0.96900 * p2 + white * 0.1538520
                                p3 = 0.86650 * p3 + white * 0.3104856
                                p4 = 0.55000 * p4 + white * 0.5329522
                                p5 = -0.7616 * p5 - white * 0.0168980
                                val pink = (p0 + p1 + p2 + p3 + p4 + p5 + p6 + white * 0.5362) * 0.11 * amp
                                p6 = white * 0.115926

                                val s1 = lb0 * pink + lb1 * lx1 + lb2 * lx2 - la1 * ly1 - la2 * ly2
                                lx2 = lx1; lx1 = pink; ly2 = ly1; ly1 = s1

                                val s2 = ub0 * s1 + ub1 * ux1 + ub2 * ux2 - ua1 * uy1 - ua2 * uy2
                                ux2 = ux1; ux1 = s1; uy2 = uy1; uy1 = s2

                                val notched = nB0 * s2 + nB1 * x1 + nB2 * x2 - nA1 * y1 - nA2 * y2
                                x2 = x1; x1 = s2; y2 = y1; y1 = notched
                                sampleL = notched * gainL
                                sampleR = notched * gainR
                            }

                            TinnitusMode.MODULATE -> {
                                // Acoustic Coordinated Reset (CR) neuromodulation + Anti-Habituation Drift
                                val activeCycle = (crCycleCount % 5) < 3
                                val s = if (activeCycle && crSampleInSlot < crBurstSamples) {
                                    val ratio = CR_RATIOS[crOrder[crStepIndex]]
                                    val toneHz = (effectiveHz * ratio).coerceIn(80.0, 18000.0)
                                    val toneInc = twoPi * toneHz / sr
                                    val env = 0.5 * (1.0 - cos(twoPi * crSampleInSlot.toDouble() / crBurstSamples))
                                    val v = sin(phase) * env * amp * 0.85
                                    phase += toneInc
                                    if (phase >= twoPi) phase -= twoPi
                                    v
                                } else {
                                    0.0
                                }
                                crSampleInSlot++
                                if (crSampleInSlot >= crSlotSamples) {
                                    crSampleInSlot = 0
                                    phase = 0.0
                                    crStepIndex++
                                    if (crStepIndex >= crOrder.size) {
                                        crStepIndex = 0
                                        crCycleCount = (crCycleCount + 1) % 1000
                                        for (i in crOrder.lastIndex downTo 1) {
                                            val j = rng.nextInt(i + 1)
                                            val tmp = crOrder[i]
                                            crOrder[i] = crOrder[j]
                                            crOrder[j] = tmp
                                        }
                                    }
                                }
                                sampleL = s * gainL
                                sampleR = s * gainR
                            }

                            TinnitusMode.STOCHASTIC -> {
                                // Krauss & Schilling (2016) Stochastic Resonance DCN Calmer:
                                // Gentle octave-band stochastic 1/f noise centered around f_T
                                val white = rng.nextDouble() * 2.0 - 1.0
                                p0 = 0.99886 * p0 + white * 0.0555179
                                p1 = 0.99332 * p1 + white * 0.0750759
                                p2 = 0.96900 * p2 + white * 0.1538520
                                val softColored = (p0 + p1 + p2 + white * 0.35) * 0.22 * amp
                                val srBand = bpB0 * softColored + bpB2 * x2 - bpA1 * y1 - bpA2 * y2
                                x2 = x1; x1 = softColored; y2 = y1; y1 = srBand
                                sampleL = srBand * gainL
                                sampleR = srBand * gainR
                            }
                        }

                        buf[f * 2] = sampleL.coerceIn(-32768.0, 32767.0).toInt().toShort()
                        buf[f * 2 + 1] = sampleR.coerceIn(-32768.0, 32767.0).toInt().toShort()
                    }

                    var off = 0
                    while (off < total && isActive) {
                        val n = track.write(buf, off, total - off, AudioTrack.WRITE_BLOCKING)
                        if (n < 0) return@launch
                        off += n
                        if (!started && off > 0) {
                            track.play()
                            started = true
                        }
                    }
                }
            } finally {
                Process.setThreadPriority(previousPriority)
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }

    fun stopLayer(id: String, fadeMs: Long = fadeMillis) {
        startJobs.remove(id)?.cancel()
        val l = layers.remove(id) ?: return
        l.fadeJob?.cancel()
        fadeScope.launch {
            val from = l.fadeFactor
            val steps = fadeSteps(fadeMs)
            for (i in 1..steps) {
                l.fadeFactor = from * fadeOutGain(i.toFloat() / steps)
                runCatching { l.track.setVolume(appliedVolume(l)) }
                delay(FADE_STEP_MS)
            }
            if (l.feeder != null) {
                l.feeder.cancel()
            } else {
                runCatching { l.track.stop() }
                runCatching { l.track.release() }
            }
        }
    }

    fun setLayerVolume(id: String, volume: Float) {
        val l = layers[id] ?: return
        l.volume = volume
        l.track.setVolume(appliedVolume(l))
    }

    fun setMasterVolume(volume: Float) {
        masterVolume = volume.coerceIn(0f, 1f)
        for ((_, l) in layers) {
            l.track.setVolume(appliedVolume(l))
        }
    }

    fun pauseAll(fade: Boolean = true) {
        stopTinnitusAudition()
        fadeJob?.cancel()
        if (!fade) {
            for ((_, l) in layers) runCatching { l.track.pause() }
            return
        }
        fadeJob = fadeScope.launch {
            val steps = fadeSteps()
            for (i in 1..steps) {
                val factor = fadeOutGain(i.toFloat() / steps)
                for ((_, l) in layers) {
                    runCatching { l.track.setVolume(appliedVolume(l) * factor) }
                }
                delay(FADE_STEP_MS)
            }
            for ((_, l) in layers) {
                runCatching { l.track.pause() }
                runCatching { l.track.setVolume(appliedVolume(l)) }
            }
        }
    }

    fun resumeAll() {
        fadeJob?.cancel()
        fadeJob = fadeScope.launch {
            for ((_, l) in layers) {
                runCatching {
                    l.track.setVolume(0f)
                    l.track.play()
                }
            }
            val steps = fadeSteps()
            for (i in 1..steps) {
                val factor = fadeInGain(i.toFloat() / steps)
                for ((_, l) in layers) {
                    runCatching { l.track.setVolume(appliedVolume(l) * factor) }
                }
                delay(FADE_STEP_MS)
            }
            for ((_, l) in layers) {
                runCatching { l.track.setVolume(appliedVolume(l)) }
            }
        }
    }

    fun release() {
        fadeJob?.cancel()
        startJobs.values.forEach { it.cancel() }
        startJobs.clear()
        for ((_, l) in layers) {
            l.fadeJob?.cancel()
            if (l.feeder != null) {
                l.feeder.cancel()
            } else {
                runCatching { l.track.stop() }
                runCatching { l.track.release() }
            }
        }
        layers.clear()
    }

    fun activeLayerIds(): Set<String> = layers.keys.filter { it != TINNITUS_AUDITION_ID }.toSet()

    fun layerVolume(id: String): Float? = layers[id]?.volume

    fun stopAllTimerFade() {
        stopTinnitusAudition()
        layers.keys.toList().forEach { stopLayer(it, timerFadeMillis) }
    }

    private fun buildTrack(
        sampleRate: Int,
        channelMask: Int,
        bufferBytes: Int,
        stream: Boolean = false,
    ): AudioTrack {
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate).setChannelMask(channelMask).build()
        val mode = if (stream) AudioTrack.MODE_STREAM else AudioTrack.MODE_STATIC
        return AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes).setTransferMode(mode)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
            .build()
    }
}
