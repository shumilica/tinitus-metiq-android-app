package xyz.metiq.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlin.math.roundToInt
import xyz.metiq.R
import xyz.metiq.audio.BINAURAL_ID
import xyz.metiq.audio.PlaybackService
import xyz.metiq.audio.TINNITUS_ID
import xyz.metiq.audio.TinnitusMode
import xyz.metiq.audio.TinnitusWaveform

private const val FALLBACK_BINAURAL_BAND = "alpha"
private const val FALLBACK_TINNITUS_MODE = "tinnitus_am_ri"

@Stable
internal class MixPlayback {
    val levels = mutableStateMapOf<String, Float>()

    var binder by mutableStateOf<PlaybackService.EngineBinder?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var tinnitusAuditioning by mutableStateOf(false)
        private set
    private var controller: MediaController? = null

    internal var binauralDefaultVolume = DEFAULT_SOUND_LEVEL
    internal var onBinauralBand: (String) -> Unit = {}
    internal var onBinauralVolume: (Float) -> Unit = {}

    internal var tinnitusDefaultVolume = 0.35f
    internal var tinnitusThreshold = 0.12f
    internal var tinnitusMml = 0.35f
    internal var tinnitusHz = 6000f
    internal var tinnitusWaveform = "tone"
    internal var tinnitusBandwidth = 0.5f
    internal var tinnitusPan = 0f
    internal var tinnitusAmHz = 10f
    internal var tinnitusEdgeBoost = 0.5f
    internal var tinnitusStdpDelayMs = 12f
    internal var tinnitusAntiHabituation = true
    internal var tinnitusAutoTimer = true
    internal var onTinnitusMode: (String) -> Unit = {}
    internal var onTinnitusVolume: (Float) -> Unit = {}

    fun toggle(id: String) {
        if (id in levels) {
            remove(id)
        } else {
            val level = when (id) {
                in BINAURAL_BAND_IDS ->
                    levels.entries.firstOrNull { it.key in BINAURAL_BAND_IDS }?.value ?: binauralDefaultVolume
                in TINNITUS_MODE_IDS ->
                    if (id == "tinnitus_sr") {
                        tinnitusThreshold.coerceIn(0.02f, 1f)
                    } else {
                        levels.entries.firstOrNull { it.key in TINNITUS_MODE_IDS && it.key != "tinnitus_sr" }?.value
                            ?: tinnitusMml.coerceIn(0.05f, 1f)
                    }
                else -> DEFAULT_SOUND_LEVEL
            }
            add(id, level)
        }
    }

    fun startScheduledTinnitusSession(modeId: String, durationMinutes: Int) {
        if (modeId !in TINNITUS_MODE_IDS) return
        val level = if (modeId == "tinnitus_sr") {
            tinnitusThreshold.coerceIn(0.02f, 1f)
        } else {
            tinnitusMml.coerceIn(0.05f, 1f)
        }
        add(modeId, level, overrideTimerSeconds = durationMinutes.coerceAtLeast(1) * 60L)
    }

    fun setLevel(id: String, level: Float) {
        if (id !in levels) return
        levels[id] = level
        binder?.engine?.setLayerVolume(engineId(id), level)
    }

    fun settleLevel(id: String) {
        val level = levels[id] ?: return
        when {
            level <= 0f -> remove(id)
            id in BINAURAL_BAND_IDS -> onBinauralVolume(level)
            id in TINNITUS_MODE_IDS -> onTinnitusVolume(level)
        }
    }

    fun playTinnitusAudition(
        overrideHz: Float,
        pulsed: Boolean,
        volume: Float,
        wf: String,
        bw: Float,
        pan: Float,
    ) {
        val engine = binder?.engine ?: return
        val activeModeId = levels.keys.firstOrNull { it in TINNITUS_MODE_IDS }
        val mode = activeModeId?.let { TinnitusMode.fromId(it) } ?: TinnitusMode.MATCH
        engine.setTinnitusParams(
            hz = overrideHz,
            mode = mode,
            waveform = TinnitusWaveform.fromKey(wf),
            bandwidth = bw,
            pan = pan,
            amHz = tinnitusAmHz,
            edgeBoost = tinnitusEdgeBoost,
            stdpDelayMs = tinnitusStdpDelayMs,
            antiHabituation = tinnitusAntiHabituation,
        )
        if (tinnitusAuditioning) {
            engine.setAuditionState(pulsed = pulsed, overrideHz = overrideHz)
            engine.setLayerVolume("tinnitus_audition", volume.coerceIn(0.01f, 1f))
        } else {
            engine.startTinnitusAudition(
                volume = volume.coerceIn(0.01f, 1f),
                pulsed = pulsed,
                overrideHz = overrideHz,
            )
            tinnitusAuditioning = true
        }
    }

