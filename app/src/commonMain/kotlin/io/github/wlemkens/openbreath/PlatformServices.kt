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
    /** Decode everything the preset points at up front — a phase change must not wait on IO. */
    fun prepare(preset: Preset)

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
    val granted: Boolean
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
    val haptics: Haptics
    val screen: KeepAwake
    val links: Links
    val formats: Formats

    fun session(): SessionServices
}

val LocalPlatform = staticCompositionLocalOf<Platform> {
    error("No Platform — wrap the app in CompositionLocalProvider(LocalPlatform provides …)")
}
