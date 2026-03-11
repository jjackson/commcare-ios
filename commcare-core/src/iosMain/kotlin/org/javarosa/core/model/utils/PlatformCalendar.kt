package org.javarosa.core.model.utils

import platform.Foundation.*

actual fun platformExtractFields(date: PlatformDate, timezoneId: String?): IntArray {
    val nsDate = NSDate(timeIntervalSince1970 = date.getTime() / 1000.0)
    val calendar = NSCalendar(NSCalendarIdentifierGregorian)
    if (timezoneId != null) {
        calendar.timeZone = NSTimeZone.timeZoneWithName(timezoneId) ?: NSTimeZone.defaultTimeZone
    }
    val components = calendar.components(
        NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay or
        NSCalendarUnitHour or NSCalendarUnitMinute or NSCalendarUnitSecond or
        NSCalendarUnitNanosecond or NSCalendarUnitWeekday,
        fromDate = nsDate
    )
    val tzOffset = (calendar.timeZone.secondsFromGMTForDate(nsDate) * 1000).toInt()
    // NSCalendar weekday: 1=Sunday, 7=Saturday (same as Java)
    return intArrayOf(
        components.year.toInt(),
        components.month.toInt(),
        components.day.toInt(),
        components.hour.toInt(),
        components.minute.toInt(),
        components.second.toInt(),
        (components.nanosecond / 1_000_000).toInt(),
        components.weekday.toInt(),
        tzOffset
    )
}

actual fun platformCreateDate(
    year: Int, month: Int, day: Int,
    hour: Int, minute: Int, second: Int, millis: Int,
    timezoneId: String?
): PlatformDate {
    val calendar = NSCalendar(NSCalendarIdentifierGregorian)
    if (timezoneId != null) {
        calendar.timeZone = NSTimeZone.timeZoneWithName(timezoneId) ?: NSTimeZone.defaultTimeZone
    }
    val components = NSDateComponents()
    components.year = year.toLong()
    components.month = month.toLong()
    components.day = day.toLong()
    components.hour = hour.toLong()
    components.minute = minute.toLong()
    components.second = second.toLong()
    components.nanosecond = (millis * 1_000_000).toLong()
    val nsDate = calendar.dateFromComponents(components) ?: NSDate()
    return PlatformDate((nsDate.timeIntervalSince1970 * 1000).toLong())
}

actual fun platformGetDefaultTimezoneOffsetMs(
    era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millisOfDay: Int
): Int {
    val calendar = NSCalendar(NSCalendarIdentifierGregorian)
    val components = NSDateComponents()
    components.year = year.toLong()
    components.month = (month + 1).toLong() // Java Calendar month is 0-based
    components.day = day.toLong()
    val nsDate = calendar.dateFromComponents(components) ?: NSDate()
    return (NSTimeZone.defaultTimeZone.secondsFromGMTForDate(nsDate) * 1000).toInt()
}

actual fun platformGetTimezoneOffsetMinutes(date: PlatformDate): Int {
    val nsDate = NSDate(timeIntervalSince1970 = date.getTime() / 1000.0)
    return -(NSTimeZone.defaultTimeZone.secondsFromGMTForDate(nsDate) / 60).toInt()
}

actual fun platformFormatMsAsISO8601(ms: Long): String {
    if (ms == 0L) return ""
    val formatter = NSDateFormatter()
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm'Z'"
    formatter.timeZone = NSTimeZone.timeZoneWithName("UTC")
    val nsDate = NSDate(timeIntervalSince1970 = ms / 1000.0)
    return formatter.stringFromDate(nsDate)
}

actual fun platformGetDayOfWeek(date: PlatformDate): Int {
    val nsDate = NSDate(timeIntervalSince1970 = date.getTime() / 1000.0)
    val calendar = NSCalendar(NSCalendarIdentifierGregorian)
    val components = calendar.components(NSCalendarUnitWeekday, fromDate = nsDate)
    return components.weekday.toInt()
}

actual fun platformLocalize(key: String, args: Array<String>): String {
    // On iOS, return a simple fallback until full localization is wired up
    return if (args.isEmpty()) key else "$key: ${args.joinToString(", ")}"
}
