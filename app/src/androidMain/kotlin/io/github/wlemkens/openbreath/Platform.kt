package io.github.wlemkens.openbreath

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.service.notification.Condition
import android.text.format.DateFormat
import java.util.Calendar

fun Context.vibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Vibrator::class.java)
    }

/** A short nudge at a phase boundary — long enough to feel with the screen off. */
fun Context.buzz(ms: Long = 45L) {
    val v = vibrator() ?: return
    if (!v.hasVibrator()) return
    v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
}

/**
 * What to put back once the meditation stops, on the old path below. UNKNOWN is a value the
 * system may report but a no-op to write, and writing it would strand the phone in DND, so it
 * becomes ALL.
 */
internal fun restorableFilter(current: Int) =
    if (current == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
        NotificationManager.INTERRUPTION_FILTER_ALL
    } else {
        current
    }

/**
 * Whether a meditation may write the interruption filter at all. Only from a phone that is not
 * already in Do Not Disturb — and only on Android 9 and below, which is the one place the app
 * still writes it.
 *
 * A phone already in DND is in it for a reason of its own — an evening schedule, Bedtime mode, a
 * rule that will end on its own in the morning. `setInterruptionFilter` does not join that rule,
 * it replaces it with a manual override, and a manual override has no morning: the rule's end no
 * longer applies, so the phone stays silent all day. Putting the filter "back" afterwards writes
 * the same override again and does not help. So on those phones, leave it alone: notifications
 * are already silenced, which is what the setting was for.
 */
internal fun shouldSilence(current: Int) =
    restorableFilter(current) == NotificationManager.INTERRUPTION_FILTER_ALL

/**
 * Names the one rule this app owns, and the rule is found again by it — so one left behind by a
 * crash is recognisable on the next run. A getter and not a `val`: `Uri.parse` is stubbed in unit
 * tests, and at file scope it would throw before any test in this file got to run.
 */
private val dndCondition: Uri get() = Uri.parse("openbreath://dnd/session")

/** What it is called in Settings › Do Not Disturb, on the phones that list it. */
private const val DND_RULE_NAME = "OpenBreath meditation"

/**
 * Silences notifications for the length of a meditation, and stops doing so afterwards.
 * Does nothing at all unless the user has granted policy access, which only they can do from
 * system settings — see [notificationPolicyIntent].
 *
 * From Android 10 this is an `AutomaticZenRule` of the app's own rather than the interruption
 * filter, and the difference is the whole point: rules coexist. The evening schedule and this
 * one can both be on, either can end without touching the other, and a schedule that begins
 * mid-sitting is still running when the sitting stops. Writing the filter instead replaces
 * every rule with one manual override that nothing ever lifts, which is how a phone ends up
 * silent all the next day.
 */
class DndGuard(private val context: Context) {
    /** The rule while a sitting is silenced, on Android 10 and up. */
    private var ruleId: String? = null

    /** The filter to put back, on Android 9 and below, where a rule cannot be driven. */
    private var previous: Int? = null

    private val nm: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    val granted: Boolean get() = nm?.isNotificationPolicyAccessGranted == true

