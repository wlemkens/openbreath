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

/**
 * The bowl recording rings for nearly 15 s, longer than a breath phase. It plays at full
 * level until [GONG_FADE_START_MS], ramps down over [GONG_FADE_MS], then the stream is
 * stopped outright — about 4.2 s audible.
 *
 * Both are tuning knobs, but the bundled sample is 6 s, so pushing the total much past that
 * needs a longer cut rather than a bigger number here.
 */
private const val GONG_FADE_START_MS = 3_500L
private const val GONG_FADE_MS = 700L

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

    private val loaded = mutableMapOf<String, Int>()
    private val fades = Handler(Looper.getMainLooper())
    private var bowl = -1
    private var endBowl = -1
    private var endStream = 0
    private var bell: AudioTrack? = null
    private val ticks = mutableMapOf<Float, AudioTrack>()

    init {
        // a marker that silently fails to decode is a phase boundary with no sound at all,
        // which is invisible without this
        pool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status != 0) Log.w(TAG, "marker sample $sampleId failed to decode (status $status)")
        }
    }

    /** Decode every mp3 the preset points at up front — a phase change must not wait on IO. */
    fun prepare(preset: Preset) {
        // res/raw/singing_bowl.mp3 is media/freesound_community-singing-bowl-hit-3-33366.mp3
        // cut to 6 s. The original rings for nearly 15 s and SoundPool decodes the whole thing
        // into memory, so the cut is there to keep the sample small — we never play past ~4 s.
        //
        // SoundPool decodes asynchronously; prepare() runs at session start and the earliest
        // possible phase end is seconds later, so the sample is ready long before it is needed.
        if (bowl < 0) {
            runCatching { bowl = pool.load(context, R.raw.singing_bowl, 1) }
                .onFailure { Log.w(TAG, "could not load the singing bowl", it) }
        }
        // res/raw/session_end.mp3 is media/freesound_community-025535_singing-bowl-60767.mp3
        // cut to 12 s with its fade baked in. The original runs 50 s, which would be nearly
        // 5 MB of PCM in a SoundPool; unlike the phase gong this needs no fade in code —
        // a session that is over can simply ring out.
        if (endBowl < 0) {
            runCatching { endBowl = pool.load(context, R.raw.session_end, 1) }
                .onFailure { Log.w(TAG, "could not load the session end bowl", it) }
        }
        for (phase in Phase.entries) {
            val uri = preset.soundOf(phase).markerUri ?: continue
            if (uri in loaded) continue
            // a file the user has since deleted or revoked must not take the session down
            runCatching {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")!!.use {
                    loaded[uri] = pool.load(it, 1)
                }
            }
        }
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
