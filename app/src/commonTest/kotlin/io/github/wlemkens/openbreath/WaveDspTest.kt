package io.github.wlemkens.openbreath

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The soundscape's arithmetic, checked on both platforms because both now run this exact code.
 *
 * None of this can say whether it sounds right — that is what ears are for — but all of it can
 * say whether it is still a sound at all. A synth that returns NaN, clips, or never finishes
 * fading is broken in a way no amount of listening on one platform would reveal about the other.
 */
class WaveDspTest {

    private fun running(sampleRate: Int = 44100) = WaveDsp(sampleRate).apply {
        wavesGain = 1f
        soundwaveGain = 1f
        openness = 0.5f
        running = true
        prime()
    }

    @Test
    fun `a stopped synth actually reaches silence`() {
        val dsp = running()
        val buf = FloatArray(1024)
        repeat(100) { dsp.render(buf) }
        assertFalse(dsp.finished, "still playing, so it cannot be finished")

        dsp.running = false
        // the sink loops until this goes true, so a fade that never lands is a thread that never
        // exits and an AudioTrack that is never released
        var blocks = 0
        while (!dsp.finished && blocks < 5000) {
            dsp.render(buf)
            blocks++
        }
        assertTrue(dsp.finished, "the fade-out never reached silence after $blocks blocks")
    }

    @Test
    fun `nothing it produces is out of range or not a number`() {
        // both rates, because the sample rate is a parameter now and the filter coefficients are
        // derived from it: a value that only stays sane at 44100 is a value iOS would break on
        for (rate in listOf(44100, 48000)) {
            val dsp = running(rate)
            val buf = FloatArray(1024)
            var peak = 0f
            repeat(300) { block ->
                // sweep the breath rather than hold it, so the cutoff glide and the arriving
                // partials are both exercised
                dsp.openness = if (block % 60 < 30) block % 30 / 30f else 1f - (block % 30) / 30f
                dsp.render(buf)
                for (s in buf) {
                    assertTrue(!s.isNaN(), "NaN at $rate Hz")
                    assertTrue(s.isFinite(), "infinity at $rate Hz")
                    peak = maxOf(peak, abs(s))
                }
            }
            assertTrue(peak <= 1f, "clipped at $rate Hz: peak was $peak")
            assertTrue(peak > 0.01f, "silent at $rate Hz, so nothing was really tested")
        }
    }

    @Test
    fun `both voices off is silence rather than a bed of noise`() {
        val dsp = WaveDsp(44100).apply {
            wavesGain = 0f
            soundwaveGain = 0f
            openness = 1f
            running = true
            prime()
        }
        val buf = FloatArray(1024)
        // past the gate glide, which starts where prime() left it and has nowhere to go
        repeat(50) { dsp.render(buf) }

        var peak = 0f
        repeat(20) {
            dsp.render(buf)
            for (s in buf) peak = maxOf(peak, abs(s))
        }
        // a phase whose sound mode is off must be off: any bed under it would be audible in the
        // holds, which are the quietest part of a sitting
        assertTrue(peak < 1e-4f, "a muted synth was still making $peak")
    }
}
