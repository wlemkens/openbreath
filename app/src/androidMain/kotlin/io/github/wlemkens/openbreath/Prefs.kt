package io.github.wlemkens.openbreath

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.datetime.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * What Android alone can answer. The stored shape is in commonMain/Model.kt and the reads and
 * writes over it are in commonMain/Store.kt; all that is left here is where the file lives and
 * two questions only a platform knows the answer to.
 */

/**
 * The same file, at the same path, that every released version has written to. The delegate is
 * what fixes that path — `filesDir/datastore/breath.preferences_pb` — so it stays rather than
 * being replaced by a hand-built path that would have to match it exactly or lose the log.
 */
private val Context.rawStore by preferencesDataStore("breath")

fun Context.store(): Store = Store(rawStore)

actual fun formatOneDecimal(value: Float): String = "%.1f".format(value)

/**
 * Android keeps the week's first day in the locale, where a Belgian gets Monday and an
 * American Sunday — not in a setting of its own, so the locale is the whole answer here.
 */
actual fun firstDayOfWeek(): DayOfWeek =
    // java.time's DayOfWeek and kotlinx-datetime's are two enums that happen to agree: both run
    // Monday..Sunday, so the ISO number carries across. Not a cast — 0.8 stopped aliasing them.
    DayOfWeek.entries[WeekFields.of(Locale.getDefault()).firstDayOfWeek.value - 1]

/** Human-readable name for a picked mp3; the raw URI's last segment is usually a document id. */
fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: "sound"
