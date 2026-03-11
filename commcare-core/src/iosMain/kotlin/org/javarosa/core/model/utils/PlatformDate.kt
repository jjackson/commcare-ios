@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of PlatformDate wrapping epoch milliseconds.
 */
actual open class PlatformDate {
    private var _time: Long

    actual constructor() {
        _time = (NSDate().timeIntervalSince1970 * 1000).toLong()
    }

    actual constructor(time: Long) {
        _time = time
    }

    actual fun getTime(): Long = _time
    actual fun setTime(time: Long) { _time = time }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlatformDate) return false
        return _time == other._time
    }

    override fun hashCode(): Int = _time.hashCode()

    override fun toString(): String = "PlatformDate(time=$_time)"
}
