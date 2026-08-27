package io.github.wlemkens.openbreath

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.awt.Desktop
import java.awt.Frame
import java.net.URI
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The desktop's answers to commonMain/PlatformServices.kt.
 *
 * Every one of these is a delegation, exactly as androidMain/AndroidPlatform.kt is: nothing here
 * decides anything, which is what keeps the layer cheap to read and what makes it obvious when a
 * screen misbehaves that the cause is in the screen.
 *
 * Four of them answer false, and each is a fact about a desktop rather than something owed:
 * there is no vibrator, no flashlight, no do-not-disturb that a program may set, and no store page
 * to rate. Each false hides a row instead of showing one that does nothing — see
 * [Haptics.supported], [TorchLight.available], [FocusGuard.supported] and [Links.canRate].
 *
 * Reminders is the one genuinely missing feature, and it is missing on iOS too. The navigator is
 * given no reminders screen, so the menu leaves the item out.
 */
class DesktopPlatform(private val owner: Frame?) : Platform {

    /**
     * The generated version and the commit it was built from — see generateBuildInfo. The sha and
     * not a build number, because a desktop build has no store record to look one up in: what
     * identifies the copy someone is running is the commit it came from.
     */
    override val version: String = "$BUILD_VERSION ($BUILD_SHA)"

    override val haptics = object : Haptics {
        /** No vibrator and no Taptic Engine. The row is left out of Settings rather than shown. */
        override val supported = false
        override fun buzz(ms: Long) = Unit
    }

    /**
     * ponytail: nothing. The JVM has no API for keeping a display awake, so a screensaver may
     * arrive mid-sitting — the sound keeps going and the cue is behind the lock screen.
     *
     * The available fixes are all worse than the problem for now: `java.awt.Robot` nudging the
     * pointer by zero pixels every half minute is the usual trick and it takes over the pointer;
     * anything honest means JNA and three per-OS calls (SetThreadExecutionState, IOPMAssertion,
     * org.freedesktop.ScreenSaver). Worth doing the day someone actually reports it.
     */
    override val screen = object : KeepAwake {
        override fun keepAwake(on: Boolean) = Unit
    }

    override val links = object : Links {
        override fun openFeedback() = open(FEEDBACK_FORM)

        override fun openPayPal(euros: Int?) = open(payPalUrl(euros))

        /**
         * There is no store page to open. The app is installed from a `.msi`, a `.dmg` or a `.deb`,
         * none of which has a review to leave, so the menu item is left out rather than pointed at
         * something arbitrary.
         */
        override val canRate = false

        override fun openRating() = Unit
    }

    override val formats = DesktopFormats()

    override val files = DesktopFiles(owner)

    override val focus = object : FocusGuard {
        /**
         * No. Windows has Focus Assist, macOS has Focus and both Linux desktops have their own
         * idea, and none of the three exposes an API a program may set — the same answer iOS gives,
         * for the same reason. False means the setting is left out entirely, which is the honest
         * sentence: not "you have not allowed this" but "this cannot exist here".
         */
        override val supported = false
        override val granted = false
        override fun requestAccess() = Unit
        override fun silence() = Unit
        override fun restore() = Unit
    }

    override val torch = object : TorchLight {
        /** A laptop has a camera and no flashlight, which is not an error. */
        override val available = false
        override fun follow(state: PhaseState) = Unit
        override fun off() = Unit
    }

    override fun session(): SessionServices = DesktopSession(focus, torch)

    /** A machine with no browser registered is not a crash worth taking a meditation down for. */
    private fun open(url: String) {
        runCatching {
            val desktop = Desktop.getDesktop()
            if (desktop.isSupported(Desktop.Action.BROWSE)) desktop.browse(URI(url))
        }
    }
}

/**
 * The two things whose shape is the reader's own setting rather than the app's: whether a clock says
 * 14:00 or 2 PM, and whether a date puts the day or the month first.
 *
 * `java.time.format` carries the locale data kotlinx-datetime deliberately does not, which is the
 * same division that makes [firstDayOfWeek] an expect. Identical in substance to Android's, which
 * asks the same library the same question — the difference is only that Android needs a Context to
 * read the 12-or-24-hour *setting*, which it keeps separately from the locale, and the desktop has
 * no such setting to read.
 *
 * Built once each: a DateTimeFormatter is not free to construct and both are called per row of the
 * log — the time on every sitting, the date on every heading.
 */
class DesktopFormats : Formats {

    private val timeOfDay: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

    private val wholeDate: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)

    override fun clockTime(hour: Int, minute: Int): String =
        timeOfDay.format(LocalTime.of(hour, minute))

    override fun dayLabel(date: LocalDate): String = wholeDate.format(date.toJavaLocalDate())
}

/**
 * A session that breathes. The waves and every marker are shared with the phones to the constant;
 * what is here is where the samples go.
 */
private class DesktopSession(
    override val focus: FocusGuard,
    override val torch: TorchLight,
) : SessionServices {

    /**
     * One stream for the bed and the markers both, which on this platform they have to share — see
     * [DesktopAudio]. That is why it is built here: [SessionServices] already exists to group the
     * things that share a lifetime, and these two now share rather more than that.
     */
    private val audio = DesktopAudio()
    private val struck = DesktopMarkers(audio)

    override val synth = object : Synth {
        override var openness: Float
            get() = audio.openness
            set(value) { audio.openness = value }
        override var wavesGain: Float
            get() = audio.wavesGain
            set(value) { audio.wavesGain = value }
        override var soundwaveGain: Float
            get() = audio.soundwaveGain
            set(value) { audio.soundwaveGain = value }

        override fun start() = audio.startWaves()
        override fun stop() = audio.stopWaves()
    }

    override val markers = object : Markers {
        override suspend fun prepare(preset: Preset) = struck.prepare(preset)

        override fun soundIdOf(phase: Phase, preset: Preset): Any = struck.soundIdOf(phase, preset)

        override fun play(phase: Phase, preset: Preset) = struck.play(phase, preset)

        override fun playSessionEnd() = struck.playSessionEnd()
        override fun stopSessionEnd() = struck.stopSessionEnd()
    }

    override fun release() {
        struck.release()
        // last, and it joins the audio thread: the markers still hold samples it may be mixing
        audio.release()
        // focus and torch are no-ops here, but released anyway: a SessionServices that skips them
        // because this platform happens not to need them is one line from being wrong elsewhere
        focus.restore()
        torch.off()
    }
}
