package org.javarosa.core.model.utils

/**
 * Cross-platform date formatting/parsing (replaces SimpleDateFormat).
 * On JVM: delegates to java.text.SimpleDateFormat.
 * On iOS: uses NSDateFormatter.
 */

/** Format epoch millis using the given pattern and optional timezone */
expect fun platformFormatDate(millis: Long, pattern: String, timezoneName: String? = null): String

/** Format a PlatformDate using the given pattern */
expect fun platformFormatPlatformDate(date: PlatformDate, pattern: String): String

/** Parse a date string using the given pattern. Returns null if unparseable. */
expect fun platformParseDate(dateString: String, pattern: String): PlatformDate?
