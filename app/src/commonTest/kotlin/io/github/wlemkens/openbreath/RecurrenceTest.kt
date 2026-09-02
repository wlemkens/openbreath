package io.github.wlemkens.openbreath

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The recurrence rule. These were androidUnitTest's `RemindersTest` and ran on the JVM only,
 * because the rule they cover was in androidMain on `java.time`; they came here with it, so iOS
 * and the desktop are held to the same arithmetic rather than trusted with a copy of it.
 *
 * Two names lost a comma on the way. Backtick names become Objective-C symbols on Kotlin/Native
 * and a comma is illegal in one, which is the trap CLAUDE.md names and which cost a red build
 * moving these.
 */
class RecurrenceTest {
    // a Wednesday, so "next Monday" and "this Friday" are both unambiguous below
    private val wednesdayNoon = LocalDateTime(2026, 8, 12, 12, 0)

    private fun at(hour: Int, minute: Int = 0, repeat: Repeat = Repeat.DAILY, vararg days: Int) =
        Reminder(
            id = 1,
            hour = hour,
            minute = minute,
            repeat = repeat,
            days = days.toSet().ifEmpty { setOf(1) },
        )

    @Test
    fun `a daily reminder still to come is today and one already past is tomorrow`() {
        assertEquals(LocalDateTime(2026, 8, 12, 18, 30), nextFireAt(at(18, 30), wednesdayNoon))
        assertEquals(LocalDateTime(2026, 8, 13, 7, 0), nextFireAt(at(7), wednesdayNoon))
    }

    @Test
    fun `the time it is due right now counts as past so a reminder never fires twice`() {
        // strictly after, or rescheduling from inside the alarm that just went off would pick
        // the same instant again and ring in a loop
        assertEquals(LocalDateTime(2026, 8, 13, 12, 0), nextFireAt(at(12), wednesdayNoon))
    }

    @Test
    fun `a weekly reminder lands on its own weekday`() {
        // Monday, five days ahead of the Wednesday
        assertEquals(
            LocalDateTime(2026, 8, 17, 8, 0),
            nextFireAt(at(8, repeat = Repeat.WEEKLY, days = intArrayOf(1)), wednesdayNoon),
        )
        // its own day, later today
        assertEquals(
            LocalDateTime(2026, 8, 12, 18, 0),
            nextFireAt(at(18, repeat = Repeat.WEEKLY, days = intArrayOf(3)), wednesdayNoon),
        )
        // its own day, but this morning has gone: a week out, not later the same day
        assertEquals(
            LocalDateTime(2026, 8, 19, 7, 0),
            nextFireAt(at(7, repeat = Repeat.WEEKLY, days = intArrayOf(3)), wednesdayNoon),
        )
    }

    @Test
    fun `a weekly reminder on several days takes whichever comes first`() {
        // Mon, Wed and Fri, from Wednesday noon: Wednesday evening, then Friday, then Monday
        val mwf = at(18, repeat = Repeat.WEEKLY, days = intArrayOf(1, 3, 5))
        val wednesdayEvening = LocalDateTime(2026, 8, 12, 18, 0)
        assertEquals(wednesdayEvening, nextFireAt(mwf, wednesdayNoon))
        assertEquals(LocalDateTime(2026, 8, 14, 18, 0), nextFireAt(mwf, wednesdayEvening))
        assertEquals(
            LocalDateTime(2026, 8, 17, 18, 0),
            nextFireAt(mwf, LocalDateTime(2026, 8, 14, 18, 0)),
        )
    }

    @Test
    fun `the fortnightly pair follow the ISO week number rather than the day they were set on`() {
        // 12 August 2026 is in ISO week 33, an odd one
        assertEquals(33, isoWeek(wednesdayNoon.date))

        val odd = at(8, repeat = Repeat.ODD_WEEKS, days = intArrayOf(1))
        val even = at(8, repeat = Repeat.EVEN_WEEKS, days = intArrayOf(1))
        // this week's Monday has gone, so odd skips to week 35 while even takes week 34
        assertEquals(LocalDateTime(2026, 8, 24, 8, 0), nextFireAt(odd, wednesdayNoon))
        assertEquals(LocalDateTime(2026, 8, 17, 8, 0), nextFireAt(even, wednesdayNoon))
        // and they stay on their own weeks months later, whenever you ask
        assertEquals(
            LocalDateTime(2026, 11, 30, 8, 0),
            nextFireAt(odd, LocalDateTime(2026, 11, 24, 12, 0)),
        )
    }

