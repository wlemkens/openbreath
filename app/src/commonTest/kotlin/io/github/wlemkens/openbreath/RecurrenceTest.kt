package io.github.wlemkens.openbreath

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recurrence rule, which had no test at all while it lived in androidMain — the fortnightly
 * weeks are the fiddly part and the year boundary is where they are fiddliest.
 */
class RecurrenceTest {

    private fun at(s: String) = LocalDateTime.parse(s)
    private fun on(s: String) = LocalDate.parse(s)

    @Test
    fun `a daily reminder later today fires today`() {
        val r = Reminder(id = 1, hour = 20, minute = 0)
        assertEquals(at("2026-03-10T20:00"), nextFireAt(r, at("2026-03-10T08:30")))
    }

    @Test
    fun `a daily reminder already past fires tomorrow`() {
        val r = Reminder(id = 1, hour = 8, minute = 0)
        assertEquals(at("2026-03-11T08:00"), nextFireAt(r, at("2026-03-10T08:30")))
    }

    @Test
    fun `the fire time is strictly after now rather than the very minute asked about`() {
        val r = Reminder(id = 1, hour = 8, minute = 0)
        assertEquals(at("2026-03-11T08:00"), nextFireAt(r, at("2026-03-10T08:00")))
    }

    @Test
    fun `a weekly reminder lands on the next chosen day`() {
        // 2026-03-10 is a Tuesday; Thursday is day 4
        val r = Reminder(id = 1, hour = 7, minute = 30, repeat = Repeat.WEEKLY, days = setOf(4))
        assertEquals(at("2026-03-12T07:30"), nextFireAt(r, at("2026-03-10T09:00")))
    }

    @Test
    fun `several days in one week stay in that week rather than each drifting`() {
        val r = Reminder(id = 1, hour = 7, minute = 0, repeat = Repeat.WEEKLY, days = setOf(1, 3, 5))
        // Tuesday: the next of Mon/Wed/Fri is Wednesday, not next Monday
        assertEquals(at("2026-03-11T07:00"), nextFireAt(r, at("2026-03-10T09:00")))
    }

    @Test
    fun `a reminder with no days left still finds a day rather than looping forever`() {
        val r = Reminder(id = 1, hour = 7, minute = 0, repeat = Repeat.WEEKLY, days = emptySet())
        assertEquals(at("2026-03-17T07:00"), nextFireAt(r, at("2026-03-10T09:00")))
    }

    @Test
    fun `an odd-week reminder skips the even week`() {
        val r = Reminder(id = 1, hour = 7, minute = 0, repeat = Repeat.ODD_WEEKS, days = setOf(1))
        val fire = nextFireAt(r, at("2026-03-10T09:00"))
        assertTrue(isoWeek(fire.date) % 2 == 1, "landed in week ${isoWeek(fire.date)}, wanted an odd one")
        assertEquals(1, fire.date.dayOfWeek.ordinal + 1, "wanted a Monday")
    }

    @Test
    fun `an even-week reminder skips the odd week`() {
        val r = Reminder(id = 1, hour = 7, minute = 0, repeat = Repeat.EVEN_WEEKS, days = setOf(1))
        val fire = nextFireAt(r, at("2026-03-10T09:00"))
        assertTrue(isoWeek(fire.date) % 2 == 0, "landed in week ${isoWeek(fire.date)}, wanted an even one")
    }

    @Test
    fun `a fortnightly reminder is never more than a fortnight away`() {
        for (repeat in listOf(Repeat.ODD_WEEKS, Repeat.EVEN_WEEKS)) {
            val r = Reminder(id = 1, hour = 7, minute = 0, repeat = repeat, days = setOf(1))
            // every day of a long stretch, including both year boundaries
            var d = on("2026-12-01")
            while (d < on("2027-02-01")) {
                val now = LocalDateTime(d, kotlinx.datetime.LocalTime(9, 0))
                val fire = nextFireAt(r, now)
                assertTrue(fire > now, "$repeat on $d went backwards")
                val days = fire.date.toEpochDays() - d.toEpochDays()
                assertTrue(days <= 21, "$repeat on $d was $days days out")
                d = LocalDate.fromEpochDays(d.toEpochDays() + 1)
            }
        }
    }

    @Test
    fun `ISO weeks are the ones a wall planner prints`() {
        // 2026-01-01 is a Thursday, so it is in week 1 of 2026
        assertEquals(1, isoWeek(on("2026-01-01")))
        assertEquals(1, isoWeek(on("2025-12-29")))  // Monday of that same ISO week
        assertEquals(53, isoWeek(on("2020-12-31")))  // 2020 was a 53-week year
        assertEquals(1, isoWeek(on("2021-01-04")))
    }

    @Test
    fun `the week hint says which of the two fortnights is the near one`() {
        val odd = Repeat.ODD_WEEKS
        val day = on("2026-03-10")
        val hint = odd.weekHint(day)
        assertEquals(if (isoWeek(day.mondayOfWeek()) % 2 == 1) " (this week)" else " (next week)", hint)
        assertEquals("", Repeat.DAILY.weekHint(day))
    }
}
