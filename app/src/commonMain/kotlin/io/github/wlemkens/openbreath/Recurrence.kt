package io.github.wlemkens.openbreath

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * When a reminder is next due, and which weeks it falls in. Pure arithmetic over a wall clock,
 * with no scheduler anywhere near it.
 *
 * In commonMain because the rule is the same on every platform and only the alarm it eventually
 * sets is not. It lived in androidMain on `java.time` for as long as reminders were Android's
 * alone, which meant the one genuinely fiddly part of the feature — fortnightly weeks across a
 * year boundary — had no test that could run.
 */

/**
 * When a reminder is next due, strictly after [now]. The whole of the recurrence rule: every
 * alarm this app sets is a single shot at this instant, rescheduled once it fires. Repeating
 * alarms drift and outlive the settings that created them.
 */
internal fun nextFireAt(reminder: Reminder, now: LocalDateTime): LocalDateTime {
    val time = LocalTime(reminder.hour, reminder.minute)
    val today = now.date
    if (reminder.repeat == Repeat.DAILY) {
        val date = if (LocalDateTime(today, time) > now) today else today.plus(1, DateTimeUnit.DAY)
        return LocalDateTime(date, time)
    }

    // weeks are walked from their Monday, so that a reminder on several days keeps them together
    // in one week rather than each drifting into a schedule of its own
    val thisWeek = today.mondayOfWeek()
    // a stored reminder emptied of its days would have nothing to land on at all
    val days = reminder.days.filter { it in 1..7 }.sorted()
        .ifEmpty { listOf(today.dayOfWeek.isoDayNumber) }

    // five weeks is more than enough: the next week of the right parity is at most two out,
    // whichever of its days the reminder falls on
    return generateSequence(thisWeek) { it.plus(1, DateTimeUnit.WEEK) }
        .take(5)
        .filter { reminder.repeat.covers(it) }
        .flatMap { week -> days.map { LocalDateTime(week.plus(it - 1, DateTimeUnit.DAY), time) } }
        .first { it > now }
}

/** The Monday of the week this date is in, which is where every weekly rule is counted from. */
internal fun LocalDate.mondayOfWeek(): LocalDate = minus(dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)

/**
 * The ISO week number, the one a European wall planner prints. A year of 53 weeks puts two odd
 * weeks back to back over new year; that is what week numbers do, not a fault to correct.
 *
 * Computed rather than looked up, because kotlinx-datetime carries no week fields — the Thursday
 * of a week decides which year the week belongs to, so counting whole weeks up to that Thursday
 * gives the ISO number without any of the year-boundary special cases being written out.
 */
internal fun isoWeek(date: LocalDate): Int {
    val thursday = date.plus(4 - date.dayOfWeek.isoDayNumber, DateTimeUnit.DAY)
    return (thursday.dayOfYear - 1) / 7 + 1
}

/** Whether the week beginning on the Monday [weekStart] is one this recurrence falls in. */
internal fun Repeat.covers(weekStart: LocalDate): Boolean = when (this) {
    Repeat.DAILY, Repeat.WEEKLY -> true
    Repeat.ODD_WEEKS -> isoWeek(weekStart) % 2 == 1
    Repeat.EVEN_WEEKS -> isoWeek(weekStart) % 2 == 0
}

/**
 * " (this week)" or " (next week)". Which of the two fortnightly options is the near one depends
 * on when you are looking at them, and picking between them is impossible without being told.
 */
internal fun Repeat.weekHint(today: LocalDate): String = when (this) {
    Repeat.ODD_WEEKS, Repeat.EVEN_WEEKS ->
        if (covers(today.mondayOfWeek())) " (this week)" else " (next week)"

    else -> ""
}