    fun stopTinnitusAudition() {
        binder?.engine?.stopTinnitusAudition()
        tinnitusAuditioning = false
        // Restore locked engine parameters after wizard auditioning
        syncTinnitusEngineParams()
    }

    fun syncTinnitusEngineParams(
        hz: Float = tinnitusHz,
        waveform: String = tinnitusWaveform,
        bandwidth: Float = tinnitusBandwidth,
        pan: Float = tinnitusPan,
        amHz: Float = tinnitusAmHz,
        edgeBoost: Float = tinnitusEdgeBoost,
        stdpDelayMs: Float = tinnitusStdpDelayMs,
        antiHabituation: Boolean = tinnitusAntiHabituation,
    ) {
        tinnitusHz = hz
        tinnitusWaveform = waveform
        tinnitusBandwidth = bandwidth
        tinnitusPan = pan
        tinnitusAmHz = amHz
        tinnitusEdgeBoost = edgeBoost
        tinnitusStdpDelayMs = stdpDelayMs
        tinnitusAntiHabituation = antiHabituation
        val activeModeId = levels.keys.firstOrNull { it in TINNITUS_MODE_IDS }
        val mode = activeModeId?.let { TinnitusMode.fromId(it) } ?: TinnitusMode.MATCH
        binder?.engine?.setTinnitusParams(
            hz = hz,
            mode = mode,
            waveform = TinnitusWaveform.fromKey(waveform),
            bandwidth = bandwidth,
            pan = pan,
            amHz = amHz,
            edgeBoost = edgeBoost,
            stdpDelayMs = stdpDelayMs,
            antiHabituation = antiHabituation,
        )
    }

    fun applyMix(mix: Map<String, Float>) {
        val valid = mix.filter { (id, level) -> level > 0f && isKnownSound(id) }
        if (valid.isEmpty() || binder == null) return
        stopTinnitusAudition()
        if (!mixMatches(levels, valid)) {
            levels.keys.filter { it !in valid }.forEach { remove(it, stopWhenEmpty = false) }
            valid.forEach { (id, level) -> if (id in levels) setLevel(id, level) else add(id, level) }
        }
        ensurePlaying()
    }

    /** Removes every sound and stops playback. */
    fun clear() {
        stopTinnitusAudition()
        if (levels.isEmpty()) return
        levels.keys.toList().forEach { remove(it, stopWhenEmpty = false) }
        controller?.stop()
        playing = false
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (levels.isEmpty()) return
        if (c.playWhenReady) {
            c.pause()
            playing = false
        } else {
            ensurePlaying()
        }
    }

    private fun add(id: String, level: Float, overrideTimerSeconds: Long? = null) {
        val bound = binder ?: return
        val engine = bound.engine
        when (id) {
            in NOISE_IDS -> engine.startLayer(id, "audio/noise/$id.ogg", level, warmthEligible = true)
            in AMBIENT_IDS -> engine.startLayer(id, "audio/ambient/$id.ogg", level)
            in BINAURAL_BAND_IDS -> {
                val band = BINAURAL_BANDS.first { it.id == id }
                val current = levels.keys.firstOrNull { it in BINAURAL_BAND_IDS }
                engine.setBinaural(BINAURAL_CARRIER_HZ, band.beatHz)
                if (current != null) {
                    levels.remove(current)
                    engine.setLayerVolume(BINAURAL_ID, level)
                } else {
                    engine.startBinaural(level)
                }
                onBinauralBand(id)
            }
            in TINNITUS_MODE_IDS -> {
                stopTinnitusAudition()
                val mode = TinnitusMode.fromId(id) ?: TinnitusMode.MATCH
                val current = levels.keys.firstOrNull { it in TINNITUS_MODE_IDS }
                engine.setTinnitusParams(
                    hz = tinnitusHz,
                    mode = mode,
                    waveform = TinnitusWaveform.fromKey(tinnitusWaveform),
                    bandwidth = tinnitusBandwidth,
                    pan = tinnitusPan,
                    amHz = tinnitusAmHz,
                    edgeBoost = tinnitusEdgeBoost,
                    stdpDelayMs = tinnitusStdpDelayMs,
                    antiHabituation = tinnitusAntiHabituation,
                )
                if (current != null) {
                    levels.remove(current)
                    engine.setLayerVolume(TINNITUS_ID, level)
                } else {
                    engine.startTinnitus(level)
                }
                onTinnitusMode(id)

                val sessionSecs = overrideTimerSeconds
                    ?: if (tinnitusAutoTimer) mode.defaultSessionSeconds else null
                if (sessionSecs != null && sessionSecs > 0L) {
                    bound.startSleepTimer(sessionSecs)
                }
            }

            else -> return
        }
        levels[id] = level
        ensurePlaying()
    }