    init {
        // a rule outlives the process that made it, so a crash mid-sitting would leave the
        // phone in DND with nothing left to switch it off. Clear ours before anything else
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { nm?.let { removeOurRules(it) } }
        }
    }

    fun silence() {
        val manager = nm ?: return
        if (!manager.isNotificationPolicyAccessGranted) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ruleId != null) return
            runCatching {
                removeOurRules(manager)
                val rule = AutomaticZenRule(
                    DND_RULE_NAME,
                    /* owner = */ null,
                    // where tapping the rule in Settings goes; one of the two must be given
                    ComponentName(context, MainActivity::class.java),
                    dndCondition,
                    /* policy = */ null,
                    NotificationManager.INTERRUPTION_FILTER_ALARMS,
                    /* enabled = */ true,
                )
                manager.addAutomaticZenRule(rule).also { id ->
                    ruleId = id
                    manager.setAutomaticZenRuleState(
                        id,
                        Condition(dndCondition, DND_RULE_NAME, Condition.STATE_TRUE),
                    )
                }
            }
            return
        }
        if (previous != null) return
        if (!shouldSilence(manager.currentInterruptionFilter)) return
        // always ALL, since anything else was just declined — see [shouldSilence]
        previous = NotificationManager.INTERRUPTION_FILTER_ALL
        runCatching { manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS) }
    }

    /** Safe to call when never silenced, and idempotent — leaving a phone stuck in DND is unforgivable. */
    fun restore() {
        val manager = nm ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ruleId = null
            if (!manager.isNotificationPolicyAccessGranted) return
            runCatching { removeOurRules(manager) }
            return
        }
        val prior = previous ?: return
        previous = null
        if (!manager.isNotificationPolicyAccessGranted) return
        runCatching { manager.setInterruptionFilter(prior) }
    }

    /**
     * Removing the rule is what ends our share of the DND, and it leaves nothing in the user's
     * Settings between sittings. By condition rather than by remembered id, so a rule stranded
     * by a crash goes too.
     */
    private fun removeOurRules(manager: NotificationManager) {
        manager.automaticZenRules
            .filterValues { it.conditionId == dndCondition }
            .keys.forEach { runCatching { manager.removeAutomaticZenRule(it) } }
    }
}

fun notificationPolicyIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

/** Opens the form in a browser. The app sends nothing itself — you fill it in and it is yours. */
fun feedbackIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_FORM))

/** The URL is commonMain's now, since both platforms open the same one — see [payPalUrl]. */
fun paypalIntent(euros: Int? = null) = Intent(Intent.ACTION_VIEW, Uri.parse(payPalUrl(euros)))

/**
 * The app's own store page, where the rating is. market:// opens the Play app directly; a phone
 * without it — or a build installed from anywhere else — falls back to the web listing. Trying
 * and catching rather than asking first, which on modern Android needs a <queries> manifest
 * entry just to be told the answer.
 */
fun Context.openRating() {
    val web = "https://play.google.com/store/apps/details?id=$packageName"
    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))) }
        .onFailure { runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(web))) } }
}

/**
 * A time of day written the way this phone writes them. Not [java.time.format.DateTimeFormatter]:
 * that follows the locale alone, and whether the clock reads 14:00 or 2:00 PM is a setting the
 * user makes for themselves, separately from where they live.
 */
fun Context.clockTime(hour: Int, minute: Int): String =
    DateFormat.getTimeFormat(this).format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.time
    )

/** Whether that clock is the 24-hour one, for pickers that have to be asked up front. */
fun Context.uses24Hour(): Boolean = DateFormat.is24HourFormat(this)

/**
 * The flashlight, brightening with the inhale. Wants no CAMERA permission: [setTorchMode] is
 * deliberately outside it. Every call is guarded — another app holding the camera makes these
 * throw, and a meditation must not end because the torch could not.
 */
class Torch(private val context: Context) {
    private val manager: CameraManager?
        get() = context.getSystemService(CameraManager::class.java)

    /** The first camera with a flash. Null on a device without one, which is not an error. */
    private val id: String? by lazy {
        val m = manager ?: return@lazy null
        runCatching {
            m.cameraIdList.firstOrNull {
                m.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    private val maxLevel: Int by lazy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@lazy 1
        val m = manager ?: return@lazy 1
        val cam = id ?: return@lazy 1
        runCatching {
            m.getCameraCharacteristics(cam)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        }.getOrDefault(1)
    }

    val available: Boolean get() = id != null

    private var written = 0

    /**
     * Called every frame from the breath. Only a change in the quantised level reaches the
     * camera service — sixty binder calls a second to say the same thing is how you jam it.
     */
    fun follow(state: PhaseState) = write(torchLevel(state, maxLevel))

    private fun write(want: Int) {
        if (want == written) return
        val m = manager ?: return
        val cam = id ?: return
        // only a call that landed counts as written, so a failed off() is retried on the next frame
        runCatching {
            when {
                want == 0 -> m.setTorchMode(cam, false)
                maxLevel > 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                    m.turnOnTorchWithStrengthLevel(cam, want)
                else -> m.setTorchMode(cam, true)
            }
        }.onSuccess { written = want }
    }

    /** Safe when never lit, and idempotent — a torch left burning outlives the app that lit it. */
    fun off() = write(0)
}
