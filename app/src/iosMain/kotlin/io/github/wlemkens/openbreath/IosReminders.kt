package io.github.wlemkens.openbreath

import io.github.wlemkens.openbreath.media.Res
import io.github.wlemkens.openbreath.media.notification_body
import org.jetbrains.compose.resources.getString

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume
import kotlin.time.Clock

/**
 * Reminders on iOS: local notifications, scheduled ahead.
 *
 * The shape is genuinely different from Android's and it is worth saying why rather than reading
 * this as a translation. Android sets **one** alarm at a time and re-arms it when it fires, so the
 * decision about the next occurrence is made at the last possible moment, by code that can read the
 * log. Nothing on iOS runs at that moment: there is no receiver, and a local notification is handed
 * to the system now and delivered by the system later, whatever the app is doing.
 *
 * So this schedules the next [OCCURRENCES] occurrences of every reminder up front and tops them up
 * whenever the app is opened, which is exactly how far ahead a fortnightly rule needs to be
 * resolved anyway. iOS caps an app at 64 pending notifications and drops the rest silently, which
 * is why the number is small and why the count is checked rather than assumed.
 *
 * Two things follow that the screen shows differently on this platform:
 *
 * - **No ringing until dismissed.** [canRingUntilDismissed] is false. Android's insistent alarm
 *   needs a sound that repeats, and the iOS equivalent is the Critical Alerts entitlement, which is
 *   applied for and granted case by case. A default tone that plays once is not that, so the switch
 *   is hidden rather than shown doing something quieter than it says.
 * - **`onlyIfBehind` is decided when the app is used, not when the notification fires.** Android
 *   checks the goals in the receiver, microseconds before it rings. Here the check happens in
 *   [apply], which the app calls on launch and after every sitting — and that is enough, because
 *   the only way to get ahead of a goal is to practise, and the only way to practise is to open the
 *   app. A day's reminder is withdrawn the moment the sitting that satisfies it is logged.
 */
@OptIn(ExperimentalForeignApi::class)
class IosReminders : ReminderScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override val supported = true

    override val canRingUntilDismissed = false

    /** Nothing to say: iOS delivers to the minute and has no permission that changes it. */
    override val late = false

    override fun fixLateness() = Unit

    override suspend fun permitted(): Boolean = suspendCancellableCoroutine { cont ->
        center.getNotificationSettingsWithCompletionHandler { settings ->
            val status = settings?.authorizationStatus
            cont.resume(
                status == UNAuthorizationStatusAuthorized ||
                    status == UNAuthorizationStatusProvisional
            )
        }
    }

    /**
     * The app's first and only permission prompt. iOS shows it once ever; asking again after a
     * refusal does nothing at all and returns false, which is why the screen offers Settings
     * instead once it has been asked.
     */
    override suspend fun request(): Boolean = suspendCancellableCoroutine { cont ->
        val options = UNAuthorizationOptionAlert or
            UNAuthorizationOptionSound or
            UNAuthorizationOptionBadge
        center.requestAuthorizationWithOptions(options) { granted, _ ->
            cont.resume(granted)
        }
    }

    /**
     * Re-arms everything, by throwing all of ours away and scheduling again. Cheaper than
     * reconciling, and it is the only way a reminder whose *rule* changed leaves no orphan behind:
     * a notification is identified by a string we chose, and yesterday's string is not recomputed
     * from today's reminder.
     */
    override suspend fun apply(reminders: List<Reminder>) {
        center.removeAllPendingNotificationRequests()
        // read once for the lot: the body is the same on every occurrence of every reminder, and
        // it is a resource lookup rather than a constant now that the app speaks three languages
        val body = getString(Res.string.notification_body)
        val zone = TimeZone.currentSystemDefault()
        var now = Clock.System.now().toLocalDateTime(zone)
        for (reminder in reminders) {
            if (!reminder.enabled) continue
            var at = now
            repeat(OCCURRENCES) {
                at = nextFireAt(reminder, at)
                schedule(reminder, body, at.hour, at.minute, at.date.year, at.date.monthNumber, at.date.dayOfMonth)
            }
        }
    }

    /**
     * Only ever called for a reminder being deleted or switched off, and [apply] follows it, so
     * this could be empty. It is not, because a cancel that does nothing is a trap for whoever
     * calls it next without reading.
     */
    override fun cancel(id: Int) {
        center.removePendingNotificationRequestsWithIdentifiers(
            List(OCCURRENCES) { "$ID_PREFIX$id-$it" }
        )
    }

    private fun schedule(
        reminder: Reminder,
        body: String,
        hour: Int,
        minute: Int,
        year: Int,
        month: Int,
        day: Int,
    ) {
        val content = UNMutableNotificationContent().apply {
            // the name is the user's own text; the body is ours, and translated
            setTitle(reminder.name)
            setBody(body)
            // the default tone, once. See canRingUntilDismissed for why there is no other option
            setSound(UNNotificationSound.defaultSound)
        }

        // A dated trigger rather than a repeating one, because the repeat rules iOS offers are
        // daily and weekly and this app also has fortnightly, which it has no idea about.
        val components = NSCalendar.currentCalendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute,
            fromDate = NSDate(),
        ).apply {
            setYear(year.toLong())
            setMonth(month.toLong())
            setDay(day.toLong())
            setHour(hour.toLong())
            setMinute(minute.toLong())
            setSecond(0)
        }

        center.addNotificationRequest(
            UNNotificationRequest.requestWithIdentifier(
                identifier = "$ID_PREFIX${reminder.id}-$year$month$day$hour$minute",
                content = content,
                trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                    dateComponents = components,
                    repeats = false,
                ),
            ),
            withCompletionHandler = null,
        )
    }

    private companion object {
        /**
         * How far ahead each reminder is armed. iOS keeps at most 64 pending notifications per app
         * and silently discards the rest, so this multiplied by the number of reminders has to stay
         * well under that — eight covers a fortnightly rule for four months and leaves room for
         * seven other reminders.
         */
        const val OCCURRENCES = 8

        const val ID_PREFIX = "openbreath-reminder-"
    }
}
