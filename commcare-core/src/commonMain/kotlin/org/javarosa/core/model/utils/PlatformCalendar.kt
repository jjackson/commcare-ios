@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Platform-specific calendar operations for date field extraction and construction.
 * On JVM, delegates to java.util.Calendar.
 * On iOS, delegates to NSCalendar.
 */

/**
 * Extract date/time fields from a PlatformDate.
 *
 * @param date The date to extract fields from
 * @param timezoneId Optional timezone ID (e.g., "UTC", "America/New_York"). If null, uses default timezone.
 * @return Array of [year, month(1-12), day(1-31), hour(0-23), minute(0-59), second(0-59), millis(0-999), dow(1-7 Sun=1), tzOffsetMs]
 */
expect fun platformExtractFields(date: PlatformDate, timezoneId: String?): IntArray

/**
 * Construct a PlatformDate from date/time fields.
 *
 * @param year Year
 * @param month Month (1-12)
 * @param day Day of month (1-31)
 * @param hour Hour (0-23)
 * @param minute Minute (0-59)
 * @param second Second (0-59)
 * @param millis Millisecond (0-999)
 * @param timezoneId Optional timezone ID. If null, uses default timezone.
 * @return PlatformDate representing the specified date/time
 */
expect fun platformCreateDate(
    year: Int, month: Int, day: Int,
    hour: Int, minute: Int, second: Int, millis: Int,
    timezoneId: String?
): PlatformDate

/**
 * Get the default timezone offset in milliseconds for the given date parameters.
 *
 * @param era 1 for CE
 * @param year Year
 * @param month Month (0-11, Java Calendar convention)
 * @param day Day of month
 * @param dayOfWeek Day of week (1-7, Sun=1)
 * @param millisOfDay Milliseconds since midnight
 * @return Offset in milliseconds from UTC
 */
expect fun platformGetDefaultTimezoneOffsetMs(
    era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millisOfDay: Int
): Int

/**
 * Get the deprecated timezoneOffset property (minutes behind UTC) from a date.
 * On JVM this calls Date.getTimezoneOffset().
 * On iOS this calculates from the current timezone.
 */
expect fun platformGetTimezoneOffsetMinutes(date: PlatformDate): Int

/**
 * Format milliseconds since epoch as ISO 8601 string (yyyy-MM-dd'T'HH:mm'Z').
 * Returns empty string if ms is 0.
 */
expect fun platformFormatMsAsISO8601(ms: Long): String

/**
 * Get the day of week for a given date.
 * @return 1=Sunday, 2=Monday, ... 7=Saturday
 */
expect fun platformGetDayOfWeek(date: PlatformDate): Int

/**
 * Get a localized string by key, with optional arguments.
 * On JVM, delegates to Localization.get().
 * On iOS, returns the key as fallback.
 */
expect fun platformLocalize(key: String, args: Array<String> = emptyArray()): String
