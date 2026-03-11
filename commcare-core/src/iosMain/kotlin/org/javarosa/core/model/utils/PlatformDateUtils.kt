@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSTimeZone
import platform.Foundation.timeIntervalSince1970

actual object PlatformDateUtils {
    private const val MS_PER_DAY = 86400000.0

    actual fun fractionalDaysSinceEpoch(d: PlatformDate): Double {
        return d.getTime() / MS_PER_DAY
    }

    actual fun daysSinceEpoch(d: PlatformDate): Int {
        return (roundDate(d).getTime() / MS_PER_DAY.toLong()).toInt()
    }

    actual fun formatDate(d: PlatformDate, format: Int): String {
        val formatter = NSDateFormatter()
        formatter.timeZone = NSTimeZone.systemTimeZone
        when (format) {
            FORMAT_ISO8601 -> formatter.dateFormat = "yyyy-MM-dd"
            else -> formatter.dateFormat = "yyyy-MM-dd"
        }
        val nsDate = NSDate(timeIntervalSinceReferenceDate = (d.getTime() / 1000.0) - 978307200.0)
        return formatter.stringFromDate(nsDate)
    }

    actual fun dateAdd(d: PlatformDate, days: Int): PlatformDate {
        return PlatformDate(d.getTime() + days * MS_PER_DAY.toLong())
    }

    actual fun getDate(year: Int, month: Int, day: Int): PlatformDate? {
        val cal = NSCalendar.currentCalendar
        val components = platform.Foundation.NSDateComponents()
        components.year = year.toLong()
        components.month = month.toLong()
        components.day = day.toLong()
        components.hour = 0
        components.minute = 0
        components.second = 0
        val nsDate = cal.dateFromComponents(components) ?: return null
        return PlatformDate((nsDate.timeIntervalSince1970 * 1000).toLong())
    }

    actual fun parseDateTime(s: String): PlatformDate? {
        val patterns = listOf("yyyy-MM-dd", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS")
        val formatter = NSDateFormatter()
        formatter.timeZone = NSTimeZone.systemTimeZone
        for (pattern in patterns) {
            formatter.dateFormat = pattern
            val nsDate = formatter.dateFromString(s)
            if (nsDate != null) {
                return PlatformDate((nsDate.timeIntervalSince1970 * 1000).toLong())
            }
        }
        return null
    }

    actual fun roundDate(d: PlatformDate): PlatformDate {
        val msPerDay = MS_PER_DAY.toLong()
        val time = d.getTime()
        val rounded = (time / msPerDay) * msPerDay
        return PlatformDate(rounded)
    }

    actual val FORMAT_ISO8601: Int = 1
}
