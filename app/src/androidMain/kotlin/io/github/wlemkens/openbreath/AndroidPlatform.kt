package io.github.wlemkens.openbreath

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Android's answers to commonMain/PlatformServices.kt.
 *
 * Every one of these is a delegation: WaveSynth, PhaseMarkers, DndGuard and Torch are unchanged
 * and still hold all of the behaviour. Nothing here decides anything, which is what keeps this
 * layer cheap to read and the port honest — if a screen stops working after moving to commonMain,
 * the cause is in the screen, not in a reimplementation hiding here.
 */
@Suppress("DEPRECATION") // versionCode: longVersionCode is API 28 and minSdk is 26
private fun Activity.versionLabel(): String = runCatching {
    val info = packageManager.getPackageInfo(packageName, 0)
    val code =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
    "${info.versionName} ($code)"
}.getOrDefault("unknown")

class AndroidPlatform(
    private val activity: Activity,
    override val files: Files,
) : Platform {

    override val version = activity.versionLabel()

    override val haptics = object : Haptics {
        override fun buzz(ms: Long) = activity.buzz(ms)
    }

    /**
     * The window flag rather than `LocalView.keepScreenOn`, so nothing in the tree has to reach
     * for a View — that reach is one of the two things that pinned the session screen to Android.
     */
    override val screen = object : KeepAwake {
        override fun keepAwake(on: Boolean) {
            val flag = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (on) activity.window.addFlags(flag) else activity.window.clearFlags(flag)
        }
    }

    override val links = object : Links {
        // a phone with nothing that opens links is not a crash
        override fun openFeedback() {
            runCatching { activity.startActivity(feedbackIntent()) }
        }

        override fun openPayPal(euros: Int?) {
            runCatching { activity.startActivity(paypalIntent(euros)) }
        }

        override val canRate = true
        override fun openRating() = activity.openRating()
    }

    override val formats = object : Formats {
        override fun clockTime(hour: Int, minute: Int) = activity.clockTime(hour, minute)

        override fun dayLabel(date: LocalDate): String = dayFormat.format(date.toJavaLocalDate())
    }

    // one each, built here and handed to every session: there is one torch on the phone
    private val dnd = DndGuard(activity)
    private val flashlight = Torch(activity)

    override val focus = object : FocusGuard {
        override val supported = true
        override val granted: Boolean get() = dnd.granted
        override fun requestAccess() {
            runCatching { activity.startActivity(notificationPolicyIntent()) }
        }
        override fun silence() = dnd.silence()
        override fun restore() = dnd.restore()
    }

    override val torch = object : TorchLight {
        override val available: Boolean get() = flashlight.available
        override fun follow(state: PhaseState) = flashlight.follow(state)
        override fun off() = flashlight.off()
    }

    override fun session(): SessionServices = AndroidSession(activity, focus, torch)
}

/** Localised rather than a hand-written pattern — see [Formats.dayLabel]. */
private val dayFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

private class AndroidSession(
    context: Activity,
    override val focus: FocusGuard,
    override val torch: TorchLight,
) : SessionServices {
    private val wave = WaveSynth()
    private val phaseMarkers = PhaseMarkers(context)

    override val synth = object : Synth {
        override var openness: Float
            get() = wave.openness
            set(value) { wave.openness = value }
        override var wavesGain: Float
            get() = wave.wavesGain
            set(value) { wave.wavesGain = value }
        override var soundwaveGain: Float
            get() = wave.soundwaveGain
            set(value) { wave.soundwaveGain = value }

        override fun start() = wave.start()
        override fun stop() = wave.stop()
    }

    override val markers = object : Markers {
        override suspend fun prepare(preset: Preset) = phaseMarkers.prepare(preset)
        override fun soundIdOf(phase: Phase, preset: Preset): Any =
            phaseMarkers.soundIdOf(phase, preset)
        override fun play(phase: Phase, preset: Preset) = phaseMarkers.play(phase, preset)
        override fun playSessionEnd() = phaseMarkers.playSessionEnd()
        override fun stopSessionEnd() = phaseMarkers.stopSessionEnd()
    }

    /** Exactly what the session screen's onDispose used to do, in the order it did it. */
    override fun release() {
        wave.stop()
        phaseMarkers.release()
        focus.restore()
        torch.off()
    }
}
