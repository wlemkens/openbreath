package io.github.wlemkens.openbreath

import android.app.NotificationManager
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
import android.text.format.DateFormat
import java.util.Calendar
import kotlin.math.roundToInt

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
 * Silences notifications for the length of a meditation and puts the previous state back
 * afterwards. Does nothing at all unless the user has granted policy access, which only
 * they can do from system settings — see [notificationPolicyIntent].
 */
/**
 * What to put back once the meditation stops. UNKNOWN is a value the system may report but
 * a no-op to write, and writing it would strand the phone in DND, so it becomes ALL.
 */
internal fun restorableFilter(current: Int) =
    if (current == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
        NotificationManager.INTERRUPTION_FILTER_ALL
    } else {
        current
    }

class DndGuard(private val context: Context) {
    private var previous: Int? = null

    private val nm: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    val granted: Boolean get() = nm?.isNotificationPolicyAccessGranted == true

    fun silence() {
        val manager = nm ?: return
        if (!manager.isNotificationPolicyAccessGranted || previous != null) return
        previous = restorableFilter(manager.currentInterruptionFilter)
        runCatching { manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS) }
    }

    /** Safe to call when never silenced, and idempotent — leaving a phone stuck in DND is unforgivable. */
    fun restore() {
        val manager = nm ?: return
        val prior = previous ?: return
        previous = null
        if (!manager.isNotificationPolicyAccessGranted) return
        runCatching { manager.setInterruptionFilter(prior) }
    }
}

fun notificationPolicyIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

/** Opens the form in a browser. The app sends nothing itself — you fill it in and it is yours. */
fun feedbackIntent() = Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_FORM))

private const val PAYPAL_ME = "https://paypal.me/wimlemkens"

/**
 * PayPal, with the amount already filled in when one was chosen. Nothing is charged here and no
 * amount is remembered: the app opens a page and steps out of the way.
 */
fun paypalIntent(euros: Int? = null) =
    Intent(Intent.ACTION_VIEW, Uri.parse(if (euros == null) PAYPAL_ME else "$PAYPAL_ME/${euros}EUR"))

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
 * The breath as a torch strength, 0 meaning off. Variable brightness only exists from API 33,
 * and even then plenty of devices report a maximum of 1 — those get lit for the whole inhale and
 * dark for the whole exhale, which is still a breath you can follow. Pulsing the torch faster to
 * fake levels flickers and hammers the camera service, so it isn't attempted.
 */
internal fun torchLevel(state: PhaseState, maxLevel: Int): Int =
    // a hold continues the phase before it: lit through hold-in, dark through hold-out
    if (maxLevel <= 1) {
        if (state.phase == Phase.INHALE || state.phase == Phase.HOLD_IN) 1 else 0
    } else {
        // rounding over the whole range rather than reserving level 1 for "barely lit": a
        // finished exhale has to leave you in the dark, not at the dimmest setting
        (maxLevel * state.openness.coerceIn(0f, 1f)).roundToInt()
    }

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
