package io.github.wlemkens.openbreath

import platform.Foundation.NSBundle
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
 * What is still owed inside the sound is a file of the reader's own — see IosMarkers for why it
 * is silent rather than approximated with the bell.
 */
/** Assigned by Apple when the app record was created, and stable for the life of the app. */
private const val APP_STORE_ID = "6805899911"

/** Whether the App Store page exists yet. See [Links.canRate]; flip on release day. */
private const val LISTING_IS_LIVE = false

class IosPlatform : Platform {

    /**
     * CFBundleShortVersionString and CFBundleVersion, which CI stamps from the run number so a
     * build installed through SideStore can be named exactly.
     */
    override val version: String = run {
        fun key(k: String) = NSBundle.mainBundle.objectForInfoDictionaryKey(k) as? String
        val name = key("CFBundleShortVersionString") ?: "?"
        val build = key("CFBundleVersion") ?: "?"
        "$name ($build)"
    }

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
         * The id exists — Apple assigned it when the app record was created, well before any
         * submission — so the only thing still missing is the listing itself. An App Store page
         * is a 404 until the app is released, and a menu item that opens one is worse than no
         * menu item, so this stays off until there is something to open.
         *
         * On release day this is the whole change: [LISTING_IS_LIVE] to true.
         */
        override val canRate = LISTING_IS_LIVE

        override fun openRating() = open("https://apps.apple.com/app/id$APP_STORE_ID")
    }

    override val formats = IosFormats()

    override val files = IosFiles()

    /** One each, built here and handed to every session — see [SessionServices.focus]. */
    override val focus = object : FocusGuard {
        override val supported = false
        override val granted = false
        override fun requestAccess() = Unit
        override fun silence() = Unit
        override fun restore() = Unit
    }

    override val torch = IosTorch()

    override fun session(): SessionServices = IosSession(focus, torch)

    private fun open(url: String) {
        val target = NSURL.URLWithString(url) ?: return
        UIApplication.sharedApplication.openURL(target, emptyMap<Any?, Any>(), null)
    }
}

/**
 * A session that breathes. The waves are real and shared with Android to the constant; the
 * struck markers are still owed.
 */
private class IosSession(
    override val focus: FocusGuard,
    override val torch: TorchLight,
) : SessionServices {

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
        override suspend fun prepare(preset: Preset) = struck.prepare(preset)

        /**
         * Still has to answer honestly: the screen uses this to collapse two phases that end on
         * the same instant into one sound, and a constant here would be a correct answer for the
         * wrong reason. The identity is the same one Android computes, minus the playing.
         */
        override fun soundIdOf(phase: Phase, preset: Preset): Any =
            preset.soundOf(phase).let { it.markerUri ?: (it.tone to markerRate(it.tone, phase)) }

        override fun play(phase: Phase, preset: Preset) = struck.play(phase, preset)

        override fun playSessionEnd() = struck.playSessionEnd()
        override fun stopSessionEnd() = struck.stopSessionEnd()
    }

    override fun release() {
        wave.release()
        struck.release()
        torch.off()
    }
}
