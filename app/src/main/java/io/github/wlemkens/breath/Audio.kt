package io.github.wlemkens.breath

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
import kotlin.math.sin
import kotlin.math.sqrt

private const val SR = 44100
private const val TAG = "Breath"

/**
 * Waves coming ashore: white noise squeezed into a band by two one-pole filters, the upper
 * edge riding the breath.
 *
 * Noise has no pitch to shift, so "the pitch goes up during the breath in" is a filter
 * cutoff sweep — that is what actually reads as a wave rising and falling. Every constant
 * below is a tuning knob; they were picked by ear and are meant to be adjusted.
 */
class WaveSynth {
    /** 0 = fully exhaled, 1 = fully inhaled. Written from the UI thread, read by the audio thread. */
    @Volatile
    var openness: Float = 0f

    /**
     * Per-phase level: the sound mode's on/off multiplied by [PhaseState.edge], so it dips
     * to silence at every phase boundary. Written every frame; smoothed only enough to take
     * the stair-steps off a 60 Hz update, since the ramp it follows is already gradual.
     */
    @Volatile
    var phaseGain: Float = 0f

    @Volatile
    private var alive = false
    private var thread: Thread? = null

    fun start() {
        alive = true
        // a thread still fading out will simply fade back in — never two tracks at once
        if (thread?.isAlive == true) return
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
        alive = false
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

        val buf = ShortArray(1024)
        val rnd = Random()
        var lp = 0f // upper band edge, swept by the breath
        var hp = 0f // lower band edge, fixed — kills the rumble
        var cutoff = LO_HZ
        var master = 0f
        var gate = phaseGain
        var lfo = 0.0

        track.play()
        // keep rendering past `alive` until the fade-out has actually reached silence
        while (alive || master > 1e-3f) {
            val target = if (alive) 1f else 0f
            val fade = if (alive) FADE_IN else FADE_OUT
            for (i in buf.indices) {
                // per-sample glides: openness only updates at frame rate, and stepping the
                // cutoff once per buffer zippers audibly
                master += (target - master) * fade
                gate += (phaseGain - gate) * GATE_GLIDE
                cutoff += (LO_HZ + (HI_HZ - LO_HZ) * openness - cutoff) * GLIDE

                val a = 1f - exp(-2.0 * PI * cutoff / SR).toFloat()
                lp += a * ((rnd.nextFloat() * 2f - 1f) - lp)
                hp += HP_A * (lp - hp)
                // a one-pole loses amplitude as it closes; undo that so loudness is set
                // only by `gain` below and not smuggled in by the sweep
                val band = (lp - hp) / sqrt(a / (2f - a))

                lfo += TEXTURE_HZ / SR
                val texture = 1f + 0.22f * sin(2.0 * PI * lfo).toFloat()
                val gain = (0.22f + 0.78f * openness) * texture * master * gate

                buf[i] = (band * gain * 9000f).toInt().coerceIn(-32768, 32767).toShort()
            }
            track.write(buf, 0, buf.size)
        }
        track.stop()
        track.release()
    }

    private companion object {
        const val LO_HZ = 260f // fully exhaled
        const val HI_HZ = 2100f // fully inhaled
        const val TEXTURE_HZ = 0.13 // slow undulation, so holds don't sound frozen
        const val GLIDE = 0.0004f // cutoff settles in ~60 ms
        // fast (~4 ms): the phase envelope must actually reach silence at a boundary, and a
        // slow crossfade here would smear the 0.2 s fade into inaudibility. Safe to be this
        // fast because mode changes now land while the envelope is already at zero.
        const val GATE_GLIDE = 0.006f
        const val FADE_IN = 0.00002f // ~1 s in
        const val FADE_OUT = 0.00015f // ~0.15 s out
        val HP_A = 1f - exp(-2.0 * PI * 90.0 / SR).toFloat()
    }
}

private const val BELL_HZ = 528f

/** Near-harmonic: a fundamental and its octave. */
private val BELL_PARTIALS = floatArrayOf(1f, 2f)

/**
 * The recorded bowl is the high tone as struck. The low tone is the same bowl played slower,
 * which drops its pitch — additive synthesis of a gong came out flat by comparison, so this
 * is a real recording rather than a model of one.
 *
 * Low at the top of the breath and high at the bottom, because a marker announces the
 * direction the breath is about to take rather than where it is: the end of the in-hold leads
 * into an exhale (downward), the end of the out-hold into an inhale (upward). The inhale
 * shares the low pitch, so a zero-length hold between them collapses to a single strike.
 */
private const val GONG_LOW_RATE = 0.6f

