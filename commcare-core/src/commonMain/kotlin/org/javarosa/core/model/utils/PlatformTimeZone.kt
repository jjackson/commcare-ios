@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform timezone representation.
 * On JVM: typealiased to java.util.TimeZone.
 * On iOS: wraps NSTimeZone.
 */
expect class PlatformTimeZone {
    fun getOffset(date: Long): Int
    fun getOffset(era: Int, year: Int, month: Int, day: Int, dayOfWeek: Int, millisOfDay: Int): Int
}

expect fun platformDefaultTimeZone(): PlatformTimeZone
expect fun platformTimeZone(id: String): PlatformTimeZone
