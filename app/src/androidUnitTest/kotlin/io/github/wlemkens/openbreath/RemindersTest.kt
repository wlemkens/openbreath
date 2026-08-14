package io.github.wlemkens.openbreath

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RemindersTest {
    // a Wednesday, so "next Monday" and "this Friday" are both unambiguous below
    private val wednesdayNoon = LocalDateTime.of(2026, 8, 12, 12, 0)

    private fun at(hour: Int, minute: Int = 0, repeat: Repeat = Repeat.DAILY, vararg days: Int) =
        Reminder(
            id = 1,
            hour = hour,
            minute = minute,
            repeat = repeat,
            days = days.toSet().ifEmpty { setOf(1) },
        )

    @Test
    fun `a daily reminder still to come today is today, and one already past is tomorrow`() {
        assertEquals(
            LocalDateTime.of(2026, 8, 12, 18, 30),
            nextFireAt(at(18, 30), wednesdayNoon),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 7, 0),
            nextFireAt(at(7), wednesdayNoon),
        )
    }

    @Test
    fun `the time it is due right now counts as past, so a reminder never fires twice`() {
        // strictly after, or rescheduling from inside the alarm that just went off would pick
        // the same instant again and ring in a loop
        assertEquals(
            LocalDateTime.of(2026, 8, 13, 12, 0),
            nextFireAt(at(12), wednesdayNoon),
        )
    }

    @Test
    fun `a weekly reminder lands on its own weekday`() {
        // Monday, five days ahead of the Wednesday
        assertEquals(
            LocalDateTime.of(2026, 8, 17, 8, 0),
            nextFireAt(at(8, repeat = Repeat.WEEKLY, days = intArrayOf(1)), wednesdayNoon),
        )
        // its own day, later today
        assertEquals(
            LocalDateTime.of(2026, 8, 12, 18, 0),
            nextFireAt(at(18, repeat = Repeat.WEEKLY, days = intArrayOf(3)), wednesdayNoon),
        )
        // its own day, but this morning has gone: a week out, not later the same day
        assertEquals(
            LocalDateTime.of(2026, 8, 19, 7, 0),
            nextFireAt(at(7, repeat = Repeat.WEEKLY, days = intArrayOf(3)), wednesdayNoon),
        )
    }

    @Test
    fun `a weekly reminder on several days takes whichever comes first`() {
        // Mon, Wed and Fri, from Wednesday noon: Wednesday evening, then Friday, then Monday
        val mwf = at(18, repeat = Repeat.WEEKLY, days = intArrayOf(1, 3, 5))
        val wednesdayEvening = LocalDateTime.of(2026, 8, 12, 18, 0)
        assertEquals(wednesdayEvening, nextFireAt(mwf, wednesdayNoon))
        assertEquals(LocalDateTime.of(2026, 8, 14, 18, 0), nextFireAt(mwf, wednesdayEvening))
        assertEquals(
            LocalDateTime.of(2026, 8, 17, 18, 0),
            nextFireAt(mwf, LocalDateTime.of(2026, 8, 14, 18, 0)),
        )
    }

    @Test
    fun `the fortnightly pair follow the ISO week number, not the day they were set on`() {
        // 12 August 2026 is in ISO week 33, an odd one
        assertEquals(33, isoWeek(wednesdayNoon.toLocalDate()))

        val odd = at(8, repeat = Repeat.ODD_WEEKS, days = intArrayOf(1))
        val even = at(8, repeat = Repeat.EVEN_WEEKS, days = intArrayOf(1))
        // this week's Monday has gone, so odd skips to week 35 while even takes week 34
        assertEquals(LocalDateTime.of(2026, 8, 24, 8, 0), nextFireAt(odd, wednesdayNoon))
        assertEquals(LocalDateTime.of(2026, 8, 17, 8, 0), nextFireAt(even, wednesdayNoon))
        // and they stay on their own weeks months later, whenever you ask
        assertEquals(
            LocalDateTime.of(2026, 11, 30, 8, 0),
            nextFireAt(odd, LocalDateTime.of(2026, 11, 24, 12, 0)),
        )
    }

    @Test
    fun `a day still ahead in a matching week is taken rather than skipped`() {
        // Wednesday noon in odd week 33: Thursday is still to come, so it is not put off a
        // fortnight the way counting from the day it was set would
        val odd = at(8, repeat = Repeat.ODD_WEEKS, days = intArrayOf(1, 4))
        assertEquals(LocalDateTime.of(2026, 8, 13, 8, 0), nextFireAt(odd, wednesdayNoon))
        // then both days move to week 35 together, neither straying into the even week
        assertEquals(
            LocalDateTime.of(2026, 8, 24, 8, 0),
            nextFireAt(odd, LocalDateTime.of(2026, 8, 13, 8, 0)),
        )
        assertEquals(
            LocalDateTime.of(2026, 8, 27, 8, 0),
            nextFireAt(odd, LocalDateTime.of(2026, 8, 24, 8, 0)),
        )
    }

    @Test
    fun `the near one of the two is named, since which it is depends on when you look`() {
        val wednesday = wednesdayNoon.toLocalDate() // odd week 33
        assertEquals(" (this week)", Repeat.ODD_WEEKS.weekHint(wednesday))
        assertEquals(" (next week)", Repeat.EVEN_WEEKS.weekHint(wednesday))
        // a week on, the labels have swapped over
        assertEquals(" (next week)", Repeat.ODD_WEEKS.weekHint(wednesday.plusWeeks(1)))
        assertEquals(" (this week)", Repeat.EVEN_WEEKS.weekHint(wednesday.plusWeeks(1)))
        // and the other two are not a fortnight at all, so they say nothing
        assertEquals("", Repeat.DAILY.weekHint(wednesday))
        assertEquals("", Repeat.WEEKLY.weekHint(wednesday))
    }
}
