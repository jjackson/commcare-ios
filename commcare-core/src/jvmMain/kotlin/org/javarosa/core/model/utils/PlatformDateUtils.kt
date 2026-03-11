@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

actual object PlatformDateUtils {
    actual fun fractionalDaysSinceEpoch(d: PlatformDate): Double =
        DateUtils.fractionalDaysSinceEpoch(d)

    actual fun daysSinceEpoch(d: PlatformDate): Int =
        DateUtils.daysSinceEpoch(d)

    actual fun formatDate(d: PlatformDate, format: Int): String =
        DateUtils.formatDate(d, format)

    actual fun dateAdd(d: PlatformDate, days: Int): PlatformDate =
        DateUtils.dateAdd(d, days)

    actual fun getDate(year: Int, month: Int, day: Int): PlatformDate? =
        DateUtils.getDate(year, month, day)

    actual fun parseDateTime(s: String): PlatformDate? =
        DateUtils.parseDateTime(s)

    actual fun roundDate(d: PlatformDate): PlatformDate =
        DateUtils.roundDate(d)

    actual val FORMAT_ISO8601: Int = DateUtils.FORMAT_ISO8601
}
