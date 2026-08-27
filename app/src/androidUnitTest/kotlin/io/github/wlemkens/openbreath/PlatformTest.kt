package io.github.wlemkens.openbreath

import android.app.NotificationManager.INTERRUPTION_FILTER_ALARMS
import android.app.NotificationManager.INTERRUPTION_FILTER_ALL
import android.app.NotificationManager.INTERRUPTION_FILTER_NONE
import android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
import android.app.NotificationManager.INTERRUPTION_FILTER_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformTest {
    @Test
    fun `an unknown filter is restored as all, never written back as unknown`() {
        assertEquals(INTERRUPTION_FILTER_ALL, restorableFilter(INTERRUPTION_FILTER_UNKNOWN))
    }

    @Test
    fun `a real filter is kept, so a phone already in DND stays there afterwards`() {
        assertEquals(INTERRUPTION_FILTER_ALL, restorableFilter(INTERRUPTION_FILTER_ALL))
        assertEquals(INTERRUPTION_FILTER_PRIORITY, restorableFilter(INTERRUPTION_FILTER_PRIORITY))
        assertEquals(INTERRUPTION_FILTER_ALARMS, restorableFilter(INTERRUPTION_FILTER_ALARMS))
    }

    @Test
    fun `a phone already in DND is left alone, so its own rule still ends in the morning`() {
        assertEquals(true, shouldSilence(INTERRUPTION_FILTER_ALL))
        // an evening schedule owns these; taking them over replaces the rule with a manual
        // override that nothing ever lifts
        assertEquals(false, shouldSilence(INTERRUPTION_FILTER_PRIORITY))
        assertEquals(false, shouldSilence(INTERRUPTION_FILTER_ALARMS))
        assertEquals(false, shouldSilence(INTERRUPTION_FILTER_NONE))
        // a filter the system will not name is not a phone we know to be silent
        assertEquals(true, shouldSilence(INTERRUPTION_FILTER_UNKNOWN))
    }

    private fun at(phase: Phase, progress: Float) = PhaseState(phase, progress, 0, 5000)

    @Test
    fun `a torch without brightness levels is lit for the inhale and dark for the exhale`() {
        // the whole phase, not half of it: an on-off torch that switched mid-inhale would tell
        // you the wrong thing to do for half of every breath
        listOf(0f, 0.5f, 0.99f).forEach { p ->
            assertEquals(1, torchLevel(at(Phase.INHALE, p), 1))
            assertEquals(1, torchLevel(at(Phase.HOLD_IN, p), 1))
            assertEquals(0, torchLevel(at(Phase.EXHALE, p), 1))
            assertEquals(0, torchLevel(at(Phase.HOLD_OUT, p), 1))
        }
    }

    @Test
    fun `a torch with brightness levels ramps over the whole range`() {
        assertEquals(0, torchLevel(at(Phase.INHALE, 0f), 5))
        assertEquals(3, torchLevel(at(Phase.INHALE, 0.5f), 5))
        assertEquals(5, torchLevel(at(Phase.HOLD_IN, 0f), 5))
        assertEquals(0, torchLevel(at(Phase.HOLD_OUT, 0.5f), 5))
    }

    @Test
    fun `a finished exhale puts the torch out, not merely down to its dimmest`() {
        assertEquals(0, torchLevel(at(Phase.EXHALE, 1f), 5))
        // and the tail of the exhale is dark before it, rather than clinging to level 1
        assertEquals(0, torchLevel(at(Phase.EXHALE, 0.97f), 5))
        assertEquals(0, torchLevel(at(Phase.INHALE, 0.03f), 5))
    }

    @Test
    fun `brightness never leaves the range the camera accepts`() {
        var prev = 0
        (0..100).forEach { i ->
            val level = torchLevel(at(Phase.INHALE, i / 100f), 8)
            assertTrue("$level out of range at $i", level in 0..8)
            assertTrue("dimmed while breathing in at $i", level >= prev)
            prev = level
        }
        assertEquals(8, prev)
    }
}
