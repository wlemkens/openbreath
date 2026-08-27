package io.github.wlemkens.openbreath

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.datetime.DayOfWeek
import okio.Path.Companion.toPath
import java.io.File
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * What the desktop alone can answer — the mirror of androidMain's Prefs.kt and iosMain's
 * Prefs.ios.kt. The stored shape is in commonMain/Model.kt and every read and write over it is in
 * commonMain/Store.kt; all that is here is where the file lives and the two questions only a
 * platform knows the answer to.
 */

/**
 * Where the log, the presets and the goals live, and **the only place in desktopMain that asks
 * which operating system this is.**
 *
 * Each of the three is the convention its own users expect and its own backup tools look in:
 * `%APPDATA%` on Windows, Application Support on macOS, and the XDG data directory on Linux.
 * Roaming rather than Local on Windows deliberately, and Application Support rather than Caches
 * on macOS: a year of sittings is exactly the kind of thing that should follow a user to a new
 * machine, which is the same trade `android:allowBackup="true"` and iOS's Documents already make.
 *
 * Lowercase on Linux alone because that is the convention there, and it matches the `.deb`'s
 * package name.
 */
internal val appDir: File by lazy {
    val home = System.getProperty("user.home") ?: "."
    val os = System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT)
    val dir = when {
        os.contains("win") ->
            File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "OpenBreath")
        os.contains("mac") || os.contains("darwin") ->
            File(home, "Library/Application Support/OpenBreath")
        else ->
            File(System.getenv("XDG_DATA_HOME") ?: "$home/.local/share", "openbreath")
    }
    dir.also { it.mkdirs() }
}

/**
 * One instance, because DataStore refuses a second one over the same file. [Store] is handed this
 * and knows nothing about it, exactly as on the other two platforms.
 *
 * The `.preferences_pb` extension is DataStore's own insistence; the stem matches Android's
 * `preferencesDataStore("breath")` so that anyone comparing the two files finds the same name.
 */
private val rawStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath { File(appDir, "breath.preferences_pb").path.toPath() }
}

fun store(): Store = Store(rawStore)

/**
 * The default locale, not a fixed one: a Belgian reader gets "2,5" here exactly as they do on
 * Android, where `"%.1f".format` also takes the default. Pinning this to Locale.US would make the
 * desktop the one platform that punctuates numbers differently from the phone beside it.
 */
actual fun formatOneDecimal(value: Float): String = "%.1f".format(value)

/**
 * Identical to Android's, because both are the JVM asking the same question of the same library.
 * Copied rather than shared through a `jvmMain` hierarchy: three functions is less code than the
 * source-set graph that would let two targets see one of them.
 */
actual fun firstDayOfWeek(): DayOfWeek =
    // java.time's DayOfWeek and kotlinx-datetime's are two enums that happen to agree: both run
    // Monday..Sunday, so the ISO number carries across. Not a cast — 0.8 stopped aliasing them.
    DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1]
