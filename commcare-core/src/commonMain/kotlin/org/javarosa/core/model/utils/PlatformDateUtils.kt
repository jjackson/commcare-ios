@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform date utility functions needed by the XPath engine.
 *
 * On JVM: delegates to the full DateUtils implementation.
 * On iOS: provides native date implementations.
 */
expect object PlatformDateUtils {
    /** Fractional days since Unix epoch */
    fun fractionalDaysSinceEpoch(d: PlatformDate): Double

    /** Integer days since Unix epoch */
    fun daysSinceEpoch(d: PlatformDate): Int

    /** Format a date according to the specified format constant */
    fun formatDate(d: PlatformDate, format: Int): String

    /** Add days to a date */
    fun dateAdd(d: PlatformDate, days: Int): PlatformDate

    /** Create a date from year/month/day */
    fun getDate(year: Int, month: Int, day: Int): PlatformDate?

    /** Parse an ISO 8601 date/datetime string, return null if unparseable */
    fun parseDateTime(s: String): PlatformDate?

    /** Round a date to midnight */
    fun roundDate(d: PlatformDate): PlatformDate

    /** ISO 8601 format constant */
    val FORMAT_ISO8601: Int
}
