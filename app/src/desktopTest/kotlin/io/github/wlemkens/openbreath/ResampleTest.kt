package io.github.wlemkens.openbreath

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The one piece of the desktop's audio that can be checked without a speaker.
 *
 * [resample] is where a bowl's pitch comes from on this platform, and it is doing two jobs at once
 * — the recording's rate to the line's rate, and the pitch the phase asks for. Getting either
 * backwards gives a bowl that is wrong by a fifth and still sounds like a bowl, which is exactly
 * the kind of mistake an ear accepts and arithmetic does not.
 */
class ResampleTest {

    /** A ramp, so the value at any position says which source sample it came from. */
    private fun ramp(n: Int) = FloatArray(n) { it.toFloat() }

    @Test
    fun `the same rate and no pitch shift changes nothing`() {
        val source = ramp(100)
        val out = resample(source, from = 44100, to = 44100, pitch = 1f)
        // one short: the last output sample needs a source sample after it to interpolate towards
        assertEquals(99, out.size)
        for (i in out.indices) assertTrue(abs(out[i] - source[i]) < 1e-4f, "sample $i moved")
    }

    @Test
    fun `a lower pitch makes the sound longer`() {
        // 0.6 is gongRate for an exhale — the bowl played slower, which is what drops its pitch
        val out = resample(ramp(600), from = 44100, to = 44100, pitch = 0.6f)
        assertEquals(998, out.size)
        // and it is still the same sweep, just stretched: halfway through the output is halfway
        // through the input
        assertTrue(abs(out[out.size / 2] - 299.7f) < 1f, "the stretch is not linear: ${out[out.size / 2]}")
    }

    @Test
    fun `a recording at one rate played on a line at another keeps its duration`() {
        // a 44.1k bowl on a 48k line has to come out ~8.8% longer in samples to last as long
        val out = resample(ramp(44_100), from = 44100, to = 48000, pitch = 1f)
        assertTrue(out.size in 47_900..48_000, "expected about 48000 samples, got ${out.size}")
    }

    @Test
    fun `the two jobs compose`() {
        // both at once: onto a faster line and pitched down. 44100/48000 * 0.6 = 0.55125 per step
        val out = resample(ramp(10_000), from = 44100, to = 48000, pitch = 0.6f)
        assertEquals((9_999 / 0.55125).toInt(), out.size)
    }

    @Test
    fun `an empty recording is empty rather than a crash`() {
        // what a bowl that failed to decode looks like by the time it reaches here
        assertEquals(0, resample(FloatArray(0), from = 44100, to = 44100, pitch = 1f).size)
    }

    @Test
    fun `interpolation lands between the two neighbours`() {
        // a step of 1.5 puts every other output sample exactly halfway between two inputs
        val out = resample(ramp(10), from = 44100, to = 44100, pitch = 1.5f)
        assertTrue(abs(out[1] - 1.5f) < 1e-4f, "expected 1.5, got ${out[1]}")
        assertTrue(abs(out[3] - 4.5f) < 1e-4f, "expected 4.5, got ${out[3]}")
    }
}
