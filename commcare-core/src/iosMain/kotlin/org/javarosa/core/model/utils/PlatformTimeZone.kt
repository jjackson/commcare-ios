@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import platform.Foundation.*

actual class PlatformTimeZone(val nsTimeZone: NSTimeZone) {
    actual fun getOffset(date: Long): Int {
        val nsDate = platform.Foundation.NSDate.dateWithTimeIntervalSince1970(date / 1000.0)
        return (nsTimeZone.secondsFromGMTForDate(nsDate) * 1000).toInt()
    }

    actual fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millisOfDay: Int): Int {
        // Approximate: construct a date from the components and get offset
        val cal = platform.Foundation.NSCalendar.currentCalendar
        val components = platform.Foundation.NSDateComponents()
        components.year = year.toLong()
        components.month = (month + 1).toLong() // NSCalendar months are 1-based
        components.day = day.toLong()
        val nsDate = cal.dateFromComponents(components) ?: return 0
        return (nsTimeZone.secondsFromGMTForDate(nsDate) * 1000).toInt()
    }
}

actual fun platformDefaultTimeZone(): PlatformTimeZone =
    PlatformTimeZone(NSTimeZone.localTimeZone)

actual fun platformTimeZone(id: String): PlatformTimeZone =
    PlatformTimeZone(NSTimeZone.timeZoneWithName(id) ?: NSTimeZone.localTimeZone)
