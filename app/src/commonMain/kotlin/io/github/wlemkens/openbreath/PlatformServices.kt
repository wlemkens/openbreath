package io.github.wlemkens.openbreath

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.datetime.LocalDate

/**
 * What a breathing session needs from the machine it runs on.
 *
 * These stand where `LocalContext` used to, for the same reason [LocalStore] does: the session
 * never wanted an Android Context, it wanted a sound, a buzz and a torch. Naming each of those
 * is what lets the screen that drives them move to commonMain, and what lets a test drive one
 * silently.
 *
 * Interfaces rather than `expect class` deliberately. An expect class fixes one implementation
 * per platform at compile time, which is right for a value like [firstDayOfWeek] and wrong for a
 * thing you might want to substitute — the screens are worth testing without waking a speaker.
 *
 * The file is not called Platform.kt because androidMain already has one in this package, and
 * two same-named files with top-level declarations compile to the same JVM facade class.
 */

/**
 * The continuous soundscape: waves ashore, pitched by the breath.
 *
 * The three levels are written every frame from composition and read by whatever is filling the
 * audio buffer, which on both platforms is another thread. Implementations are responsible for
 * making that safe; the caller only ever assigns.
 */
interface Synth {
    /** 0 = fully exhaled, 1 = fully inhaled. */
    var openness: Float

    /** Level of each voice, already multiplied by the phase's edge fade. */
    var wavesGain: Float
    var soundwaveGain: Float

    fun start()
    fun stop()
}

/** The sound struck at the end of a phase, and the one that ends a whole session. */
interface Markers {
    /**
     * Decode everything the preset points at up front — a phase change must not wait on IO.
     *
     * Suspends because the bundled bowls are read through the resource loader, which does. It is
     * called from the session's own coroutine at start, seconds before the earliest boundary it
     * could be needed for, so the suspension costs nothing anyone can hear.
     */
    suspend fun prepare(preset: Preset)

    /**
     * Identity of the sound a phase would play, so that two phases ending on the same instant —
     * which is what a zero-length hold means — sound once rather than twice.
     */
    fun soundIdOf(phase: Phase, preset: Preset): Any

    fun play(phase: Phase, preset: Preset)
    fun playSessionEnd()

    /** Cuts the end bowl short, so starting again does not play over its tail. */
    fun stopSessionEnd()
}

/**
 * Silencing notifications for the length of a sitting. Android's do-not-disturb; on iOS there is
 * no public API that sets a Focus, so the implementation there does nothing and [granted] is
 * false, which is the signal for the setting to be hidden rather than shown and broken.
 */
interface FocusGuard {
    /**
     * Whether this platform has the idea at all. False on iOS, where no public API sets a Focus,
     * and the setting is then left out of the screen entirely rather than shown as un-grantable —
     * "you have not allowed this" and "this cannot exist here" are different sentences and only
     * one of them is worth printing.
     */
    val supported: Boolean

    /** Whether the reader has allowed it. Always false where [supported] is false. */
    val granted: Boolean

    /** Send them to wherever the permission is given. Never called unless [supported]. */
    fun requestAccess()

    fun silence()

    /** Safe when never silenced, and idempotent — a phone left in DND is unforgivable. */
    fun restore()
}

/** The flashlight, brightening with the inhale. */
interface TorchLight {
    /** False on a device without one, which is not an error. */
    val available: Boolean

    /** Called every frame; implementations quantise so the camera service is not hammered. */
    fun follow(state: PhaseState)

    /** Safe when never lit, and idempotent — a torch left burning outlives the app that lit it. */
    fun off()
}

/** A short nudge at a phase boundary, long enough to feel with the screen off. */
interface Haptics {
    /**
     * Whether this machine can be felt at all. False on the desktop, which has no vibrator and no
     * Taptic Engine, and the setting is then left out of the screen — the same sentence
     * [FocusGuard.supported] and [TorchLight.available] answer, for the same reason.
     */
    val supported: Boolean

    fun buzz(ms: Long = 45L)
}

/** Holding the screen on, because a breath you are following is not idle time. */
interface KeepAwake {
    fun keepAwake(on: Boolean)
}

/**
 * The handful of things whose formatting is the reader's own setting rather than their locale.
 *
 * An interface and not an `expect fun` because Android cannot answer without a Context: whether
 * this phone writes 14:00 or 2:00 PM is a system setting, and `DateFormat.getTimeFormat` wants
 * the Context to read it from. kotlinx-datetime has no opinion here and shouldn't — it carries
 * no locale data at all, which is the same reason [firstDayOfWeek] is an expect.
 *
 * `uses24Hour` belongs here too and joins with the reminders port, which needs it for a picker.
 */
interface Formats {
    /** A time of day written the way this phone writes them. */
    fun clockTime(hour: Int, minute: Int): String

    /**
     * A whole date, spelled out — "Thursday, 13 August 2026". Localised rather than a pattern of
     * our own: which of the day, month and year comes first is not ours to decide, and a log is
     * read at a glance or not at all.
     */
    fun dayLabel(date: LocalDate): String
}

