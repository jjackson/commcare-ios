@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import java.util.Calendar

actual class PlatformCalendar(val javaCal: Calendar) {
    actual var time: PlatformDate
        get() = javaCal.time
        set(value) { javaCal.time = value }

    actual var timeZone: PlatformTimeZone
        get() = PlatformTimeZone(javaCal.timeZone)
        set(value) { javaCal.timeZone = value.javaTimeZone }

    actual fun get(field: Int): Int = javaCal.get(field)
    actual fun set(field: Int, value: Int) = javaCal.set(field, value)
    actual fun add(field: Int, amount: Int) = javaCal.add(field, amount)
}

actual fun platformCalendarInstance(): PlatformCalendar = PlatformCalendar(Calendar.getInstance())
actual fun platformCalendarInstance(tz: PlatformTimeZone): PlatformCalendar =
    PlatformCalendar(Calendar.getInstance(tz.javaTimeZone))

// Field constants
actual val CALENDAR_YEAR: Int = Calendar.YEAR
actual val CALENDAR_MONTH: Int = Calendar.MONTH
actual val CALENDAR_DAY_OF_MONTH: Int = Calendar.DAY_OF_MONTH
actual val CALENDAR_HOUR_OF_DAY: Int = Calendar.HOUR_OF_DAY
actual val CALENDAR_MINUTE: Int = Calendar.MINUTE
actual val CALENDAR_SECOND: Int = Calendar.SECOND
actual val CALENDAR_MILLISECOND: Int = Calendar.MILLISECOND
actual val CALENDAR_DAY_OF_WEEK: Int = Calendar.DAY_OF_WEEK

// Month constants
actual val MONTH_JANUARY: Int = Calendar.JANUARY
actual val MONTH_FEBRUARY: Int = Calendar.FEBRUARY
actual val MONTH_APRIL: Int = Calendar.APRIL
actual val MONTH_JUNE: Int = Calendar.JUNE
actual val MONTH_SEPTEMBER: Int = Calendar.SEPTEMBER
actual val MONTH_NOVEMBER: Int = Calendar.NOVEMBER

// Day-of-week constants
actual val DAY_SUNDAY: Int = Calendar.SUNDAY
actual val DAY_MONDAY: Int = Calendar.MONDAY
actual val DAY_TUESDAY: Int = Calendar.TUESDAY
actual val DAY_WEDNESDAY: Int = Calendar.WEDNESDAY
actual val DAY_THURSDAY: Int = Calendar.THURSDAY
actual val DAY_FRIDAY: Int = Calendar.FRIDAY
actual val DAY_SATURDAY: Int = Calendar.SATURDAY
