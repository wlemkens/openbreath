package io.github.wlemkens.openbreath

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
    fun `zero length holds are skipped rather than divided by`() {
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
    fun `openness runs 0 to 1 and back and is flat through the holds`() {
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
        // never to silence, at either end of the breath: a hole punched in the soundscape is
        // what reads as a gap rather than a turn
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(0, t).edge, 0.02f) // inhale begins
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(12000, t).edge, 0.02f) // hold_out begins
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(4000, t).edge, 0.02f) // hold_in begins
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(6000, t).edge, 0.02f) // exhale begins
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(3999, t).edge, 0.02f) // 1 ms before inhale ends
        // ...and that is still a big enough duck to hear against full level
        assertEquals(true, phaseAt(6000, t).edge < 0.5f * phaseAt(9000, t).edge)

        // full level through the middle of every phase
        assertEquals(1f, phaseAt(2000, t).edge, 1e-3f) // inhale
        assertEquals(1f, phaseAt(9000, t).edge, 1e-3f) // exhale

        // eased, not linear: a quarter into the ramp is well below a quarter of the way up,
        // which is what stops the fade sounding abrupt
        val quarter = phaseAt(EDGE_RAMP_MS / 4L, t).edge
        assertEquals(true, quarter < EDGE_FLOOR_AT_PEAK + 0.25f * (1f - EDGE_FLOOR_AT_PEAK))
        // halfway up the ramp is halfway up the range, the one point the cosine is symmetric on
        assertEquals(0.65f, phaseAt(EDGE_RAMP_MS / 2L, t).edge, 1e-2f)
    }

    @Test
    fun `both turns of the breath fade alike`() {
        // the same distance either side of a boundary gives the same level, wherever in the
        // breath it falls. The exhale used to fade to silence into the next inhale while the
        // inhale only ducked into the exhale, and that difference was audible as a gap
        assertEquals(phaseAt(4000 - 400, t).edge, phaseAt(400, t).edge, 1e-3f)

        val intoExhale = phaseAt(6000 + 400, t).edge // leaving the peak
        val intoInhale = phaseAt(13000 + 400, t).edge // leaving the trough, a cycle later
        assertEquals(intoExhale, intoInhale, 1e-3f)
    }

    @Test
    fun `a marker that varies by phase still sounds once at a skipped hold`() {
        // a zero-length hold begins on the same instant as the phase after it. Both are asked
        // for their sound, and the two answers must be one sound, not two struck together.
        // A hold pairs with what it runs into, because that is what opens alongside it
        for (tone in MarkerTone.entries) {
            // kotlin.test takes the message last, where JUnit took it first
            assertEquals(
                markerRate(tone, Phase.EXHALE),
                markerRate(tone, Phase.HOLD_IN),
                1e-4f,
                "$tone splits the top of the breath",
            )
            assertEquals(
                markerRate(tone, Phase.INHALE),
                markerRate(tone, Phase.HOLD_OUT),
                1e-4f,
                "$tone splits the bottom of the breath",
            )
        }
        // and the two turns of the breath are told apart, which is the point of varying at all
        assertEquals(true, markerRate(MarkerTone.TICK, Phase.INHALE) != markerRate(MarkerTone.TICK, Phase.EXHALE))
        assertEquals(true, markerRate(MarkerTone.GONG, Phase.INHALE) != markerRate(MarkerTone.GONG, Phase.EXHALE))
    }

    @Test
    fun `a short phase is not swallowed by its own fades`() {
        // a 600 ms hold: ramp capped at 200 ms, so it still reaches full level in the middle
        val short = Timing(inhaleMs = 4000, holdInMs = 600, exhaleMs = 4000, holdOutMs = 0)
        assertEquals(1f, phaseAt(4000 + 200, short).edge, 1e-3f)
        assertEquals(EDGE_FLOOR_AT_PEAK, phaseAt(4000, short).edge, 0.02f)
        // and a phase far shorter than the ramp still peaks rather than staying at the floor
        val tiny = Timing(inhaleMs = 4000, holdInMs = 300, exhaleMs = 4000, holdOutMs = 0)
        assertEquals(1f, phaseAt(4000 + 150, tiny).edge, 1e-3f)
    }

    @Test
    fun `a zero length phase still begins so a marker on it can ring`() {
        val coherence = Timing(inhaleMs = 5500, holdInMs = 0, exhaleMs = 5500, holdOutMs = 0)
        // the instant the exhale begins is also the instant the skipped hold begins
        assertEquals(
            listOf(Phase.HOLD_IN, Phase.EXHALE),
            phasesStartingBetween(Phase.INHALE, Phase.EXHALE, coherence),
        )
        assertEquals(
            listOf(Phase.HOLD_OUT, Phase.INHALE),
            phasesStartingBetween(Phase.EXHALE, Phase.INHALE, coherence),
        )
    }

    @Test
    fun `a phase with real length begins on its own`() {
        val box = Timing(4000, 4000, 4000, 4000)
        assertEquals(listOf(Phase.HOLD_IN), phasesStartingBetween(Phase.INHALE, Phase.HOLD_IN, box))
        assertEquals(listOf(Phase.INHALE), phasesStartingBetween(Phase.HOLD_OUT, Phase.INHALE, box))
        // an in-between phase that has length is not swept up by its neighbour
        assertEquals(listOf(Phase.EXHALE), phasesStartingBetween(Phase.HOLD_IN, Phase.EXHALE, box))
    }

    @Test
    fun `a cycle with one non-zero phase begins every phase at the wrap`() {
        val onlyInhale = Timing(inhaleMs = 5000, holdInMs = 0, exhaleMs = 0, holdOutMs = 0)
        // the phase never changes, so without this every marker in the preset would be mute.
        // In the order they arrive: the three skipped ones, then the inhale coming round again
        assertEquals(
            listOf(Phase.HOLD_IN, Phase.EXHALE, Phase.HOLD_OUT, Phase.INHALE),
            phasesStartingBetween(Phase.INHALE, Phase.INHALE, onlyInhale),
        )
    }

    @Test
    fun `the gong is low at the top of the breath and higher at the bottom`() {
        // a slower playback rate is a lower pitch, and a marker rings as its phase opens: an
        // exhale opens downward, an inhale upward. A hold takes the pitch of what follows it
        assertEquals(true, gongRate(Phase.HOLD_IN) < gongRate(Phase.HOLD_OUT))
        // the bowl is used as recorded for the high tone, never sped up
        assertEquals(1f, gongRate(Phase.HOLD_OUT), 1e-3f)
        // and SoundPool only resamples within 0.5x..2x
        assertEquals(true, gongRate(Phase.HOLD_IN) >= 0.5f)

        // a hold shares the pitch of the phase it runs into, so when it is zero-length and the
        // two open on the same instant they collapse to one strike
        assertEquals(gongRate(Phase.EXHALE), gongRate(Phase.HOLD_IN), 1e-3f)
        assertEquals(gongRate(Phase.INHALE), gongRate(Phase.HOLD_OUT), 1e-3f)

        // the bell rings identically wherever it is used
        assertEquals(
            markerRate(MarkerTone.BELL, Phase.HOLD_IN),
            markerRate(MarkerTone.BELL, Phase.HOLD_OUT),
            1e-3f,
        )
    }

    @Test
    fun `an all zero timing is rejected rather than dividing by zero`() {
        assertFailsWith<IllegalArgumentException> { Timing(0, 0, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Timing(inhaleMs = -1) }
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
        // the end-of-session bowl is on unless turned off; a silent ending leaves you
        // sitting there not knowing whether it finished
        assertEquals(true, Config().endSound)
    }

    @Test
    fun `a logged sitting keeps the timing it was breathed at and counts whole breaths only`() {
        val preset = Preset("Coherence 5.5", 5500, 0, 5500, 0)
        // 84s of an 11s breath: seven finished, and the eighth is in the duration but uncounted
        val entry = entryFor(1_000L, 84_000L, preset)
        assertEquals(7, entry.cycles)
        assertEquals(5500, entry.inhaleMs)
        assertEquals(0, entry.holdInMs)
        // zero-length phases are left out, and whole seconds are written without a decimal.
        //
        // The separator inside 5.5 is the reader's business and not this test's — formatOneDecimal
        // exists precisely so a Belgian sees 5,5 — so the expectation is composed with it rather
        // than spelling the dot a US locale happens to give. As a literal this failed on any
        // machine set to nl_BE, and it would fail the same way on the iOS simulator.
        val half = formatOneDecimal(5.5f)
        assertEquals("$half – $half", entry.pattern)
        assertEquals("4 – 7 – 8", entryFor(0L, 0L, Preset("4-7-8", 4000, 7000, 8000, 0)).pattern)
        // an entry from before timings were logged has nothing to show rather than "0 – 0"
        assertEquals("", Entry(1_000L, 60_000L, "Box 4").pattern)
    }

    @Test
    fun `a goal counts only the sittings inside its own period and in its own unit`() {
        val zone = TimeZone.of("Europe/Brussels")
        val noon = LocalDateTime(2026, 8, 12, 12, 0).toInstant(zone)
        val startOfDay = periodStartMs(GoalPeriod.DAY, noon, zone)
        val startOfWeek = periodStartMs(GoalPeriod.WEEK, noon, zone)

        val preset = Preset("Coherence 5.5", 5500, 0, 5500, 0)
        val log = listOf(
            entryFor(startOfDay - 1, 600_000L, preset), // last night, inside the week only
            entryFor(startOfDay + 1, 300_000L, preset),
            entryFor(startOfDay + 2, 120_000L, preset),
        )

        fun goal(metric: GoalMetric) = Goal(id = 1, metric = metric)
        // 5 + 2 minutes today; the ten from last night belong to yesterday
        assertEquals(7, log.towards(goal(GoalMetric.MINUTES), startOfDay))
        assertEquals(17, log.towards(goal(GoalMetric.MINUTES), startOfWeek))
        assertEquals(2, log.towards(goal(GoalMetric.SITTINGS), startOfDay))
        // 300s and 120s of an 11s breath: 27 and 10 whole ones
        assertEquals(37, log.towards(goal(GoalMetric.BREATHS), startOfDay))
        // reached is the whole of "did I manage it", and short of the target is not reached
        assertTrue(Goal(id = 1, metric = GoalMetric.SITTINGS, target = 2).reached(2))
        assertFalse(Goal(id = 1, metric = GoalMetric.SITTINGS, target = 3).reached(2))
    }

    @Test
    fun `a streak counts back from now and today's blank page does not break it`() {
        val zone = TimeZone.of("Europe/Brussels")
        val wednesdayNoon = LocalDateTime(2026, 8, 12, 12, 0).toInstant(zone)
        val preset = Preset("Coherence 5.5", 5500, 0, 5500, 0)
        fun on(day: Int, hour: Int = 9) =
            entryFor(LocalDateTime(2026, 8, day, hour, 0).toInstant(zone).toEpochMilliseconds(), 300_000L, preset)

        // Monday and Tuesday sat, Wednesday not yet
        val upToYesterday = listOf(on(10), on(11))
        assertEquals(2, upToYesterday.streak(EVERY_DAY, wednesdayNoon, zone))
        // sitting today extends it rather than starting again
        assertEquals(3, (upToYesterday + on(12)).streak(EVERY_DAY, wednesdayNoon, zone))
        // but a whole day missed ends it: Tuesday empty leaves only today
        assertEquals(1, listOf(on(10), on(12)).streak(EVERY_DAY, wednesdayNoon, zone))
        // and nothing since Sunday means the run is over, not merely paused
        assertEquals(0, listOf(on(9)).streak(EVERY_DAY, wednesdayNoon, zone))
    }

    @Test
    fun `a streak is of the goal being reached rather than of sitting at all`() {
        val zone = TimeZone.of("Europe/Brussels")
        val wednesdayNoon = LocalDateTime(2026, 8, 12, 12, 0).toInstant(zone)
        val preset = Preset("Coherence 5.5", 5500, 0, 5500, 0)
        fun on(day: Int, hour: Int, ms: Long) =
            entryFor(LocalDateTime(2026, 8, day, hour, 0).toInstant(zone).toEpochMilliseconds(), ms, preset)

        val tenADay = Goal(id = 1, metric = GoalMetric.MINUTES, period = GoalPeriod.DAY, target = 10)
        // Monday reached in two sittings; Tuesday sat, but only for six minutes
        val log = listOf(on(10, 9, 300_000L), on(10, 18, 360_000L), on(11, 9, 360_000L))
        assertEquals(0, log.streak(tenADay, wednesdayNoon, zone))
        // the same days do carry a streak of merely turning up
        assertEquals(2, log.streak(EVERY_DAY, wednesdayNoon, zone))

        // a weekly goal counts weeks: this one is reached in the week of the 10th alone
        val weekly = Goal(id = 2, metric = GoalMetric.MINUTES, period = GoalPeriod.WEEK, target = 15)
        assertEquals(1, log.streak(weekly, wednesdayNoon, zone))
    }

    @Test
    fun `a reminder that waits for goals only stays quiet once every one is reached`() {
        val zone = TimeZone.of("Europe/Brussels")
        val noon = LocalDateTime(2026, 8, 12, 12, 0).toInstant(zone)
        val preset = Preset("Coherence 5.5", 5500, 0, 5500, 0)
        val today = periodStartMs(GoalPeriod.DAY, noon, zone)
        val log = listOf(entryFor(today + 1, 300_000L, preset)) // one sitting, five minutes

        val oneSitting = Goal(id = 1, metric = GoalMetric.SITTINGS, target = 1)
        val tenMinutes = Goal(id = 2, metric = GoalMetric.MINUTES, target = 10)
        assertTrue(listOf(oneSitting).allReached(log, noon, zone))
        // one goal short is still behind, however well the others are going
        assertFalse(listOf(oneSitting, tenMinutes).allReached(log, noon, zone))
        // and no goals at all is not "done" — a reminder that skipped itself for want of
        // anything to measure would simply never arrive
        assertFalse(emptyList<Goal>().allReached(log, noon, zone))
    }

    @Test
    fun `a milestone run counts days where every daily goal was met`() {
        val zone = TimeZone.of("Europe/Brussels")
        val wednesdayNoon = LocalDateTime(2026, 8, 12, 12, 0).toInstant(zone)
        val preset = Preset("Coherence 5.5", 5500, 0, 5500, 0)
        fun on(day: Int, hour: Int, ms: Long) =
            entryFor(LocalDateTime(2026, 8, day, hour, 0).toInstant(zone).toEpochMilliseconds(), ms, preset)

        val tenMinutes = Goal(id = 1, metric = GoalMetric.MINUTES, target = 10)
        val weekly = Goal(id = 2, metric = GoalMetric.MINUTES, period = GoalPeriod.WEEK, target = 300)
        // Mon and Tue reached the ten minutes, Wed is only halfway there so far
        val log = listOf(on(10, 9, 600_000L), on(11, 9, 600_000L), on(12, 9, 300_000L))

        // today falling short does not end the run: the day is not over
        assertEquals(2, log.allReachedStreak(listOf(tenMinutes), wednesdayNoon, zone))
        // a weekly goal nowhere near met would make every day fail, so it is left out entirely
        assertEquals(2, log.allReachedStreak(listOf(tenMinutes, weekly), wednesdayNoon, zone))
        // with no daily goals the bar is simply having practised, so Wednesday counts too
        assertEquals(3, log.allReachedStreak(listOf(weekly), wednesdayNoon, zone))
        // and a day missed in the middle ends it
        assertEquals(1, listOf(on(10, 9, 600_000L), on(12, 9, 600_000L))
            .allReachedStreak(listOf(tenMinutes), wednesdayNoon, zone))
    }

    @Test
    fun `a milestone is due once and a run that passed one while away still gets its moment`() {
        assertEquals(null, dueMilestone(days = 2, celebrated = 0))
        assertEquals(3, dueMilestone(days = 3, celebrated = 0))
        // shown once: the same run does not celebrate again tomorrow
        assertEquals(null, dueMilestone(days = 4, celebrated = 3))
        assertEquals(7, dueMilestone(days = 7, celebrated = 3))
        // opened after a fortnight away: the longest one passed, not the first or all of them
        assertEquals(30, dueMilestone(days = 45, celebrated = 3))
        // past the last fixed length it is every anniversary, and nothing in between
        assertEquals(null, dueMilestone(days = 700, celebrated = 500))
        assertEquals(730, dueMilestone(days = 730, celebrated = 500))
    }

    @Test
    fun `a goal keeps a target its own metric can mean`() {
        // 300 breaths is a fair week; 300 minutes is not a target, it is a typo
        assertEquals(
            GoalMetric.MINUTES.max,
            Goal(id = 1, metric = GoalMetric.MINUTES, target = 300).sane().target,
        )
        assertEquals(
            GoalMetric.BREATHS.min,
            Goal(id = 2, metric = GoalMetric.BREATHS, target = 1).sane().target,
        )
        // one already in range is left exactly as it was
        val fine = Goal(id = 3, metric = GoalMetric.SITTINGS, target = 2)
        assertEquals(fine, fine.sane())
    }

    @Test
    fun `a sitting too short to count is not logged at all`() {
        val log = listOf(Entry(1_000L, 60_000L, "Box 4"))
        // the same list back, which is what tells the store there is nothing to rewrite
        assertSame(log, log.logging(Entry(2_000L, LOGGED_MIN_MS - 1, "Box 4")))
        assertEquals(2, log.logging(Entry(2_000L, LOGGED_MIN_MS, "Box 4")).size)
    }

    @Test
    fun `pausing and carrying on rewrites the sitting rather than logging a second one`() {
        val started = 1_000L
        val paused = emptyList<Entry>().logging(Entry(started, 60_000L, "Box 4"))
        val finished = paused.logging(Entry(started, 300_000L, "Box 4"))
        assertEquals(1, finished.size)
        assertEquals(300_000L, finished.single().durationMs)
        // a genuinely new sitting is a new start time, so it lands alongside
        assertEquals(2, finished.logging(Entry(started + 1, 60_000L, "Box 4")).size)
    }
}