    private fun remove(id: String, stopWhenEmpty: Boolean = true) {
        levels.remove(id) ?: return
        binder?.engine?.stopLayer(engineId(id))
        if (stopWhenEmpty && levels.isEmpty()) {
            controller?.stop()
            playing = false
        }
    }

    private fun ensurePlaying() {
        val c = controller ?: return
        binder?.requestAudioFocusNow()
        if (!c.playWhenReady) c.play()
        playing = true
    }

    internal fun attach(
        bound: PlaybackService.EngineBinder,
        savedBand: String?,
        savedTinnitusMode: String?,
    ) {
        binder = bound
        val engine = bound.engine
        syncTinnitusEngineParams()
        val ids = engine.activeLayerIds()
        ids.filter { it in NOISE_IDS || it in AMBIENT_IDS }.forEach { id ->
            if (id !in levels) levels[id] = engine.layerVolume(id) ?: DEFAULT_SOUND_LEVEL
        }
        if (BINAURAL_ID in ids && levels.keys.none { it in BINAURAL_BAND_IDS }) {
            val band = savedBand?.takeIf { it in BINAURAL_BAND_IDS } ?: FALLBACK_BINAURAL_BAND
            levels[band] = engine.layerVolume(BINAURAL_ID) ?: binauralDefaultVolume
        }
        if (TINNITUS_ID in ids && levels.keys.none { it in TINNITUS_MODE_IDS }) {
            val mode = savedTinnitusMode?.takeIf { it in TINNITUS_MODE_IDS } ?: FALLBACK_TINNITUS_MODE
            levels[mode] = engine.layerVolume(TINNITUS_ID) ?: tinnitusDefaultVolume
        }
    }

    internal fun detach() {
        binder = null
        tinnitusAuditioning = false
    }

    internal fun attachController(c: MediaController?) {
        controller = c
        playing = c != null && isPlaying(c)
    }

    internal fun onPlayerChanged(c: Player) {
        playing = isPlaying(c)
    }

    private fun isPlaying(c: Player): Boolean =
        c.playWhenReady && c.playbackState != Player.STATE_IDLE

    internal fun onPlaybackStateChanged(state: Int) {
        if (
            state == Player.STATE_IDLE &&
            controller?.playWhenReady != true &&
            binder?.engine?.activeLayerIds()?.isEmpty() == true
        ) {
            levels.clear()
        }
    }

    private fun engineId(id: String): String = when (id) {
        in BINAURAL_BAND_IDS -> BINAURAL_ID
        in TINNITUS_MODE_IDS -> TINNITUS_ID
        else -> id
    }

    private fun isKnownSound(id: String): Boolean =
        id in NOISE_IDS || id in AMBIENT_IDS || id in BINAURAL_BAND_IDS || id in TINNITUS_MODE_IDS
}

