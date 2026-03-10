@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual open class PlatformDate {
    var time: Long

    actual constructor() {
        time = (NSDate().timeIntervalSince1970 * 1000).toLong()
    }

    actual constructor(time: Long) {
        this.time = time
    }

    actual fun getTime(): Long = time

    actual fun setTime(time: Long) {
        this.time = time
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PlatformDate) return false
        return time == other.time
    }

    override fun hashCode(): Int = time.hashCode()

    override fun toString(): String = "PlatformDate(time=$time)"
}
