package io.github.wlemkens.openbreath

import kotlin.concurrent.Volatile
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Waves coming ashore: white noise squeezed into a band by two one-pole filters, the upper
 * edge riding the breath.
 *
 * Noise has no pitch to shift, so "the pitch goes up during the breath in" is a filter
 * cutoff sweep — that is what actually reads as a wave rising and falling. Every constant
 * below is a tuning knob; they were picked by ear and are meant to be adjusted.
 *
 * This is the whole of the sound and none of the playing of it. It fills a buffer and knows
 * nothing about AudioTrack or AVAudioEngine, which is what lets one tuning serve both platforms:
 * an ear-picked constant that drifted between Android and iOS would be a bug nobody could see in
 * a diff.
 *
 * **The sample rate is a parameter and has to be.** It was a constant 44100 while there was only
 * AudioTrack to satisfy, and it is baked into every filter coefficient and oscillator increment
 * here. iOS hands back whatever its output node is running at — 48000 as often as not — and a
 * synth built for 44100 played at 48000 is the same sound roughly a semitone sharp and with its
 * filter corners moved to match.
 */
class WaveDsp(private val sampleRate: Int) {

    /** 0 = fully exhaled, 1 = fully inhaled. Written from the UI thread, read by the audio one. */
    @Volatile
    var openness: Float = 0f

    /**
     * Per-phase level of each voice: the sound mode's on/off multiplied by [PhaseState.edge],
     * so it dips at every phase boundary. Written every frame; smoothed only enough to take
     * the stair-steps off a 60 Hz update, since the ramp it follows is already gradual.
     */
    @Volatile
    var wavesGain: Float = 0f

    @Volatile
    var soundwaveGain: Float = 0f

    /** Set false to fade out. [finished] goes true once the fade has actually reached silence. */
    @Volatile
    var running: Boolean = false

    private var lp = 0f // upper band edge, swept by the breath
    private var hp = 0f // lower band edge, fixed — kills the rumble
    private var cutoff = LO_HZ
    private var master = 0f
    private var gate = 0f
    private var lfo = 0.0

    // the soundwave: one partial per overtone, each with a phase and a slow shimmer of its own
    private var hum = 0f
    private val partial = DoubleArray(SOUNDWAVE_PARTIALS)
    private val shimmer = FloatArray(SOUNDWAVE_PARTIALS) { 1f }
    private var shimmerPhase = 0.0

    private val hpA = 1f - exp(-2.0 * PI * 90.0 / sampleRate).toFloat()

    /**
     * Keep rendering until this is true: a stopped synth still has its fade-out to play, and
     * cutting the sink the instant [running] goes false is a click at the end of every session.
     */
    val finished: Boolean get() = !running && master <= 1e-3f

    /**
     * The gate starts where the level already is rather than gliding up from zero, so the first
     * phase does not open with a swell that belongs to no breath.
     */
    fun prime() {
        gate = wavesGain
    }

