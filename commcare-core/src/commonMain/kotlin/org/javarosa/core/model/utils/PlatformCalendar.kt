@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform calendar representation for date field extraction and construction.
 * On JVM: typealiased to java.util.Calendar.
 * On iOS: wraps NSCalendar/NSDateComponents.
 */
expect class PlatformCalendar {
    var time: PlatformDate
    var timeZone: PlatformTimeZone
    fun get(field: Int): Int
    fun set(field: Int, value: Int)
    fun add(field: Int, amount: Int)
}

expect fun platformCalendarInstance(): PlatformCalendar
expect fun platformCalendarInstance(tz: PlatformTimeZone): PlatformCalendar

// Field constants
expect val CALENDAR_YEAR: Int
expect val CALENDAR_MONTH: Int
expect val CALENDAR_DAY_OF_MONTH: Int
expect val CALENDAR_HOUR_OF_DAY: Int
expect val CALENDAR_MINUTE: Int
expect val CALENDAR_SECOND: Int
expect val CALENDAR_MILLISECOND: Int
expect val CALENDAR_DAY_OF_WEEK: Int

// Month constants (0-based on JVM: January=0)
expect val MONTH_JANUARY: Int
expect val MONTH_FEBRUARY: Int
expect val MONTH_APRIL: Int
expect val MONTH_JUNE: Int
expect val MONTH_SEPTEMBER: Int
expect val MONTH_NOVEMBER: Int

// Day-of-week constants (1-based on JVM: Sunday=1)
expect val DAY_SUNDAY: Int
expect val DAY_MONDAY: Int
expect val DAY_TUESDAY: Int
expect val DAY_WEDNESDAY: Int
expect val DAY_THURSDAY: Int
expect val DAY_FRIDAY: Int
expect val DAY_SATURDAY: Int
