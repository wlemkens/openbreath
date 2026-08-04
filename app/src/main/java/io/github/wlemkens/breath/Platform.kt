package io.github.wlemkens.breath

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings

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
class DndGuard(private val context: Context) {
    private var previous: Int? = null

    private val nm: NotificationManager?
        get() = context.getSystemService(NotificationManager::class.java)

    val granted: Boolean get() = nm?.isNotificationPolicyAccessGranted == true

    fun silence() {
        val manager = nm ?: return
        if (!manager.isNotificationPolicyAccessGranted || previous != null) return
        previous = manager.currentInterruptionFilter
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
