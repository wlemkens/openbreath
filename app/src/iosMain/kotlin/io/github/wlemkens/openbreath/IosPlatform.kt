package io.github.wlemkens.openbreath

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

/**
 * iOS's answers to commonMain/PlatformServices.kt.
 *
 * All of it is real now except the two that cannot be: do-not-disturb, which iOS has no public
 * API for at all, and the rating link, which needs an App Store id this app has not been given
 * yet. Both answer false rather than pretending, which is the signal for a screen to hide the
 * setting instead of offering one that does nothing.
 *
 * What is still owed is inside the sound: the two recorded bowls and a file of the reader's own
 * — see IosMarkers for why those are silent rather than approximated with the bell.
 */
class IosPlatform : Platform {

    override val haptics = IosHaptics()

    override val screen = object : KeepAwake {
        override fun keepAwake(on: Boolean) {
            UIApplication.sharedApplication.idleTimerDisabled = on
        }
    }

    override val links = object : Links {
        override fun openFeedback() = open(FEEDBACK_FORM)

        override fun openPayPal(euros: Int?) = open(payPalUrl(euros))

        /**
         * Nothing yet, and it cannot be otherwise: an App Store review link needs the numeric app
         * id Apple assigns at first submission, and this app has never been submitted. Wiring a
         * guessed URL would open a 404. The menu item wants hiding on iOS until the id exists.
         */
        override val canRate = false

        override fun openRating() = Unit
    }

    override val formats = IosFormats()

    override fun session(): SessionServices = IosSession()

    private fun open(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
    }
}

/**
 * A session that breathes. The waves are real and shared with Android to the constant; the
 * struck markers are still owed.
 */
private class IosSession : SessionServices {

    private val wave = IosWaveSynth()
    private val struck = IosMarkers()

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
        override fun prepare(preset: Preset) = struck.prepare(preset)

        /**
         * Still has to answer honestly: the screen uses this to collapse two phases that end on
         * the same instant into one sound, and a constant here would be a correct answer for the
         * wrong reason. The identity is the same one Android computes, minus the playing.
         */
        override fun soundIdOf(phase: Phase, preset: Preset): Any =
            preset.soundOf(phase).let { it.markerUri ?: (it.tone to markerRate(it.tone, phase)) }

        override fun play(phase: Phase, preset: Preset) = struck.play(phase, preset)

        // both are recordings rather than syntheses — see IosMarkers for why they are silent
        // rather than approximated with the bell
        override fun playSessionEnd() = Unit
        override fun stopSessionEnd() = Unit
    }

    /** No public API sets a Focus on iOS, so this is not a gap to be filled later — see CLAUDE.md. */
    override val focus = object : FocusGuard {
        override val granted = false
        override fun silence() = Unit
        override fun restore() = Unit
    }

    override val torch = IosTorch()

    override fun release() {
        wave.release()
        struck.release()
        torch.off()
    }
}
