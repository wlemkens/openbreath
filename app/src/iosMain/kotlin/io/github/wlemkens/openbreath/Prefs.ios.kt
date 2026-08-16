package io.github.wlemkens.openbreath

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.datetime.DayOfWeek
import okio.Path.Companion.toPath
import platform.Foundation.NSCalendar
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.NSNumberFormatterRoundHalfUp
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * What iOS alone can answer — the mirror of androidMain's Prefs.kt. The stored shape is in
 * commonMain/Model.kt and the reads and writes over it are in commonMain/Store.kt; all that is
 * here is where the file lives and the two questions only a platform knows the answer to.
 */

/**
 * Documents rather than Application Support, and deliberately: Documents is included in the
 * device backup, so a practice log survives moving to a new iPhone. That is the same trade
 * `android:allowBackup="true"` makes on the other platform — carrying a year of sittings to a
 * new handset was judged worth a restore putting back an older copy, and the answer should not
 * differ by platform.
 *
 * One instance, because DataStore refuses a second one over the same file. [Store] is handed
 * this and knows nothing about it, exactly as on Android.
 */
private val rawStore: DataStore<Preferences> by lazy {
    PreferenceDataStoreFactory.createWithPath {
        val documents = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .first() as NSURL
        val dir = requireNotNull(documents.path) { "no Documents directory to store the log in" }
        // DataStore insists on this extension; the name matches Android's preferencesDataStore("breath")
        "$dir/breath.preferences_pb".toPath()
    }
}

fun store(): Store = Store(rawStore)

/**
 * Built once: NSNumberFormatter is famously expensive to construct and this is called from
 * composition. A reader who changes region mid-session sees the old separator until the next
 * launch, which is the same staleness every other iOS app has here.
 *
 * The three settings are all there to match what `"%.1f".format(value)` gives on Android:
 * exactly one fraction digit, no grouping separator (`%.1f` does not group — `%,.1f` would),
 * and half-up rounding, where Foundation would otherwise round half to even and disagree with
 * Android on every .x5.
 */
private val oneDecimal: NSNumberFormatter by lazy {
    NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        // ULong, not UInt: these are NSUInteger, which Kotlin/Native maps to ULong on 64-bit
        minimumFractionDigits = 1uL
        maximumFractionDigits = 1uL
        usesGroupingSeparator = false
        roundingMode = NSNumberFormatterRoundHalfUp
    }
}

actual fun formatOneDecimal(value: Float): String =
    oneDecimal.stringFromNumber(NSNumber(float = value)) ?: value.toString()

/**
 * iOS keeps the week's first day in the calendar, where a Belgian gets Monday and an American
 * Sunday, and where the reader can also override it independently of language.
 *
 * `firstWeekday` counts 1 = Sunday … 7 = Saturday. kotlinx-datetime's DayOfWeek runs
 * MONDAY..SUNDAY, so the two disagree on both origin and order — hence the shift rather than
 * the cast that would work if they happened to line up.
 */
actual fun firstDayOfWeek(): DayOfWeek =
    DayOfWeek.entries[((NSCalendar.currentCalendar.firstWeekday.toInt() + 5) % 7)]
