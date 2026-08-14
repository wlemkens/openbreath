package io.github.wlemkens.openbreath

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Every read and write of the practice log, the settings, the goals and the reminders.
 *
 * These were `Context` extensions, which is why they were Android's alone — DataStore itself
 * has been multiplatform since 1.1 and the keys and JSON were never platform-specific. Taking
 * the [DataStore] as a constructor argument rather than reaching for a `Context` is the whole
 * of what it took to move them; each platform decides where the file lives and nothing else
 * differs.
 *
 * The file it opens is not this class's business, and deliberately so: on Android it is still
 * the one the `preferencesDataStore("breath")` delegate has always opened, at the same path,
 * holding the same bytes. A `Store` that picked its own path would be a fresh empty log on
 * every phone that updated.
 */
class Store(private val data: DataStore<Preferences>) {

    /** A config written by an older build must not crash the app — fall back to defaults. */
    fun configFlow(): Flow<Config> = data.data.map { prefs ->
        val stored = prefs[CONFIG]?.let { runCatching { json.decodeFromString<Config>(it) }.getOrNull() }
        (stored ?: Config()).sane()
    }

    suspend fun saveConfig(config: Config) {
        val encoded = json.encodeToString(config.sane())
        data.edit { it[CONFIG] = encoded }
    }

    fun historyFlow(): Flow<List<Entry>> = data.data.map { decodeHistory(it[HISTORY]) }

    /** Records what was breathed. Every stop calls this, including the ones that logged nothing. */
    suspend fun logSession(at: Long, durationMs: Long, preset: Preset) {
        data.edit { prefs ->
            val old = decodeHistory(prefs[HISTORY])
            val new = old.logging(entryFor(at, durationMs, preset))
            // logging returns the list it was given when there was nothing to record; not rewriting
            // the store then keeps a tap on Start and Pause from churning it
            if (new !== old) prefs[HISTORY] = json.encodeToString(new)
        }
    }

    fun goalsFlow(): Flow<List<Goal>> =
        data.data.map { decodeGoals(it[GOALS]).map { g -> g.sane() } }

    suspend fun saveGoals(goals: List<Goal>) {
        val encoded = json.encodeToString(goals.map { it.sane() })
        data.edit { it[GOALS] = encoded }
    }

    fun celebratedFlow(): Flow<Int> = data.data.map { it[CELEBRATED] ?: 0 }

    suspend fun saveCelebrated(days: Int) {
        data.edit { it[CELEBRATED] = days }
    }

    fun remindersFlow(): Flow<List<Reminder>> = data.data.map { decodeReminders(it[REMINDERS]) }

    suspend fun saveReminders(reminders: List<Reminder>) {
        val encoded = json.encodeToString(reminders)
        data.edit { it[REMINDERS] = encoded }
    }

    private companion object {
        // these five strings are the on-disk schema. Renaming one loses whatever it named.
        val CONFIG = stringPreferencesKey("config")
        val HISTORY = stringPreferencesKey("history")

        // its own key rather than a field of Config: goals and settings are edited on different
        // screens, and a whole-Config write from one would roll back what the other had just saved
        val GOALS = stringPreferencesKey("goals")

        /** The longest milestone already shown, so a run is celebrated once rather than every launch. */
        val CELEBRATED = intPreferencesKey("celebrated")
        val REMINDERS = stringPreferencesKey("reminders")
    }
}

/**
 * The one [Store], reached from anywhere in the tree. It stands where `LocalContext` used to:
 * the screens never wanted a Context, only the thing it could open.
 */
val LocalStore = staticCompositionLocalOf<Store> {
    error("No Store — wrap the app in CompositionLocalProvider(LocalStore provides …)")
}
