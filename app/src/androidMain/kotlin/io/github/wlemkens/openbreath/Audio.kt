package io.github.wlemkens.openbreath

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.SoundPool
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val SR = 44100
private const val TAG = "OpenBreath"

/**
 * How long [PhaseMarkers.prepare] will wait for SoundPool to finish decoding.
 *
 * Long enough for a 200 KB mp3 on a slow phone, short enough that it is not felt after a tap on
 * Start. Bounded on purpose: a decode that never finishes should cost a silent bowl, not a
 * session that will not begin.
 */
private const val DECODE_WAIT_MS = 1_500L

/**
 * The sink for [WaveDsp]: a thread, an AudioTrack, and nothing else.
 *
 * Every constant and every filter that makes the sound is in commonMain/WaveDsp.kt, so the tuning
 * is one copy shared with iOS rather than two that drift. What stays here is the part Android
 * actually owns — where the samples go.
 */
class WaveSynth {
    private val dsp = WaveDsp(SR)

    var openness: Float
        get() = dsp.openness
        set(value) { dsp.openness = value }
    var wavesGain: Float
        get() = dsp.wavesGain
        set(value) { dsp.wavesGain = value }
    var soundwaveGain: Float
        get() = dsp.soundwaveGain
        set(value) { dsp.soundwaveGain = value }

    private var thread: Thread? = null

    fun start() {
        dsp.running = true
        // a thread still fading out will simply fade back in — never two tracks at once
        if (thread?.isAlive == true) return
        dsp.prime()
        thread = Thread(::render, "WaveSynth").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    /**
     * Fire-and-forget: the render thread fades out and releases itself. Deliberately does
     * not join — this is called from the main thread and a ~1 s fade must not block a frame.
     */
    fun stop() {
        dsp.running = false
    }

    private fun render() {
        val minBytes =
            AudioTrack.getMinBufferSize(SR, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBytes, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        val block = FloatArray(1024)
        val buf = ShortArray(block.size)

        track.play()
        // keep rendering past `running` until the fade-out has actually reached silence
        while (!dsp.finished) {
            dsp.render(block)
            for (i in block.indices) {
                // the exact inverse of the scaling WaveDsp applies, so these are the same shorts
                // the loop wrote when it lived here
                buf[i] = (block[i] * 32768f).toInt().coerceIn(-32768, 32767).toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop()
        track.release()
    }
}

// BELL_HZ, TICK_HZ and the synthesis of both moved to commonMain/MarkerSynth.kt: they are
// arithmetic over a sample rate, and iOS strikes the same bell from the same numbers.

/**
 * The sound at the *end* of a phase: the user's own mp3 if they picked one, otherwise the
 * bundled singing bowl or a synthesized bell.
 */
class PhaseMarkers(private val context: Context) {
    private val pool = SoundPool.Builder()
        // streams stay live for the length of the bowl's fade, so leave room for a few
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /**
     * A resource written into the cache so SoundPool has a path to open. Rewritten only when it
     * is absent or the wrong length, so a session start after the first costs no IO at all.
     */
    private fun spill(name: String, bytes: ByteArray): File {
        val file = File(context.cacheDir, name)
        if (!file.exists() || file.length() != bytes.size.toLong()) file.writeBytes(bytes)
        return file
    }

    private val loaded = mutableMapOf<String, Int>()
    private val fades = Handler(Looper.getMainLooper())
    private var bowl = -1
    private var endBowl = -1
    private var endStream = 0
    private var bell: AudioTrack? = null
    private val ticks = mutableMapOf<Float, AudioTrack>()

    /** Samples asked for but not decoded yet, so [prepare] can wait for them. */
    private val pending = mutableMapOf<Int, CompletableDeferred<Unit>>()

    init {
        // a marker that silently fails to decode is a phase boundary with no sound at all,
        // which is invisible without this
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) Log.w(TAG, "marker sample $sampleId failed to decode (status $status)")
            pending.remove(sampleId)?.complete(Unit)
        }
    }

    /**
     * Hands back the sample id and something to wait on. Registering the waiter immediately after
     * the load is safe without a lock: SoundPool calls the listener on the thread that made it,
     * which is this one, and nothing suspends in between.
     */
    private fun load(name: String, bytes: ByteArray): Int {
        val id = pool.load(spill(name, bytes).path, 1)
        if (id > 0) pending[id] = CompletableDeferred()
        return id
    }

    /**
     * Decode every recording the preset points at up front — a phase change must not wait on IO.
     *
     * SoundPool decodes asynchronously; this runs at session start and the earliest possible phase
     * end is seconds later, so a sample is ready long before it is needed.
     */
    suspend fun prepare(preset: Preset) {
        // the two bowls are commonMain's resources now, not res/raw, so that iOS can read the
        // same files. SoundPool will not take bytes — only a path or a descriptor — so they are
        // spilled to the cache once and loaded from there. The cache is the right place for it:
        // losing the copy costs one decode, and the resource it came from is still in the APK.
        if (bowl < 0) {
            bowlBytes(Bowl.PHASE)?.let { bytes ->
                runCatching { bowl = load("singing_bowl.mp3", bytes) }
                    .onFailure { Log.w(TAG, "could not load the singing bowl", it) }
            } ?: Log.w(TAG, "the singing bowl resource is missing or empty")
        }
        if (endBowl < 0) {
            bowlBytes(Bowl.SESSION_END)?.let { bytes ->
                runCatching { endBowl = load("session_end.mp3", bytes) }
                    .onFailure { Log.w(TAG, "could not load the session end bowl", it) }
            } ?: Log.w(TAG, "the session end bowl resource is missing or empty")
        }
        for (phase in Phase.entries) {
            val uri = preset.soundOf(phase).markerUri ?: continue
            if (uri in loaded) continue
            // a file the user has since deleted or revoked must not take the session down
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")!!.use {
                    val id = pool.load(it, 1)
                    loaded[uri] = id
                    if (id > 0) pending[id] = CompletableDeferred()
                }
            }
        }

        // Build the synthesised tracks this preset will actually strike. They were built on first
        // use, which was fine while the first marker was a phase *end* seconds away; now that a
        // marker opens the session, the first strike would otherwise be the one constructing it.
        for (phase in Phase.entries) {
            val sound = preset.soundOf(phase)
            if (sound.mode != SoundMode.MARKER || sound.markerUri != null) continue
            when (sound.tone) {
                MarkerTone.BELL -> bell = bell ?: struck(BELL_HZ)
                MarkerTone.TICK -> ticks.getOrPut(tickRate(phase)) { ticked(TICK_HZ * tickRate(phase)) }
                MarkerTone.GONG -> Unit
            }
        }

        // And wait for the decodes, which is the whole reason this function suspends. SoundPool
        // decodes off-thread and a sample asked for too early simply does not sound: pool.play
        // returns stream 0 and the boundary passes in silence. That is what a marker at t=0 hit.
        //
        // Bounded, because a decode that never completes must cost a silent bowl and not a
        // session that will not start.
        withTimeoutOrNull(DECODE_WAIT_MS) {
            pending.values.toList().forEach { it.await() }
        } ?: Log.w(TAG, "a marker sample was still decoding after ${DECODE_WAIT_MS}ms")
    }

