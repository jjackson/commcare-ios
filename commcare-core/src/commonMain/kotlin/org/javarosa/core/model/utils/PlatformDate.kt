@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform date representation backed by milliseconds since epoch.
 * On JVM, typealiased to java.util.Date for perfect backward compatibility.
 * On iOS, a simple wrapper around millis.
 */
expect class PlatformDate() {
    constructor(date: Long)
    fun getTime(): Long
    fun setTime(time: Long)
}

/**
 * Returns the timezone offset in minutes for the given date.
 * Positive values are west of UTC, negative are east.
 * (Matches the deprecated java.util.Date.getTimezoneOffset() behavior)
 */
expect fun PlatformDate.getTimezoneOffset(): Int
