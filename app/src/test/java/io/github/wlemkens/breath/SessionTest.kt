package io.github.wlemkens.breath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SessionTest {
    // 4s in, 2s hold, 6s out, 1s hold = 13s cycle
    private val t = Timing(inhaleMs = 4000, holdInMs = 2000, exhaleMs = 6000, holdOutMs = 1000)

    @Test
    fun `phases follow in order with correct progress`() {
        assertEquals(Phase.INHALE, phaseAt(0, t).phase)
        assertEquals(0f, phaseAt(0, t).progress, 1e-4f)
        assertEquals(0.5f, phaseAt(2000, t).progress, 1e-4f)

        assertEquals(Phase.HOLD_IN, phaseAt(4000, t).phase)
        assertEquals(Phase.EXHALE, phaseAt(6000, t).phase)
        assertEquals(0.5f, phaseAt(9000, t).progress, 1e-4f)
        assertEquals(Phase.HOLD_OUT, phaseAt(12000, t).phase)
    }

    @Test
    fun `zero length holds are skipped, not divided by`() {
        val coherence = Timing(inhaleMs = 5500, holdInMs = 0, exhaleMs = 5500, holdOutMs = 0)
        // the instant the inhale ends we must already be exhaling
        assertEquals(Phase.EXHALE, phaseAt(5500, coherence).phase)
        assertEquals(0f, phaseAt(5500, coherence).progress, 1e-4f)
        // and the cycle must wrap cleanly rather than land in a 0-length hold
        assertEquals(Phase.INHALE, phaseAt(11000, coherence).phase)
        assertEquals(1, phaseAt(11000, coherence).cycle)
    }

    @Test
    fun `cycles are counted from zero and wrap on the boundary`() {
        assertEquals(0, phaseAt(12999, t).cycle)
        assertEquals(1, phaseAt(13000, t).cycle)
        assertEquals(Phase.INHALE, phaseAt(13000, t).phase)
        assertEquals(3, phaseAt(13000 * 3L + 500, t).cycle)
    }

    @Test
    fun `a duration limit rounds up to whole cycles`() {
        // 60s of a 13s cycle -> 5 cycles = 65s, never a session cut off mid-breath
        assertEquals(65_000L, Limit.Duration(60_000).totalMs(t))
        assertEquals(13_000L, Limit.Duration(1).totalMs(t))
        assertEquals(26_000L, Limit.Duration(26_000).totalMs(t))
    }

    @Test
    fun `a cycle limit is exactly that many cycles`() {
        assertEquals(65_000L, Limit.Cycles(5).totalMs(t))
    }

    @Test
    fun `openness runs 0 to 1 and back, flat through the holds`() {
        assertEquals(0f, phaseAt(0, t).openness, 1e-4f)
        assertEquals(1f, phaseAt(4000, t).openness, 1e-4f) // hold_in
        assertEquals(1f, phaseAt(5999, t).openness, 1e-4f) // still hold_in
        assertEquals(1f, phaseAt(6000, t).openness, 1e-4f) // exhale, progress 0
        assertEquals(0f, phaseAt(12000, t).openness, 1e-4f) // hold_out
        // monotonic rise through the inhale
        assertEquals(true, phaseAt(1000, t).openness < phaseAt(3000, t).openness)
    }

    @Test
    fun `every phase fades in and out so its boundary is audible`() {
        // at the bottom of the breath the dip reaches true silence
        assertEquals(0f, phaseAt(0, t).edge, 1e-3f) // inhale begins
        assertEquals(0f, phaseAt(12000, t).edge, 1e-3f) // hold_out begins

        // at the top it ducks hard but never to silence: a hole at peak volume reads as harsh
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(4000, t).edge, 0.02f) // hold_in begins
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(6000, t).edge, 0.02f) // exhale begins
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(3999, t).edge, 0.02f) // 1 ms before inhale ends
        // ...and that is still a big enough duck to hear against full level
        assertEquals(true, phaseAt(6000, t).edge < 0.5f * phaseAt(9000, t).edge)

        // full level through the middle of every phase
        assertEquals(1f, phaseAt(2000, t).edge, 1e-3f) // inhale
        assertEquals(1f, phaseAt(9000, t).edge, 1e-3f) // exhale

        // eased, not linear: a quarter into the ramp is well below a quarter of the level,
        // which is what stops the fade sounding abrupt
        assertEquals(0.146f, phaseAt(EDGE_MS / 4L, t).edge, 1e-2f)
        assertEquals(0.5f, phaseAt(EDGE_MS / 2L, t).edge, 1e-2f)
    }

    @Test
    fun `the fade is longer at the loud top of the breath than the quiet bottom`() {
        // 400 ms in from the quiet bottom of the breath is already well up the ramp, while
        // 400 ms out from the loud peak has barely begun to drop: the loud boundary gets the
        // longer, gentler fade, which is what stops it sounding harder than the quiet one
        assertEquals(true, phaseAt(400, t).edge > phaseAt(4000 - 400, t).edge)

        // same comparison across the two boundaries themselves: leaving the peak into the
        // exhale fades in more slowly than leaving the trough into the next inhale
        val fromPeak = phaseAt(6000 + 400, t).edge // exhale begins at full openness
        val fromTrough = phaseAt(13000 + 400, t).edge // next inhale begins at zero openness
        assertEquals(true, fromPeak < fromTrough)
    }

    @Test
    fun `a short phase is not swallowed by its own fades`() {
        // a 600 ms hold: ramp capped at 200 ms, so it still reaches full level in the middle
        val short = Timing(inhaleMs = 4000, holdInMs = 600, exhaleMs = 4000, holdOutMs = 0)
        assertEquals(1f, phaseAt(4000 + 200, short).edge, 1e-3f)
        // a hold sits at the top of the breath, so its dip stops at the floor
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(4000, short).edge, 0.02f)
        // and a phase far shorter than the ramp still peaks rather than staying silent
        val tiny = Timing(inhaleMs = 4000, holdInMs = 300, exhaleMs = 4000, holdOutMs = 0)
        assertEquals(1f, phaseAt(4000 + 150, tiny).edge, 1e-3f)
    }

    @Test
    fun `a zero length phase still ends, so a marker on it can ring`() {
        val coherence = Timing(inhaleMs = 5500, holdInMs = 0, exhaleMs = 5500, holdOutMs = 0)
        // the instant the inhale ends is also the instant the skipped hold ends
        assertEquals(
            listOf(Phase.INHALE, Phase.HOLD_IN),
            phasesEndingBetween(Phase.INHALE, Phase.EXHALE, coherence),
        )
        assertEquals(
            listOf(Phase.EXHALE, Phase.HOLD_OUT),
            phasesEndingBetween(Phase.EXHALE, Phase.INHALE, coherence),
        )
    }

    @Test
    fun `a phase with real length ends on its own`() {
        val box = Timing(4000, 4000, 4000, 4000)
        assertEquals(listOf(Phase.INHALE), phasesEndingBetween(Phase.INHALE, Phase.HOLD_IN, box))
        assertEquals(listOf(Phase.HOLD_OUT), phasesEndingBetween(Phase.HOLD_OUT, Phase.INHALE, box))
        // an in-between phase that has length is not swept up by its neighbour
        assertEquals(listOf(Phase.HOLD_IN), phasesEndingBetween(Phase.HOLD_IN, Phase.EXHALE, box))
    }

    @Test
    fun `a cycle with one non-zero phase ends every phase at the wrap`() {
        val onlyInhale = Timing(inhaleMs = 5000, holdInMs = 0, exhaleMs = 0, holdOutMs = 0)
        // the phase never changes, so without this every marker in the preset would be mute
        assertEquals(
            Phase.entries.toList(),
            phasesEndingBetween(Phase.INHALE, Phase.INHALE, onlyInhale),
        )
    }

    @Test
    fun `the gong is low at the top of the breath and higher at the bottom`() {
        // a slower playback rate is a lower pitch: the end of the in-hold leads into an
        // exhale (downward), the end of the out-hold into an inhale (upward)
        assertEquals(true, gongRate(Phase.HOLD_IN) < gongRate(Phase.HOLD_OUT))
        // the bowl is used as recorded for the high tone, never sped up
        assertEquals(1f, gongRate(Phase.HOLD_OUT), 1e-3f)
        // and SoundPool only resamples within 0.5x..2x
        assertEquals(true, gongRate(Phase.HOLD_IN) >= 0.5f)

        // the inhale shares the in-hold's pitch, so a zero-length hold between them is one sound
        assertEquals(gongRate(Phase.INHALE), gongRate(Phase.HOLD_IN), 1e-3f)
        assertEquals(gongRate(Phase.EXHALE), gongRate(Phase.HOLD_OUT), 1e-3f)

        // the bell rings identically wherever it is used
        assertEquals(
            markerRate(MarkerTone.BELL, Phase.HOLD_IN),
            markerRate(MarkerTone.BELL, Phase.HOLD_OUT),
            1e-3f,
        )
    }

    @Test
    fun `an all zero timing is rejected rather than dividing by zero`() {
        assertThrows(IllegalArgumentException::class.java) { Timing(0, 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { Timing(inhaleMs = -1) }
    }

    @Test
    fun `a nonsense stored config is repaired instead of crashing`() {
        // an out-of-range index, e.g. a preset deleted by an older build: clamped into
        // range so `active` is always safe to read unguarded
        val clamped = Config(activeIndex = 99).sane()
        assertEquals(clamped.presets.size - 1, clamped.activeIndex)
        assertEquals(clamped.presets.last(), clamped.active)
        // no presets at all
        val empty = Config(presets = emptyList(), activeIndex = 3).sane()
        assertEquals(true, empty.presets.isNotEmpty())
        assertEquals(0, empty.activeIndex)
        // and a preset whose phases sum to zero still yields a usable timing
        assertEquals(Timing().cycleMs, Preset("bad", 0, 0, 0, 0).timing.cycleMs)
    }
}