/**
 * Reaching the reader's own files, for the backup that leaves the phone and the mp3 that comes
 * onto it.
 *
 * Callbacks rather than suspend functions because both platforms are genuinely asynchronous in a
 * way a coroutine would only paper over: each hands the choosing to a system picker and hears
 * back through the machinery it registered with, long after the call returned.
 *
 * A null result means nothing happened — the reader cancelled, or the file could not be read.
 * Those two are not told apart, deliberately: a file the system has just handed over and then
 * refuses to open is rare enough that a second error message for it would be noise, and the
 * screen already says the useful thing when a file opens but is not a backup.
 */
interface Files {
    /** Ask where to put [text] and write it. [onDone] is false when it did not land. */
    fun exportText(suggestedName: String, text: String, onDone: (Boolean) -> Unit)

    /** Ask for a file and read it as text. */
    fun importText(onResult: (String?) -> Unit)

    /**
     * False where choosing your own sound is not implemented yet, which leaves the button out
     * rather than offering one that does nothing — the same rule [FocusGuard.granted] follows.
     */
    val canPickAudio: Boolean

    /** Ask for an audio file. The string is the opaque handle stored in [PhaseSound.markerUri]. */
    fun pickAudio(onPicked: (String?) -> Unit)

    /** Something a reader would recognise for a handle from [pickAudio]. */
    fun audioName(handle: String): String
}

/**
 * Sending the reader somewhere outside the app. Opening a link is not a crash risk worth taking a
 * meditation down for, so implementations swallow the case of a device with nothing to open it.
 *
 * The PayPal link is here now that the Support screen has moved. Whatever else changes about it,
 * the rule it lives under does not: nothing is ever given in return for a payment.
 */
internal const val FEEDBACK_FORM =
    "https://docs.google.com/forms/d/e/1FAIpQLSfiiYcjxAfzvYQXIEQfyxwErtbEDVqjxbdaPAsvSuZpGgaFDA/viewform"

internal const val PAYPAL_ME = "https://paypal.me/wimlemkens"

/** PayPal, with the amount already filled in when one was chosen. */
internal fun payPalUrl(euros: Int?) = if (euros == null) PAYPAL_ME else "$PAYPAL_ME/${euros}EUR"

interface Links {
    /** The feedback form, in a browser. The app itself sends nothing. */
    fun openFeedback()

    /**
     * PayPal, in a browser. A browser and not a screen of our own on either platform, and on iOS
     * that is the rule rather than a preference: App Review 3.2.2(iv) allows funds to be collected
     * for a cause only outside the app, "such as via Safari or SMS". Opening a URL is exactly that.
     *
     * Nothing is charged here and no amount is remembered — the app opens a page and steps out of
     * the way, and it is never told whether anything was sent. That is what keeps this a tip and
     * not a purchase, on both stores. See the monetisation note in CLAUDE.md.
     */
    fun openPayPal(euros: Int?)

    /**
     * False where there is no store page to open, which is the honest answer before an app has
     * ever been submitted and been given an id. The menu leaves the item out when it is.
     */
    val canRate: Boolean

    /** The app's own store page, where the rating is. */
    fun openRating()
}

/**
 * The pieces a running session drives, grouped because they share one lifetime: created with the
 * screen, released with it. That grouping is the whole point — every one of them leaves something
 * switched on if it is dropped without a word, and a single [release] is far harder to forget
 * than four separate teardowns.
 */
interface SessionServices {
    val synth: Synth
    val markers: Markers

    /**
     * The very same objects [Platform.focus] and [Platform.torch] hand out, not copies. A session
     * drives them and the settings screen only asks them questions, but there is one torch on the
     * phone and one do-not-disturb state, and two objects believing they own either is how one
     * gets left switched on.
     */
    val focus: FocusGuard
    val torch: TorchLight

    /** Stops the sound, puts notifications back, and puts the torch out. */
    fun release()
}

/**
 * Everything platform-shaped, reached from anywhere in the tree exactly as [LocalStore] is.
 *
 * [haptics] and [screen] live as long as the app; a session's own pieces come from [session] and
 * are released with the screen that asked for them.
 */
interface Platform {
    /**
     * What this build calls itself — "1.0.41 (41)", the marketing version and the build number.
     *
     * Shown under Advanced so it can be quoted in a bug report. A report that names a version is
     * the difference between fixing something and asking which one they had, and the person
     * reporting it cannot be expected to know where a store hides it.
     */
    val version: String

    val haptics: Haptics
    val screen: KeepAwake
    val links: Links
    val formats: Formats
    val files: Files

    /**
     * Reachable without starting a session, because the settings screen has to ask whether this
     * device has a torch and whether this platform has do-not-disturb at all — and building a
     * session to find out would start an audio engine to answer a question about a flashlight.
     */
    val focus: FocusGuard
    val torch: TorchLight

    fun session(): SessionServices
}

val LocalPlatform = staticCompositionLocalOf<Platform> {
    error("No Platform — wrap the app in CompositionLocalProvider(LocalPlatform provides …)")
}
