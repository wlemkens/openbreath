package io.github.wlemkens.openbreath

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterFullStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeZoneForSecondsFromGMT

/**
 * The two things whose shape is the reader's own setting rather than the app's: whether a clock
 * says 14:00 or 2 PM, and whether a date puts the day or the month first.
 *
 * `NSDateFormatter` answers both, because Foundation carries the locale data kotlinx-datetime
 * deliberately does not — the same division that makes [firstDayOfWeek] an expect.
 *
 * Built once each: NSDateFormatter is famously expensive to construct and both are called per row
 * of the log — the time on every sitting, the date on every heading.
 */
class IosFormats : Formats {

    /**
     * Fixed to UTC, which is what lets [clockTime] hand it a bare number of seconds. The reader's
     * own zone would shift 08:00 by whatever they are offset by and print the wrong hour.
     */
    private val timeOfDay = NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
        timeZone = NSTimeZone.timeZoneForSecondsFromGMT(0)
    }

    private val wholeDate = NSDateFormatter().apply {
        dateStyle = NSDateFormatterFullStyle
        timeStyle = NSDateFormatterNoStyle
    }

    /**
     * A formatter wants a whole instant even to print two fields of it, so this is that time of
     * day on the epoch itself — 1970-01-01 plus the hours and minutes, read back in UTC. The date
     * half is thrown away by the No-style above, and only the 12-or-24-hour choice survives, which
     * is the entire question being asked.
     */
    override fun clockTime(hour: Int, minute: Int): String {
        val secondsIntoTheDay = hour * 3600.0 + minute * 60.0
        return timeOfDay.stringFromDate(NSDate.dateWithTimeIntervalSince1970(secondsIntoTheDay))
    }

    override fun dayLabel(date: LocalDate): String {
        val midnight = date.atStartOfDayIn(TimeZone.currentSystemDefault())
        val asNSDate = NSDate.dateWithTimeIntervalSince1970(midnight.epochSeconds.toDouble())
        return wholeDate.stringFromDate(asNSDate)
    }
}