    /**
     * Identity of the sound a phase would play. Two phases ending on the same instant — which
     * is what a zero-length hold between them means — must only sound once.
     */
    fun soundIdOf(phase: Phase, preset: Preset): Any =
        preset.soundOf(phase).let { it.markerUri ?: (it.tone to markerRate(it.tone, phase)) }

    fun play(phase: Phase, preset: Preset) {
        val sound = preset.soundOf(phase)
        val id = sound.markerUri?.let { loaded[it] }
        when {
            // the user's own file plays whole — truncating their choice is not ours to do
            id != null -> pool.play(id, 1f, 1f, 1, 0, 1f)
            sound.tone == MarkerTone.GONG -> playBowl(gongRate(phase))
            sound.tone == MarkerTone.TICK -> playTick(tickRate(phase))
            else -> playBell()
        }
    }

    /** The bowl that marks the end of a whole session. Rings out on its own; no fade needed. */
    fun playSessionEnd() {
        if (endBowl < 0) return
        endStream = pool.play(endBowl, 1f, 1f, 1, 0, 1f)
        if (endStream == 0) Log.w(TAG, "session end bowl id=$endBowl did not start")
    }

    /**
     * Cuts the end bowl short. It rings for 12 s, which is long enough that starting another
     * session would otherwise play the first breath over the tail of the last one.
     */
    fun stopSessionEnd() {
        if (endStream != 0) {
            pool.stop(endStream)
            endStream = 0
        }
    }

    /** The bowl, pitched by [gongRate] and faded so its 15 s tail doesn't outstay the phase. */
    private fun playBowl(rate: Float) {
        if (bowl < 0) return
        val stream = pool.play(bowl, 1f, 1f, 1, 0, rate)
        if (stream == 0) {
            // not finished decoding yet, or every stream is busy
            Log.w(TAG, "bowl id=$bowl did not start")
            return
        }
        val steps = 10
        for (i in 1..steps) {
            val volume = 1f - i.toFloat() / steps
            fades.postDelayed({
                if (volume <= 0f) pool.stop(stream) else pool.setVolume(stream, volume, volume)
            }, GONG_FADE_START_MS + GONG_FADE_MS * i / steps)
        }
    }

    /**
     * ponytail: one static AudioTrack for the bell, so two strikes inside its 1.2 s tail cut
     * each other off. Phases are seconds apart; give it a SoundPool entry if that changes.
     */
    private fun playBell() {
        val track = bell ?: struck(BELL_HZ).also { bell = it }
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    /**
     * One track per pitch, kept because a tick is 60 ms of buffer and rendering one on the
     * boundary itself would be the audible thing about it.
     */
    private fun playTick(rate: Float) {
        val track = ticks.getOrPut(rate) { ticked(TICK_HZ * rate) }
        runCatching {
            track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun release() {
        fades.removeCallbacksAndMessages(null)
        pool.release()
        bell?.release()
        bell = null
        ticks.values.forEach { it.release() }
        ticks.clear()
        bowl = -1
        endBowl = -1
        endStream = 0
        loaded.clear()
    }

    /** Wraps commonMain's bell in a static track. */
    private fun struck(hz: Float): AudioTrack = staticTrack(struckMarker(hz, SR).toPcm())

    /** Wraps commonMain's tick in a static track. */
    private fun ticked(hz: Float): AudioTrack = staticTrack(tickMarker(hz, SR).toPcm())

    /** The exact inverse of the scaling MarkerSynth applies, so these are the same samples. */
    private fun FloatArray.toPcm() = ShortArray(size) { (this[it] * 32768f).toInt().toShort() }

    private fun staticTrack(pcm: ShortArray): AudioTrack {
        val n = pcm.size
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SR)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(n * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
            .also { it.write(pcm, 0, n) }
    }
}
