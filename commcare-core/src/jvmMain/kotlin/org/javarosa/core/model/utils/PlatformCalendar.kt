package org.javarosa.core.model.utils

import org.javarosa.core.services.locale.Localization
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

actual fun platformExtractFields(date: PlatformDate, timezoneId: String?): IntArray {
    val cal = Calendar.getInstance()
    if (timezoneId != null) {
        cal.timeZone = TimeZone.getTimeZone(timezoneId)
    }
    cal.time = date
    val tzOffset = cal.timeZone.getOffset(date.time)
    return intArrayOf(
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH) + 1, // Convert 0-based to 1-based
        cal.get(Calendar.DAY_OF_MONTH),
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        cal.get(Calendar.SECOND),
        cal.get(Calendar.MILLISECOND),
        cal.get(Calendar.DAY_OF_WEEK), // 1=Sun, 7=Sat
        tzOffset
    )
}

actual fun platformCreateDate(
    year: Int, month: Int, day: Int,
    hour: Int, minute: Int, second: Int, millis: Int,
    timezoneId: String?
): PlatformDate {
    val cal = Calendar.getInstance()
    if (timezoneId != null) {
        cal.timeZone = TimeZone.getTimeZone(timezoneId)
    }
    cal.set(Calendar.YEAR, year)
    cal.set(Calendar.MONTH, month - 1) // Convert 1-based to 0-based
    cal.set(Calendar.DAY_OF_MONTH, day)
    cal.set(Calendar.HOUR_OF_DAY, hour)
    cal.set(Calendar.MINUTE, minute)
    cal.set(Calendar.SECOND, second)
    cal.set(Calendar.MILLISECOND, millis)
    return cal.time
}

actual fun platformGetDefaultTimezoneOffsetMs(
    era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millisOfDay: Int
): Int {
    return TimeZone.getDefault().getOffset(era, year, month, day, dayOfWeek, millisOfDay)
}

@Suppress("DEPRECATION")
actual fun platformGetTimezoneOffsetMinutes(date: PlatformDate): Int {
    return date.timezoneOffset
}

actual fun platformFormatMsAsISO8601(ms: Long): String {
    if (ms == 0L) return ""
    val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
    df.timeZone = TimeZone.getTimeZone("UTC")
    return df.format(ms)
}

actual fun platformGetDayOfWeek(date: PlatformDate): Int {
    val cal = Calendar.getInstance()
    cal.time = date
    return cal.get(Calendar.DAY_OF_WEEK)
}

actual fun platformLocalize(key: String, args: Array<String>): String {
    return Localization.get(key, args)
}
