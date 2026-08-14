package io.github.wlemkens.openbreath

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Where the stored shape actually meets the disk. The shape itself — every enum, every
 * `@Serializable` class, the Json instance and the decoders — lives in commonMain/Model.kt so
 * that iOS can read the same bytes. What is left here is the two things that still name a
 * platform: DataStore, and the week's first day, which no library will answer for us.
 */

actual fun formatOneDecimal(value: Float): String = "%.1f".format(value)

/**
 * Android keeps the week's first day in the locale, where a Belgian gets Monday and an
 * American Sunday — not in a setting of its own, so the locale is the whole answer here.
 */
actual fun firstDayOfWeek(): DayOfWeek =
    // java.time's DayOfWeek and kotlinx-datetime's are two enums that happen to agree: both run
    // Monday..Sunday, so the ISO number carries across. Not a cast — 0.8 stopped aliasing them.
    DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1]

private val Context.store by preferencesDataStore("breath")
private val CONFIG = stringPreferencesKey("config")

/** A config written by an older build must not crash the app — fall back to defaults. */
fun Context.configFlow(): Flow<Config> = store.data.map { prefs ->
    val stored = prefs[CONFIG]?.let { runCatching { json.decodeFromString<Config>(it) }.getOrNull() }
    (stored ?: Config()).sane()
}

suspend fun Context.saveConfig(config: Config) {
    val encoded = json.encodeToString(config.sane())
    store.edit { it[CONFIG] = encoded }
}

private val HISTORY = stringPreferencesKey("history")

fun Context.historyFlow(): Flow<List<Entry>> = store.data.map { decodeHistory(it[HISTORY]) }

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

// its own key rather than a field of Config: goals and settings are edited on different screens,
// and a whole-Config write from one would roll back what the other had just saved
private val GOALS = stringPreferencesKey("goals")

fun Context.goalsFlow(): Flow<List<Goal>> = store.data.map { decodeGoals(it[GOALS]).map { g -> g.sane() } }

suspend fun Context.saveGoals(goals: List<Goal>) {
    val encoded = json.encodeToString(goals.map { it.sane() })
    store.edit { it[GOALS] = encoded }
}

/** The longest milestone already shown, so a run is celebrated once rather than every launch. */
private val CELEBRATED = intPreferencesKey("celebrated")

fun Context.celebratedFlow(): Flow<Int> = store.data.map { it[CELEBRATED] ?: 0 }

suspend fun Context.saveCelebrated(days: Int) {
    store.edit { it[CELEBRATED] = days }
}

private val REMINDERS = stringPreferencesKey("reminders")

fun Context.remindersFlow(): Flow<List<Reminder>> = store.data.map { decodeReminders(it[REMINDERS]) }

suspend fun Context.saveReminders(reminders: List<Reminder>) {
    val encoded = json.encodeToString(reminders)
    store.edit { it[REMINDERS] = encoded }
}

/** Human-readable name for a picked mp3; the raw URI's last segment is usually a document id. */
fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "sound"
