@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import platform.Foundation.*

/**
 * iOS implementation of PlatformDate, wrapping milliseconds since epoch.
 */
actual class PlatformDate actual constructor() {
    private var millis: Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

    actual constructor(date: Long) : this() {
        millis = date
    }

    actual fun getTime(): Long = millis
    actual fun setTime(time: Long) { millis = time }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlatformDate) return false
        return millis == other.millis
    }

    override fun hashCode(): Int = millis.hashCode()

    override fun toString(): String = "PlatformDate(millis=$millis)"
}

actual fun PlatformDate.getTimezoneOffset(): Int {
    val seconds = this.getTime() / 1000.0
    val date = NSDate.dateWithTimeIntervalSince1970(seconds)
    val tz = NSTimeZone.localTimeZone
    // NSTimeZone.secondsFromGMTForDate returns seconds east of GMT
    // Java's getTimezoneOffset returns minutes WEST of UTC
    return -(tz.secondsFromGMTForDate(date).toInt() / 60)
}