    /**
     * One block, in [-1, 1]. Any length: the shimmer advances by the block it was given, so a
     * sink is free to use whatever buffer its platform prefers.
     */
    fun render(out: FloatArray) {
        val target = if (running) 1f else 0f
        val fade = if (running) FADE_IN else FADE_OUT
        // each partial drifts in loudness at its own slow rate, so the note is never quite
        // the same twice. Once a buffer is plenty for something this slow, and a sine per
        // partial per sample for it would cost as much as the soundwave itself
        shimmerPhase += out.size.toDouble() / sampleRate
        for (n in shimmer.indices) {
            shimmer[n] = 1f + SOUNDWAVE_SHIMMER *
                sin(2.0 * PI * (SOUNDWAVE_SHIMMER_HZ * (n + 1) * shimmerPhase + n * 0.37)).toFloat()
        }
        for (i in out.indices) {
            // per-sample glides: openness only updates at frame rate, and stepping the
            // cutoff once per buffer zippers audibly
            master += (target - master) * fade
            gate += (wavesGain - gate) * GATE_GLIDE
            hum += (soundwaveGain - hum) * GATE_GLIDE
            cutoff += (LO_HZ + (HI_HZ - LO_HZ) * openness - cutoff) * GLIDE

            val a = 1f - exp(-2.0 * PI * cutoff / sampleRate).toFloat()
            lp += a * ((Random.nextFloat() * 2f - 1f) - lp)
            hp += hpA * (lp - hp)
            // a one-pole loses amplitude as it closes; undo that so loudness is set
            // only by `gain` below and not smuggled in by the sweep
            val band = (lp - hp) / sqrt(a / (2f - a))

            lfo += TEXTURE_HZ / sampleRate
            val texture = 1f + 0.22f * sin(2.0 * PI * lfo).toFloat()
            val gain = (0.22f + 0.78f * openness) * texture * master * gate

            var sample = band * gain * 9000f

            // one note, held. The breath does not move its pitch — moving pitch is what
            // made every earlier attempt sound like a theremin — it decides how many
            // overtones are sounding, so they arrive on the inhale and dissolve on the exhale
            if (hum > 1e-4f) {
                var voice = 0.0
                for (n in 0 until SOUNDWAVE_PARTIALS) {
                    partial[n] = (partial[n] + SOUNDWAVE_HZ * (n + 1) / sampleRate) % 1.0
                    // the lowest few are the note itself and never leave; the rest are the
                    // breath's to bring in, the last only at the very top
                    val above = n - SOUNDWAVE_ALWAYS
                    val arrives =
                        above.toFloat() / (SOUNDWAVE_PARTIALS - SOUNDWAVE_ALWAYS) * (1f - SOUNDWAVE_FADE)
                    val open =
                        if (above < 0) 1f
                        else ((openness - arrives) / SOUNDWAVE_FADE).coerceIn(0f, 1f)
                    if (open <= 0f) break // the rest are quieter still, and not yet in
                    voice += sin(2.0 * PI * partial[n]) * SOUNDWAVE_ROLLOFF[n] * eased(open) * shimmer[n]
                }
                // square-rooted, not linear: openness is a raised cosine and so dawdles at
                // the bottom, which left the end of every exhale sounding like a gap
                sample += voice.toFloat() *
                    (SOUNDWAVE_FLOOR + (1f - SOUNDWAVE_FLOOR) * sqrt(openness)) *
                    master * hum * SOUNDWAVE_LEVEL
            }

            // the arithmetic above is in the 16-bit scale it was tuned in; dividing by a power of
            // two is exact in binary floating point, so a sink that multiplies it straight back
            // gets the same samples this produced when it wrote shorts directly
            out[i] = sample * (1f / 32768f)
        }
    }

    internal companion object {
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

        // The soundwave, built up rather than filtered down. A sawtooth through a sweeping filter
        // was the obvious way and sounded synthetic for two reasons: a naive saw aliases, and
        // a filter passing over harmonics is a gesture the ear knows from synthesizers. Summed
        // partials cannot alias at all, and an overtone that simply fades in has no edge to it.
        // E3. An octave-and-a-fifth above where this started: 110 Hz sat under what a phone
        // speaker can reproduce, so the bottom of every exhale was heard as a gap.
        const val SOUNDWAVE_HZ = 165f
        const val SOUNDWAVE_PARTIALS = 9 // to 990 Hz: past that it starts to ring like metal

        /**
         * How many partials sound whatever the breath is doing. Three, not one: a phone speaker
         * puts almost nothing below 300 Hz into the room, so a lone 110 Hz fundamental at the
         * bottom of the exhale is heard as silence however loud it measures.
         */
        const val SOUNDWAVE_ALWAYS = 3
        const val SOUNDWAVE_FADE = 0.3f // how much of the breath a partial takes to arrive
        const val SOUNDWAVE_FLOOR = 0.45f // quietest at the bottom of the breath, never a gap
        const val SOUNDWAVE_LEVEL = 5200f // softer than the waves, which are meant to be the loud one
        const val SOUNDWAVE_SHIMMER = 0.11f // how far a partial drifts in loudness
        const val SOUNDWAVE_SHIMMER_HZ = 0.043 // and how slowly, times the partial number

        /**
         * 1/n^1.9: far steeper than a sawtooth's 1/n. This exponent is what separates soft from
         * metallic — the upper partials are what ring, so this is the knob to turn first.
         */
        val SOUNDWAVE_ROLLOFF = FloatArray(SOUNDWAVE_PARTIALS) { (1.0 / (it + 1.0).pow(1.9)).toFloat() }
            .let { a -> val sum = a.sum(); FloatArray(a.size) { a[it] / sum } }
    }
}
