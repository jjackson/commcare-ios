@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import platform.Foundation.*

actual class PlatformCalendar(
    private val calendar: NSCalendar,
    private var components: NSDateComponents,
    private var _timeZone: PlatformTimeZone
) {
    actual var time: PlatformDate
        get() {
            val nsDate = calendar.dateFromComponents(components) ?: NSDate()
            return PlatformDate((nsDate.timeIntervalSince1970 * 1000).toLong())
        }
        set(value) {
            val nsDate = NSDate.dateWithTimeIntervalSince1970(value.getTime() / 1000.0)
            calendar.timeZone = _timeZone.nsTimeZone
            components = calendar.components(
                NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
                        NSCalendarUnitNanosecond or NSCalendarUnitWeekday,
                nsDate
            )
        }

    actual var timeZone: PlatformTimeZone
        get() = _timeZone
        set(value) {
            _timeZone = value
            calendar.timeZone = value.nsTimeZone
            // Re-extract components in new timezone
            val nsDate = calendar.dateFromComponents(components) ?: NSDate()
            components = calendar.components(
                NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
                        NSCalendarUnitNanosecond or NSCalendarUnitWeekday,
                nsDate
            )
        }

    actual fun get(field: Int): Int {
        return when (field) {
            0 -> components.year.toInt()        // YEAR
            1 -> (components.month - 1).toInt() // MONTH (0-based)
            2 -> components.day.toInt()          // DAY_OF_MONTH
            3 -> components.hour.toInt()         // HOUR_OF_DAY
            4 -> components.minute.toInt()       // MINUTE
            5 -> components.second.toInt()       // SECOND
            6 -> ((components.nanosecond / 1_000_000).toInt()) // MILLISECOND
            7 -> components.weekday.toInt()      // DAY_OF_WEEK (1=Sunday)
            else -> 0
        }
    }

    actual fun set(field: Int, value: Int) {
        when (field) {
            0 -> components.year = value.toLong()
            1 -> components.month = (value + 1).toLong() // Convert 0-based to 1-based
            2 -> components.day = value.toLong()
            3 -> components.hour = value.toLong()
            4 -> components.minute = value.toLong()
            5 -> components.second = value.toLong()
            6 -> components.nanosecond = (value * 1_000_000).toLong()
            7 -> components.weekday = value.toLong()
        }
    }

    actual fun add(field: Int, amount: Int) {
        val addComponents = NSDateComponents()
        when (field) {
            0 -> addComponents.year = amount.toLong()
            1 -> addComponents.month = amount.toLong()
            2 -> addComponents.day = amount.toLong()
            3 -> addComponents.hour = amount.toLong()
            4 -> addComponents.minute = amount.toLong()
            5 -> addComponents.second = amount.toLong()
            6 -> {
                // Add milliseconds as nanoseconds
                val currentNs = components.nanosecond
                val totalMs = (currentNs / 1_000_000) + amount
                addComponents.second = (totalMs / 1000).toLong()
                val remainingMs = totalMs % 1000
                components.nanosecond = (remainingMs * 1_000_000)
            }
        }
        val nsDate = calendar.dateFromComponents(components) ?: return
        val newDate = calendar.dateByAddingComponents(addComponents, nsDate, 0u) ?: return
        components = calendar.components(
            NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                    NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
                    NSCalendarUnitNanosecond or NSCalendarUnitWeekday,
            newDate
        )
    }
}

actual fun platformCalendarInstance(): PlatformCalendar {
    val cal = NSCalendar.currentCalendar
    val tz = platformDefaultTimeZone()
    cal.timeZone = tz.nsTimeZone
    val now = NSDate()
    val components = cal.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
                NSCalendarUnitNanosecond or NSCalendarUnitWeekday,
        now
    )
    return PlatformCalendar(cal, components, tz)
}

actual fun platformCalendarInstance(tz: PlatformTimeZone): PlatformCalendar {
    val cal = NSCalendar.currentCalendar
    cal.timeZone = tz.nsTimeZone
    val now = NSDate()
    val components = cal.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
                NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
                NSCalendarUnitNanosecond or NSCalendarUnitWeekday,
        now
    )
    return PlatformCalendar(cal, components, tz)
}

// Field constants (must match JVM Calendar values for cross-platform compatibility)
actual val CALENDAR_YEAR: Int = 0
actual val CALENDAR_MONTH: Int = 1
actual val CALENDAR_DAY_OF_MONTH: Int = 2
actual val CALENDAR_HOUR_OF_DAY: Int = 3
actual val CALENDAR_MINUTE: Int = 4
actual val CALENDAR_SECOND: Int = 5
actual val CALENDAR_MILLISECOND: Int = 6
actual val CALENDAR_DAY_OF_WEEK: Int = 7

// Month constants (0-based to match JVM Calendar)
actual val MONTH_JANUARY: Int = 0
actual val MONTH_FEBRUARY: Int = 1
actual val MONTH_APRIL: Int = 3
actual val MONTH_JUNE: Int = 5
actual val MONTH_SEPTEMBER: Int = 8
actual val MONTH_NOVEMBER: Int = 10

// Day-of-week constants (1-based, Sunday=1 to match JVM Calendar)
actual val DAY_SUNDAY: Int = 1
actual val DAY_MONDAY: Int = 2
actual val DAY_TUESDAY: Int = 3
actual val DAY_WEDNESDAY: Int = 4
actual val DAY_THURSDAY: Int = 5
actual val DAY_FRIDAY: Int = 6
actual val DAY_SATURDAY: Int = 7
