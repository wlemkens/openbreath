package io.github.wlemkens.breath

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * When a reminder is next due, strictly after [now]. Pure, and the whole of the recurrence
 * rule: every alarm this app sets is a single shot at this instant, rescheduled once it fires.
 * Repeating alarms drift and outlive the settings that created them.
 */
internal fun nextFireAt(reminder: Reminder, now: LocalDateTime): LocalDateTime {
    val time = LocalTime.of(reminder.hour, reminder.minute)
    val today = now.toLocalDate()
    if (reminder.repeat == Repeat.DAILY) {
        val date = if (today.atTime(time).isAfter(now)) today else today.plusDays(1)
        return date.atTime(time)
    }

    // weeks are walked from their Monday, so that a reminder on several days keeps them together
    // in one week rather than each drifting into a schedule of its own
    val thisWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    // a stored reminder emptied of its days would have nothing to land on at all
    val days = reminder.days.filter { it in 1..7 }.sorted().ifEmpty { listOf(today.dayOfWeek.value) }

    // five weeks is more than enough: the next week of the right parity is at most two out,
    // whichever of its days the reminder falls on
    return generateSequence(thisWeek) { it.plusWeeks(1) }
        .take(5)
        .filter { reminder.repeat.covers(it) }
        .flatMap { week -> days.map { week.plusDays((it - 1).toLong()).atTime(time) } }
        .first { it.isAfter(now) }
}

/**
 * The ISO week number, the one a European wall planner prints. A year of 53 weeks puts two odd
 * weeks back to back over new year; that is what week numbers do, not a fault to correct.
 */
internal fun isoWeek(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

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
        if (covers(today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))) {
            " (this week)"
        } else {
            " (next week)"
        }

    else -> ""
}

private const val ACTION_REMIND = "io.github.wlemkens.breath.REMIND"
private const val EXTRA_ID = "id"
private const val CHANNEL = "reminders"

private fun Context.alarms(): AlarmManager? = getSystemService(AlarmManager::class.java)

/**
 * Whether the phone will let us fire to the minute. Android 12 made that a permission and
 * Android 14 stopped granting it by default, so the honest answer is often no — reminders then
 * fall back to an inexact alarm, which doze can hold back by a few minutes.
 */
fun Context.canScheduleExact(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms()?.canScheduleExactAlarms() == true

fun exactAlarmIntent() = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)

private fun Context.alarmIntent(id: Int) = PendingIntent.getBroadcast(
    this,
    id,
    Intent(ACTION_REMIND, null, this, ReminderReceiver::class.java).putExtra(EXTRA_ID, id),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

fun Context.scheduleReminder(reminder: Reminder) {
    val manager = alarms() ?: return
    if (!reminder.enabled) return cancelReminder(reminder.id)
    val at = nextFireAt(reminder, LocalDateTime.now())
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pending = alarmIntent(reminder.id)
    // both survive doze; only the first is to the minute, and only with the permission granted
    runCatching {
        if (canScheduleExact()) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
}

fun Context.cancelReminder(id: Int) {
    alarms()?.cancel(alarmIntent(id))
}

/** Re-arms every reminder. Safe to call repeatedly — an alarm for the same id replaces itself. */
fun Context.applyReminders(reminders: List<Reminder>) = reminders.forEach { scheduleReminder(it) }

/**
 * Rings a reminder and arms the next one. Also re-arms everything after a reboot, which clears
 * the alarm table. A reminder that has since been deleted or turned off simply stops here,
 * so a stale alarm can never outlive it by more than one firing.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val stored = context.remindersFlow().first()
                if (intent.action == ACTION_REMIND) {
                    val reminder = stored.firstOrNull { it.id == id && it.enabled } ?: return@launch
                    context.ring(reminder)
                    context.scheduleReminder(reminder)
                } else {
                    context.applyReminders(stored)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

private fun Context.ring(reminder: Reminder) {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    // creating it every time is cheap and idempotent, and saves tracking whether this install
    // has been through a run since the channel was added
    manager.createNotificationChannel(
        NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_DEFAULT)
    )
    val open = PendingIntent.getActivity(
        this,
        reminder.id,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(reminder.name)
        .setContentText("Time to breathe")
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    // notifications can be revoked at any time; that is not an error worth crashing a broadcast
    runCatching { manager.notify(reminder.id, notification) }
}
