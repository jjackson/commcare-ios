package org.javarosa.core.model.utils

import platform.Foundation.*

actual fun platformFormatDate(millis: Long, pattern: String, timezoneName: String?): String {
    val formatter = NSDateFormatter()
    formatter.dateFormat = convertJavaPatternToNS(pattern)
    formatter.locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    if (timezoneName != null) {
        formatter.timeZone = NSTimeZone.timeZoneWithName(timezoneName) ?: NSTimeZone.localTimeZone
    }
    val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
    return formatter.stringFromDate(date)
}

actual fun platformFormatPlatformDate(date: PlatformDate, pattern: String): String {
    return platformFormatDate(date.getTime(), pattern)
}

actual fun platformParseDate(dateString: String, pattern: String): PlatformDate? {
    val formatter = NSDateFormatter()
    formatter.dateFormat = convertJavaPatternToNS(pattern)
    formatter.locale = NSLocale.localeWithLocaleIdentifier("en_US_POSIX")
    val nsDate = formatter.dateFromString(dateString) ?: return null
    return PlatformDate((nsDate.timeIntervalSince1970 * 1000).toLong())
}

/** Convert common Java SimpleDateFormat patterns to NSDateFormatter patterns */
private fun convertJavaPatternToNS(javaPattern: String): String {
    // Most patterns are the same between Java and NSDateFormatter
    // Key differences: Java uses 'y' for year, NSDateFormatter also uses 'y'
    // Java 'EEE' = short day name, same in NSDateFormatter
    // Java uses single-quoted literals, NSDateFormatter also uses single quotes
    return javaPattern
}
