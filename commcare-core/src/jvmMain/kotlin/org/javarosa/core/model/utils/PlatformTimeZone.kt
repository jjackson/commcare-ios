@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

import java.util.TimeZone

actual class PlatformTimeZone(val javaTimeZone: TimeZone) {
    actual fun getOffset(date: Long): Int = javaTimeZone.getOffset(date)
    actual fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millisOfDay: Int): Int =
        javaTimeZone.getOffset(era, year, month, day, dayOfWeek, millisOfDay)
}

actual fun platformDefaultTimeZone(): PlatformTimeZone = PlatformTimeZone(TimeZone.getDefault())
actual fun platformTimeZone(id: String): PlatformTimeZone = PlatformTimeZone(TimeZone.getTimeZone(id))
