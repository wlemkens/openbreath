package io.github.wlemkens.openbreath

import android.app.Activity
import android.view.WindowManager

/**
 * Android's answers to commonMain/PlatformServices.kt.
 *
 * Every one of these is a delegation: WaveSynth, PhaseMarkers, DndGuard and Torch are unchanged
 * and still hold all of the behaviour. Nothing here decides anything, which is what keeps this
 * layer cheap to read and the port honest — if a screen stops working after moving to commonMain,
 * the cause is in the screen, not in a reimplementation hiding here.
 */
class AndroidPlatform(private val activity: Activity) : Platform {

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

        override fun openRating() = activity.openRating()
    }

    override fun session(): SessionServices = AndroidSession(activity)
}

private class AndroidSession(context: Activity) : SessionServices {
    private val wave = WaveSynth()
    private val phaseMarkers = PhaseMarkers(context)
    private val dnd = DndGuard(context)
    private val flashlight = Torch(context)

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
        override fun prepare(preset: Preset) = phaseMarkers.prepare(preset)
        override fun soundIdOf(phase: Phase, preset: Preset): Any =
            phaseMarkers.soundIdOf(phase, preset)
        override fun play(phase: Phase, preset: Preset) = phaseMarkers.play(phase, preset)
        override fun playSessionEnd() = phaseMarkers.playSessionEnd()
        override fun stopSessionEnd() = phaseMarkers.stopSessionEnd()
    }

    override val focus = object : FocusGuard {
        override val granted: Boolean get() = dnd.granted
        override fun silence() = dnd.silence()
        override fun restore() = dnd.restore()
    }

    override val torch = object : TorchLight {
        override val available: Boolean get() = flashlight.available
        override fun follow(state: PhaseState) = flashlight.follow(state)
        override fun off() = flashlight.off()
    }

    /** Exactly what the session screen's onDispose used to do, in the order it did it. */
    override fun release() {
        wave.stop()
        phaseMarkers.release()
        dnd.restore()
        flashlight.off()
    }
}
