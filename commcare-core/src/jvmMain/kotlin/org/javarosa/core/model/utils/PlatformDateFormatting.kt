package org.javarosa.core.model.utils

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

actual fun platformFormatDate(millis: Long, pattern: String, timezoneName: String?): String {
    val sdf = SimpleDateFormat(pattern, Locale.US)
    if (timezoneName != null) {
        sdf.timeZone = TimeZone.getTimeZone(timezoneName)
    }
    return sdf.format(java.util.Date(millis))
}

actual fun platformFormatPlatformDate(date: PlatformDate, pattern: String): String {
    return SimpleDateFormat(pattern, Locale.US).format(date)
}

actual fun platformParseDate(dateString: String, pattern: String): PlatformDate? {
    return try {
        SimpleDateFormat(pattern, Locale.US).parse(dateString)
    } catch (e: Exception) {
        null
    }
}
