package io.github.wlemkens.openbreath

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS's answers to commonMain/PlatformServices.kt.
 *
 * Two of these are real and the rest are honest silence. Keeping the app awake and opening a link
 * are a line each and there is no reason to defer them; sound, haptics and the torch are whole
 * subsystems and are owed by later work. A no-op here is deliberate and says so — the alternative,
 * leaving the interface unimplemented, is an app that cannot be run at all, and a breathing app
 * that runs silently is still a breathing app.
 *
 * Nothing here pretends to a capability it lacks: [FocusGuard.granted] and [TorchLight.available]
 * both answer false, which is the signal for a screen to hide the setting rather than offer one
 * that does nothing.
 */
class IosPlatform : Platform {

    /** Owed by the haptics work: CHHapticEngine, where Android has Vibrator. */
    override val haptics = object : Haptics {
        override fun buzz(ms: Long) = Unit
    }

    override val screen = object : KeepAwake {
        override fun keepAwake(on: Boolean) {
            UIApplication.sharedApplication.idleTimerDisabled = on
        }
    }

    override val links = object : Links {
        override fun openFeedback() = open(FEEDBACK_FORM)

        /**
         * Nothing yet, and it cannot be otherwise: an App Store review link needs the numeric app
         * id Apple assigns at first submission, and this app has never been submitted. Wiring a
         * guessed URL would open a 404. The menu item wants hiding on iOS until the id exists.
         */
        override val canRate = false

        override fun openRating() = Unit
    }

    override fun session(): SessionServices = SilentSession()

    private fun open(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
    }
}

/**
 * A session that breathes without a sound. The cue animates, the phases turn, the sitting is
 * logged — everything the engine does is platform-free already. What is missing is only the
 * playing of it, which is owed by the audio work.
 */
private class SilentSession : SessionServices {

    override val synth = object : Synth {
        // written every frame and read by nobody yet; kept rather than discarded so that the
        // screen driving them is exercised exactly as it will be once there is an engine behind
        override var openness: Float = 0f
        override var wavesGain: Float = 0f
        override var soundwaveGain: Float = 0f

        override fun start() = Unit
        override fun stop() = Unit
    }

    override val markers = object : Markers {
        override fun prepare(preset: Preset) = Unit

        /**
         * Still has to answer honestly: the screen uses this to collapse two phases that end on
         * the same instant into one sound, and a constant here would be a correct answer for the
         * wrong reason. The identity is the same one Android computes, minus the playing.
         */
        override fun soundIdOf(phase: Phase, preset: Preset): Any =
            preset.soundOf(phase).let { it.markerUri ?: (it.tone to markerRate(it.tone, phase)) }

        override fun play(phase: Phase, preset: Preset) = Unit
        override fun playSessionEnd() = Unit
        override fun stopSessionEnd() = Unit
    }

    /** No public API sets a Focus on iOS, so this is not a gap to be filled later — see CLAUDE.md. */
    override val focus = object : FocusGuard {
        override val granted = false
        override fun silence() = Unit
        override fun restore() = Unit
    }

    /** Owed by the torch work: AVCaptureDevice, where Android has CameraManager. */
    override val torch = object : TorchLight {
        override val available = false
        override fun follow(state: PhaseState) = Unit
        override fun off() = Unit
    }

    override fun release() = Unit
}
