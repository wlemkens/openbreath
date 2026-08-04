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
    val muteNotifications: Boolean = false,
    /**
     * A bowl at the end of the session. On by default: the whole point of a timed meditation
     * is not having to check whether it is over, and with your eyes shut a silent ending
     * leaves you sitting there wondering.
     */
    val endSound: Boolean = true,
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

private val DEFAULT_PRESETS = listOf(
    Preset("Coherence 5.5", 5500, 0, 5500, 0),
    Preset("4-7-8", 4000, 7000, 8000, 0),
    Preset("Box 4", 4000, 4000, 4000, 4000),
)

private val Context.store by preferencesDataStore("breath")
private val CONFIG = stringPreferencesKey("config")
private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/** A config written by an older build must not crash the app — fall back to defaults. */
fun Context.configFlow(): Flow<Config> = store.data.map { prefs ->
    val stored = prefs[CONFIG]?.let { runCatching { json.decodeFromString<Config>(it) }.getOrNull() }
    (stored ?: Config()).sane()
}

suspend fun Context.saveConfig(config: Config) {
    val encoded = json.encodeToString(config.sane())
    store.edit { it[CONFIG] = encoded }
}

/** Human-readable name for a picked mp3; the raw URI's last segment is usually a document id. */
fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "sound"