@Composable
internal fun rememberMixPlayback(
    savedBinauralBand: String?,
    binauralVolume: Float,
    onBinauralBand: (String) -> Unit,
    onBinauralVolume: (Float) -> Unit,
    savedTinnitusMode: String? = null,
    tinnitusVolume: Float = 0.35f,
    tinnitusThreshold: Float = 0.12f,
    tinnitusMml: Float = 0.35f,
    tinnitusHz: Float = 6000f,
    tinnitusWaveform: String = "tone",
    tinnitusBandwidth: Float = 0.5f,
    tinnitusPan: Float = 0f,
    tinnitusAmHz: Float = 10f,
    tinnitusEdgeBoost: Float = 0.5f,
    tinnitusStdpDelayMs: Float = 12f,
    tinnitusAntiHabituation: Boolean = true,
    tinnitusAutoTimer: Boolean = true,
    onTinnitusMode: (String) -> Unit = {},
    onTinnitusVolume: (Float) -> Unit = {},
): MixPlayback {
    val context = LocalContext.current
    val playback = remember { MixPlayback() }
    val latestSavedBand by rememberUpdatedState(savedBinauralBand)
    val latestSavedTinnitusMode by rememberUpdatedState(savedTinnitusMode)
    SideEffect {
        playback.binauralDefaultVolume = binauralVolume
        playback.onBinauralBand = onBinauralBand
        playback.onBinauralVolume = onBinauralVolume
        playback.tinnitusDefaultVolume = tinnitusVolume
        playback.tinnitusThreshold = tinnitusThreshold
        playback.tinnitusMml = tinnitusMml
        playback.tinnitusAutoTimer = tinnitusAutoTimer
        playback.onTinnitusMode = onTinnitusMode
        playback.onTinnitusVolume = onTinnitusVolume
        playback.syncTinnitusEngineParams(
            hz = tinnitusHz,
            waveform = tinnitusWaveform,
            bandwidth = tinnitusBandwidth,
            pan = tinnitusPan,
            amHz = tinnitusAmHz,
            edgeBoost = tinnitusEdgeBoost,
            stdpDelayMs = tinnitusStdpDelayMs,
            antiHabituation = tinnitusAntiHabituation,
        )
    }

    DisposableEffect(Unit) {
        var controllerFuture: ListenableFuture<MediaController>? = null
        var connectedController: MediaController? = null
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                    playback.onPlaybackStateChanged(player.playbackState)
                }
                playback.onPlayerChanged(player)
            }
        }

        fun releaseController() {
            controllerFuture?.let { f ->
                if (connectedController == null) MediaController.releaseFuture(f)
            }
            controllerFuture = null
            connectedController?.removeListener(listener)
            connectedController?.release()
            connectedController = null
            playback.attachController(null)
        }

        fun connectController() {
            releaseController()
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            controllerFuture = future
            future.addListener({
                if (controllerFuture !== future) return@addListener // superseded by a reconnect
                val c = future.get()
                connectedController = c
                playback.attachController(c)
                c.addListener(listener)
            }, ContextCompat.getMainExecutor(context))
        }

        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, b: IBinder?) {
                playback.attach(
                    bound = b as PlaybackService.EngineBinder,
                    savedBand = latestSavedBand,
                    savedTinnitusMode = latestSavedTinnitusMode,
                )
                connectController()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                playback.detach()
            }
        }
        val bindIntent = Intent(context, PlaybackService::class.java)
            .setAction(PlaybackService.ENGINE_BIND_ACTION)
        context.bindService(bindIntent, conn, Context.BIND_AUTO_CREATE)

        onDispose {
            releaseController()
            context.unbindService(conn)
        }
    }
    return playback
}

@Composable
internal fun mixLabel(levels: Map<String, Float>, mixTitle: String?, tinnitusHz: Float = 6000f): String? {
    if (levels.isEmpty()) return null
    if (mixTitle != null) return mixTitle
    if (levels.size == 1) {
        val id = levels.keys.first()
        NOISE_COLORS.firstOrNull { it.id == id }?.let { return stringResource(it.noiseTitleRes) }
        AMBIENT_SOUNDS.firstOrNull { it.id == id }?.let { return stringResource(it.labelRes) }
        BINAURAL_BANDS.firstOrNull { it.id == id }?.let {
            return stringResource(R.string.binaural_noti_label, stringResource(it.labelRes))
        }
        TINNITUS_MODES.firstOrNull { it.id == id }?.let {
            return stringResource(R.string.tinnitus_noti_label, stringResource(it.labelRes), tinnitusHz.roundToInt())
        }
    }
    return stringResource(R.string.mix_now_playing)
}

@Composable
internal fun mixSoundNames(levels: Map<String, Float>, tinnitusHz: Float = 6000f): List<String> = buildList {
    NOISE_COLORS.filter { it.id in levels }.forEach { add(stringResource(it.noiseTitleRes)) }
    AMBIENT_SOUNDS.filter { it.id in levels }.forEach { add(stringResource(it.labelRes)) }
    BINAURAL_BANDS.filter { it.id in levels }.forEach {
        add(stringResource(R.string.binaural_noti_label, stringResource(it.labelRes)))
    }
    TINNITUS_MODES.filter { it.id in levels }.forEach {
        add(stringResource(R.string.tinnitus_noti_label, stringResource(it.labelRes), tinnitusHz.roundToInt()))
    }
}
