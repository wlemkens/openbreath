package io.github.wlemkens.openbreath

import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.notification_body
import io.github.wlemkens.openbreath.media.notification_body_alarm
import io.github.wlemkens.openbreath.media.notification_channel_alarms
import io.github.wlemkens.openbreath.media.notification_channel_reminders
import org.jetbrains.compose.resources.getString

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/**
 * The recurrence rule moved to commonMain/Recurrence.kt when reminders stopped being Android's
 * alone. What is left here is the alarm table: [nextFireAt] says when, AlarmManager says how.
 */

private const val ACTION_REMIND = "io.github.wlemkens.openbreath.REMIND"
private const val EXTRA_ID = "id"
private const val CHANNEL = "reminders"
private const val CHANNEL_ALARM = "alarms"

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
    // the rule is shared and works in wall-clock terms; only the conversion to an instant for
    // AlarmManager is local, and it has to use the same zone the rule was reasoned in
    val zone = TimeZone.currentSystemDefault()
    val at = nextFireAt(reminder, Clock.System.now().toLocalDateTime(zone))
        .toInstant(zone).toEpochMilliseconds()
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
                // a receiver has no composition to read LocalStore from, so it opens its own
                // handle on the same file — DataStore keeps a single instance per path anyway
                val store = context.store()
                val stored = store.remindersFlow().first()
                if (intent.action == ACTION_REMIND) {
                    val reminder = stored.firstOrNull { it.id == id && it.enabled } ?: return@launch
                    val done = reminder.onlyIfBehind &&
                        store.goalsFlow().first()
                            .allReached(store.historyFlow().first(), Clock.System.now())
                    if (!done) context.ring(reminder)
                    // rescheduled either way: a morning it stayed quiet is not the end of it
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

/**
 * The alarm channel: the phone's own alarm tone, on the alarm stream so it is heard through a
 * silenced ringer. A channel is fixed once created, so its settings are what a user who never
 * touches them gets — and what they change is theirs to keep.
 */
private fun alarmChannel(name: String) =
    NotificationChannel(CHANNEL_ALARM, name, NotificationManager.IMPORTANCE_HIGH).apply {
        setSound(
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        enableVibration(true)
        // honoured only where the app has been given notification policy access, quietly ignored
        // otherwise — the same access the silence-during-a-session setting asks for
        setBypassDnd(true)
    }

private suspend fun Context.ring(reminder: Reminder) {
    val manager = getSystemService(NotificationManager::class.java) ?: return
    // creating it every time is cheap and idempotent, and saves tracking whether this install
    // has been through a run since the channel was added
    manager.createNotificationChannel(
        if (reminder.alarm) {
            alarmChannel(getString(Res.string.notification_channel_alarms))
        } else {
            NotificationChannel(
                CHANNEL,
                getString(Res.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        }
    )
    val open = PendingIntent.getActivity(
        this,
        reminder.id,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = Notification.Builder(this, if (reminder.alarm) CHANNEL_ALARM else CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(reminder.name)
        // the title is the reminder's name, which is the user's own text; the line under it is
        // ours, and translated
        .setContentText(
            getString(
                if (reminder.alarm) Res.string.notification_body_alarm else Res.string.notification_body
            )
        )
        .setContentIntent(open)
        .setCategory(if (reminder.alarm) Notification.CATEGORY_ALARM else Notification.CATEGORY_REMINDER)
        // dismissible on purpose: an ongoing alarm you cannot swipe away is one you cannot stop
        .setAutoCancel(true)
        .build()
    if (reminder.alarm) {
        // repeats the tone until the notification is dismissed or opened. This one flag is the
        // whole difference between a reminder you can sleep through and one you cannot
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
    }
    // notifications can be revoked at any time; that is not an error worth crashing a broadcast
    runCatching { manager.notify(reminder.id, notification) }
}
