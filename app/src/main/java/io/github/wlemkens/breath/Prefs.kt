package io.github.wlemkens.breath

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class SoundMode(val label: String) {
    /** Continuous waves for the whole phase, cutoff riding the breath. */
    WAVES("Waves"),

    /** One sound at the moment the phase ends. */
    MARKER("Marker"),
    SILENT("Silent"),
}

/** The built-in marker sounds, used when the user hasn't pointed a phase at their own mp3. */
enum class MarkerTone(val label: String) {
    BELL("Bell"),

    /** Pitched by phase — see [markerHz]. */
    GONG("Gong"),
}

/** What the breath cue on the session screen looks like. */
enum class CueStyle(val label: String) {
    /** Points packed into a core at the bottom of the breath, opening into a sphere at the top. */
    CLOUD("Cloud"),

    /** The plain gradient sphere. */
    GLOW("Glow"),
}

@Serializable
data class PhaseSound(
    val mode: SoundMode = SoundMode.WAVES,
    /** content:// URI of the user's own mp3. Null falls back to a synthesized [tone]. */
    val markerUri: String? = null,
    val tone: MarkerTone = MarkerTone.BELL,
)

@Serializable
data class Preset(
    val name: String = "Coherence 5.5",
    val inhaleMs: Int = 5500,
    val holdInMs: Int = 0,
    val exhaleMs: Int = 5500,
    val holdOutMs: Int = 0,
    val inhaleSound: PhaseSound = PhaseSound(),
    val holdInSound: PhaseSound = PhaseSound(),
    val exhaleSound: PhaseSound = PhaseSound(),
    val holdOutSound: PhaseSound = PhaseSound(),
) {
    fun soundOf(p: Phase) = when (p) {
        Phase.INHALE -> inhaleSound
        Phase.HOLD_IN -> holdInSound
        Phase.EXHALE -> exhaleSound
        Phase.HOLD_OUT -> holdOutSound
    }

    fun withSound(p: Phase, f: (PhaseSound) -> PhaseSound) = when (p) {
        Phase.INHALE -> copy(inhaleSound = f(inhaleSound))
        Phase.HOLD_IN -> copy(holdInSound = f(holdInSound))
        Phase.EXHALE -> copy(exhaleSound = f(exhaleSound))
        Phase.HOLD_OUT -> copy(holdOutSound = f(holdOutSound))
    }

    fun withDuration(p: Phase, ms: Int) = when (p) {
        Phase.INHALE -> copy(inhaleMs = ms)
        Phase.HOLD_IN -> copy(holdInMs = ms)
        Phase.EXHALE -> copy(exhaleMs = ms)
        Phase.HOLD_OUT -> copy(holdOutMs = ms)
    }

    /** Falls back to the default rather than throwing on a stored preset that adds up to zero. */
    val timing: Timing
        get() = runCatching { Timing(inhaleMs, holdInMs, exhaleMs, holdOutMs) }.getOrElse { Timing() }
}

@Serializable
data class Config(
    val presets: List<Preset> = DEFAULT_PRESETS,
    val activeIndex: Int = 0,
    val limitIsCycles: Boolean = false,
    val durationMs: Long = 5 * 60_000L,
    val cycles: Int = 20,
    val vibrate: Boolean = false,
    /** The torch brightens with the inhale and fades with the exhale. */
    val flashlight: Boolean = false,
    val muteNotifications: Boolean = false,
    /**
     * A bowl at the end of the session. On by default: the whole point of a timed meditation
     * is not having to check whether it is over, and with your eyes shut a silent ending
     * leaves you sitting there wondering.
     */
    val endSound: Boolean = true,
    /** The three progress readouts, each independently hideable for a barer screen. */
    val showTime: Boolean = true,
    val showDots: Boolean = true,
    val showBreaths: Boolean = true,
    val cue: CueStyle = CueStyle.CLOUD,
    /** Opaque ARGB. The bright end of the cue, and the colour of the breath dots. */
    val cueColor: Int = DEFAULT_CUE_COLOR,
) {
    val active: Preset get() = presets[activeIndex]

    val limit: Limit
        get() = if (limitIsCycles) Limit.Cycles(cycles) else Limit.Duration(durationMs)

    /** Keeps [active] and [activeIndex] safe to use unguarded everywhere else. */
    fun sane(): Config {
        // a stored index means nothing once the list it referred to is gone
        if (presets.isEmpty()) return copy(presets = DEFAULT_PRESETS, activeIndex = 0)
        return copy(activeIndex = activeIndex.coerceIn(0, presets.size - 1))
    }
}

/** The teal the app shipped with. */
const val DEFAULT_CUE_COLOR: Int = 0xFF5FD6C8.toInt()