    @Test
    fun `a day still ahead in a matching week is taken rather than skipped`() {
        // Wednesday noon in odd week 33: Thursday is still to come, so it is not put off a
        // fortnight the way counting from the day it was set would
        val odd = at(8, repeat = Repeat.ODD_WEEKS, days = intArrayOf(1, 4))
        assertEquals(LocalDateTime(2026, 8, 13, 8, 0), nextFireAt(odd, wednesdayNoon))
        // then both days move to week 35 together, neither straying into the even week
        assertEquals(
            LocalDateTime(2026, 8, 24, 8, 0),
            nextFireAt(odd, LocalDateTime(2026, 8, 13, 8, 0)),
        )
        assertEquals(
            LocalDateTime(2026, 8, 27, 8, 0),
            nextFireAt(odd, LocalDateTime(2026, 8, 24, 8, 0)),
        )
    }

    @Test
    fun `the near one of the two is named since which it is depends on when you look`() {
        val wednesday = wednesdayNoon.date // odd week 33
        assertEquals(" (this week)", Repeat.ODD_WEEKS.weekHint(wednesday))
        assertEquals(" (next week)", Repeat.EVEN_WEEKS.weekHint(wednesday))
        // a week on, the labels have swapped over
        val next = wednesday.plus(1, DateTimeUnit.WEEK)
        assertEquals(" (next week)", Repeat.ODD_WEEKS.weekHint(next))
        assertEquals(" (this week)", Repeat.EVEN_WEEKS.weekHint(next))
        // and the other two are not a fortnight at all, so they say nothing
        assertEquals("", Repeat.DAILY.weekHint(wednesday))
        assertEquals("", Repeat.WEEKLY.weekHint(wednesday))
    }

    // --- what the move added, rather than carried over -----------------------------------------

    @Test
    fun `a reminder emptied of its days still finds one rather than looping forever`() {
        // not reachable through the screen, which refuses to unset the last day — but a stored
        // reminder is a stored shape, and `first {}` over an empty sequence throws
        val none = Reminder(id = 1, hour = 7, repeat = Repeat.WEEKLY, days = emptySet())
        assertEquals(LocalDateTime(2026, 8, 19, 7, 0), nextFireAt(none, wednesdayNoon))
    }

    @Test
    fun `a fortnightly reminder never goes backwards or more than three weeks out`() {
        // swept across a year boundary because that is where ISO weeks misbehave: a 53-week year
        // puts two odd weeks back to back, and a rule checked by hand in August would not notice
        for (repeat in listOf(Repeat.ODD_WEEKS, Repeat.EVEN_WEEKS)) {
            val r = Reminder(id = 1, hour = 7, repeat = repeat, days = setOf(1))
            var day = LocalDate(2026, 12, 1)
            while (day < LocalDate(2027, 2, 1)) {
                val now = LocalDateTime(day, LocalTime(9, 0))
                val fire = nextFireAt(r, now)
                assertTrue(fire > now, "$repeat on $day went backwards")
                val out = fire.date.toEpochDays() - day.toEpochDays()
                assertTrue(out <= 21, "$repeat on $day was $out days out")
                day = day.plus(1, DateTimeUnit.DAY)
            }
        }
    }

    @Test
    fun `ISO weeks are the ones a wall planner prints`() {
        // computed here rather than looked up, since kotlinx-datetime has no week fields
        assertEquals(1, isoWeek(LocalDate(2026, 1, 1)))    // a Thursday, so week 1 of 2026
        assertEquals(1, isoWeek(LocalDate(2025, 12, 29)))  // the Monday of that same ISO week
        assertEquals(53, isoWeek(LocalDate(2020, 12, 31))) // 2020 ran to 53 weeks
        assertEquals(1, isoWeek(LocalDate(2021, 1, 4)))
    }
}
