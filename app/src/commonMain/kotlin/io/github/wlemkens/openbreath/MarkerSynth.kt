package io.github.wlemkens.openbreath

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The two markers this app synthesises rather than plays back: the bell and the metronome tick.
 *
 * Both render into a buffer once and are then struck from it, so the arithmetic never happens on
 * a phase boundary where it would be the audible thing about the boundary. Both live here rather
 * than beside an AudioTrack for the same reason [WaveDsp] does — the sound is the app's and the
 * playing of it is the platform's, and a constant that drifted between two copies would be a
 * difference nobody could see.
 *
 * The bowl and the end-of-session bowl are recordings and are not here; they are files.
 */

/** The bell's pitch, wherever it rings. */
const val BELL_HZ = 528f

/** Near-harmonic: a fundamental and its octave. */
private val BELL_PARTIALS = floatArrayOf(1f, 2f)

/** Base pitch of the metronome tick, before [tickRate] moves it. */
const val TICK_HZ = 900f

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

/**
 * Peak every marker is normalised to, leaving headroom over the waves it plays against.
 *
 * Expressed against the 16-bit scale these were tuned in and then normalised, so a float sink and
 * a PCM one land at the same loudness rather than one of them being quietly half.
 */
private const val MARKER_PEAK = 12500f / 32768f

/**
 * A struck-metal marker, in [-1, 1].
 *
 * Higher partials decay faster than low ones, so the wash settles into a hum rather than
 * every partial dying at once. Each partial is doubled by a slightly detuned twin: the
 * two beat against each other, and that shimmer is what separates a gong from a big bell.
 */
fun struckMarker(hz: Float, sampleRate: Int): FloatArray {
    val partials = BELL_PARTIALS
    val baseDecay = 3.2
    val seconds = 1.2

    val n = (sampleRate * seconds).toInt()
    val raw = FloatArray(n)
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
    val strikeA = 1.0 - exp(-2.0 * PI * STRIKE_HZ / sampleRate)
    val strikeNorm = 1.0 / sqrt(strikeA / (2.0 - strikeA))
    var strikeLp = 0.0

    for (i in 0 until n) {
        val t = i.toDouble() / sampleRate
        var s = 0.0
        for (p in partials.indices) {
            val f = hz * partials[p]
            val env = amps[p] * exp(-baseDecay * (1.0 + p * HF_DECAY) * t)
            s += env * (sin(2 * PI * f * t) + sin(2 * PI * f * DETUNE * t)) * 0.5
        }
        // the strike itself: a soft thump, gone in about 50 ms
        strikeLp += strikeA * ((Random.nextFloat() * 2 - 1) - strikeLp)
        val strike = exp(-70.0 * t) * strikeLp * strikeNorm * 0.4

        val attack = if (t < ATTACK_S) eased((t / ATTACK_S).toFloat()).toDouble() else 1.0
        val taper = if (i > taperFrom) (n - i).toDouble() / (n - taperFrom) else 1.0
        raw[i] = ((s * norm + strike) * attack * taper).toFloat()
    }

    return raw.normalisedTo(MARKER_PEAK)
}

/**
 * A metronome click: a short sine gone in about 40 ms, with a filtered noise transient on
 * top. The noise is what the ear hears as the tick; the sine is what gives it a pitch to
 * tell one boundary from another. No detune and no partials — shimmer is what makes a bell
 * sound like a bell, and a metronome is meant to be a hard, plain edge.
 */
fun tickMarker(hz: Float, sampleRate: Int): FloatArray {
    val seconds = 0.06
    val n = (sampleRate * seconds).toInt()
    val raw = FloatArray(n)
    // the same one-pole as the struck markers, and the same correction for what it costs
    val clickA = 1.0 - exp(-2.0 * PI * STRIKE_HZ / sampleRate)
    val clickNorm = 1.0 / sqrt(clickA / (2.0 - clickA))
    var clickLp = 0.0

    for (i in 0 until n) {
        val t = i.toDouble() / sampleRate
        clickLp += clickA * ((Random.nextFloat() * 2 - 1) - clickLp)
        // starts at zero either way: a sine from phase 0 and noise through a closed filter
        val body = sin(2 * PI * hz * t) * exp(-90.0 * t)
        val click = clickLp * clickNorm * exp(-260.0 * t)
        // whatever is left at the end of the buffer would click on its own, so taper out
        val taper = 1.0 - (i.toDouble() / n).pow(6)
        raw[i] = ((body * 0.7 + click * 0.6) * taper).toFloat()
    }

    return raw.normalisedTo(MARKER_PEAK)
}

/**
 * Every marker lands at the same peak. Left to a fixed scalar, each tone and pitch ends up a
 * different loudness purely from how its partials happen to sum — and normalising also makes
 * clipping impossible rather than merely unlikely.
 */
private fun FloatArray.normalisedTo(peak: Float): FloatArray {
    var loudest = 0f
    for (v in this) loudest = maxOf(loudest, abs(v))
    val scale = if (loudest > 0f) peak / loudest else 0f
    for (i in indices) this[i] = this[i] * scale
    return this
}