fun gongRate(phase: Phase): Float = when (phase) {
    Phase.INHALE, Phase.HOLD_IN -> GONG_LOW_RATE
    Phase.EXHALE, Phase.HOLD_OUT -> 1f
}

/** The bell has one pitch wherever it rings, so only the gong varies. */
fun markerRate(tone: MarkerTone, phase: Phase): Float =
    if (tone == MarkerTone.GONG) gongRate(phase) else 1f

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

/** Twin-partial offset: 0.6% apart beats slowly enough to read as shimmer, not as vibrato. */
private const val DETUNE = 1.006

// Warmth knobs for the struck markers. Warmth is mostly the absence of high-frequency
// energy, so every one of these tilts the balance downward.

/**
 * Corner of the spectral tilt, in Hz. Partials are attenuated by absolute frequency rather
 * than by partial number: warmth is about where energy sits in Hz, and tilting by index
 * strips body from a low sound while leaving a bright 1 kHz octave untouched.
 */
private const val WARM_HZ = 700.0

/** How much faster each higher partial dies. Higher makes brightness leave sooner. */
private const val HF_DECAY = 0.45

/** The strike is a lowpassed thump; unfiltered noise here read as a bright tick. */
private const val STRIKE_HZ = 900.0

/** A few ms of rise. An instant attack is heard as a hard edge however dark the tone is. */
private const val ATTACK_S = 0.006

/** Peak every marker is normalised to, leaving headroom over the waves it plays against. */
private const val MARKER_PEAK = 12500f

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
    private var bell: AudioTrack? = null

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
            else -> playBell()
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

    fun release() {
        fades.removeCallbacksAndMessages(null)
        pool.release()
        bell?.release()
        bell = null
        bowl = -1
        loaded.clear()
    }

    /**
     * Renders a struck-metal marker into a static buffer.
     *
     * Higher partials decay faster than low ones, so the wash settles into a hum rather than
     * every partial dying at once. Each partial is doubled by a slightly detuned twin: the
     * two beat against each other, and that shimmer is what separates a gong from a big bell.
     */
    private fun struck(hz: Float): AudioTrack {
        val partials = BELL_PARTIALS
        val baseDecay = 3.2
        val seconds = 1.2

        val n = (SR * seconds).toInt()
        val raw = FloatArray(n)
        val rnd = Random()
        // -12 dB/octave above WARM_HZ, so brightness is shed wherever it actually lives
        val amps = FloatArray(partials.size) {
            val ratio = hz * partials[it] / WARM_HZ
            (1.0 / (1.0 + ratio * ratio)).toFloat()
        }
        val norm = 1.0 / amps.sum()
        // whatever is still ringing when the buffer ends would click, so taper the last tenth
        val taperFrom = (n * 0.9).toInt()

        // the strike gets its own lowpass, and closing a one-pole costs amplitude, so undo
        // that here rather than letting the filter secretly set how hard the hit lands
        val strikeA = 1.0 - exp(-2.0 * PI * STRIKE_HZ / SR)
        val strikeNorm = 1.0 / sqrt(strikeA / (2.0 - strikeA))
        var strikeLp = 0.0

        for (i in 0 until n) {
            val t = i.toDouble() / SR
            var s = 0.0
            for (p in partials.indices) {
                val f = hz * partials[p]
                val env = amps[p] * exp(-baseDecay * (1.0 + p * HF_DECAY) * t)
                s += env * (sin(2 * PI * f * t) + sin(2 * PI * f * DETUNE * t)) * 0.5
            }
            // the strike itself: a soft thump, gone in about 50 ms
            strikeLp += strikeA * ((rnd.nextFloat() * 2 - 1) - strikeLp)
            val strike = exp(-70.0 * t) * strikeLp * strikeNorm * 0.4

            val attack = if (t < ATTACK_S) eased((t / ATTACK_S).toFloat()).toDouble() else 1.0
            val taper = if (i > taperFrom) (n - i).toDouble() / (n - taperFrom) else 1.0
            raw[i] = ((s * norm + strike) * attack * taper).toFloat()
        }

        // every marker lands at the same peak. Left to a fixed scalar, each tone and pitch
        // ends up a different loudness purely from how its partials happen to sum — and
        // normalising also makes clipping impossible rather than merely unlikely.
        var peak = 0f
        for (v in raw) peak = maxOf(peak, abs(v))
        val scale = if (peak > 0f) MARKER_PEAK / peak else 0f
        return staticTrack(ShortArray(n) { (raw[it] * scale).toInt().toShort() })
    }

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