private val DEFAULT_PRESETS = listOf(
    Preset("Coherence 5.5", 5500, 0, 5500, 0),
    Preset("4-7-8", 4000, 7000, 8000, 0),
    Preset("Box 4", 4000, 4000, 4000, 4000),
)

private val Context.store by preferencesDataStore("breath")
private val CONFIG = stringPreferencesKey("config")
// coerceInputValues so that retiring an enum constant costs that one field its default rather than
// failing the whole decode — the fallback below is all-or-nothing, and losing every saved preset
// over a renamed cue style is not a trade worth making
private val json = Json {
    ignoreUnknownKeys = true; encodeDefaults = true; coerceInputValues = true
}

/** A config written by an older build must not crash the app — fall back to defaults. */
fun Context.configFlow(): Flow<Config> = store.data.map { prefs ->
    val stored = prefs[CONFIG]?.let { runCatching { json.decodeFromString<Config>(it) }.getOrNull() }
    (stored ?: Config()).sane()
}

suspend fun Context.saveConfig(config: Config) {
    val encoded = json.encodeToString(config.sane())
    store.edit { it[CONFIG] = encoded }
}

/**
 * One sitting, however much of it was actually breathed. The timing is copied in rather than
 * referenced: a preset that is renamed or re-timed later must not rewrite what was breathed.
 * The four phases default to 0 for entries logged before they were recorded — see [pattern].
 */
@Serializable
data class Entry(
    /** Epoch ms at which it was started. Doubles as its identity — see [logSession]. */
    val at: Long,
    val durationMs: Long,
    val preset: String,
    val inhaleMs: Int = 0,
    val holdInMs: Int = 0,
    val exhaleMs: Int = 0,
    val holdOutMs: Int = 0,
    /** Whole breaths finished. The tail of a part-breathed cycle is in [durationMs] only. */
    val cycles: Int = 0,
) {
    /** "5.5 – 5.5" or "4 – 7 – 8", in seconds. Empty for an entry logged before timings were. */
    val pattern: String
        get() = listOf(inhaleMs, holdInMs, exhaleMs, holdOutMs)
            // a zero-length phase is one the breath never had, not one that took no time
            .filter { it > 0 }
            // whole seconds written whole; a decimal separator is the locale's business, so
            // trimming a trailing ".0" off the formatted string is not the way to do it
            .joinToString(" – ") { if (it % 1000 == 0) "${it / 1000}" else "%.1f".format(it / 1000f) }
}

/** What to log for a sitting of [durationMs] on [preset], started at [at]. */
internal fun entryFor(at: Long, durationMs: Long, preset: Preset) = Entry(
    at = at,
    durationMs = durationMs,
    preset = preset.name,
    inhaleMs = preset.inhaleMs,
    holdInMs = preset.holdInMs,
    exhaleMs = preset.exhaleMs,
    holdOutMs = preset.holdOutMs,
    cycles = (durationMs / preset.timing.cycleMs).toInt(),
)

/** Shorter than this was a mistap, not a meditation. */
const val LOGGED_MIN_MS = 20_000L

// ponytail: one JSON blob rewritten per sitting, capped. Years of daily practice fit; move to a
// row store if it ever has to hold tens of thousands.
private const val HISTORY_MAX = 2000

private val HISTORY = stringPreferencesKey("history")

private fun decodeHistory(stored: String?): List<Entry> =
    stored?.let { runCatching { json.decodeFromString<List<Entry>>(it) }.getOrNull() } ?: emptyList()

fun Context.historyFlow(): Flow<List<Entry>> = store.data.map { decodeHistory(it[HISTORY]) }

/**
 * The log with [entry] recorded, or unchanged if the sitting was too short to have been one.
 * Keyed on the start time, so pausing, resuming and finishing a single sitting keeps rewriting
 * one entry rather than logging three.
 */
internal fun List<Entry>.logging(entry: Entry): List<Entry> =
    if (entry.durationMs < LOGGED_MIN_MS) this
    else (filterNot { it.at == entry.at } + entry).takeLast(HISTORY_MAX)

/** Records what was breathed. Every stop calls this, including the ones that logged nothing. */
suspend fun Context.logSession(at: Long, durationMs: Long, preset: Preset) {
    store.edit { prefs ->
        val old = decodeHistory(prefs[HISTORY])
        val new = old.logging(entryFor(at, durationMs, preset))
        // logging returns the list it was given when there was nothing to record; not rewriting
        // the store then keeps a tap on Start and Pause from churning it
        if (new !== old) prefs[HISTORY] = json.encodeToString(new)
    }
}

/** Human-readable name for a picked mp3; the raw URI's last segment is usually a document id. */
fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "sound"
