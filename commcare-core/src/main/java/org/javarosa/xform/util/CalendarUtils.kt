package org.javarosa.xform.util

import org.commcare.util.ArrayDataSource
import org.commcare.util.DefaultArrayDataSource
import org.commcare.util.LocaleArrayDataSource
import org.javarosa.core.model.utils.DateUtils

import java.util.Calendar
import org.javarosa.core.model.utils.PlatformDate
import java.util.TimeZone

class CalendarUtils {
    companion object {
        private var arrayDataSource: ArrayDataSource = LocaleArrayDataSource(
            DefaultArrayDataSource()
        )

        private val NEPALI_YEAR_MONTHS = HashMap<Int, IntArray>()

        private const val MIN_YEAR = 1970
        private const val MAX_YEAR = 2090

        /*
         * Nepali calendar system has no discernible cyclic month pattern, so we must manually
         * enter them here as new calendars are known.
         * Calendar source: https://nepalipatro.com.np/calendar
         *
         * TODO: Enter month lengths for years beyond 2090
         */
        init {
            NEPALI_YEAR_MONTHS[1970] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1971] = intArrayOf(0, 31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1972] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1973] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[1974] = intArrayOf(0, 31, 31, 32, 30, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1975] = intArrayOf(0, 31, 31, 32, 32, 30, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1976] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[1977] = intArrayOf(0, 31, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[1978] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1979] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1980] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[1981] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1982] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1983] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1984] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[1985] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1986] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1987] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1988] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[1989] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1990] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1991] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1992] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[1993] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1994] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1995] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1996] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[1997] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1998] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[1999] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2000] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2001] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2002] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2003] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2004] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2005] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2006] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2007] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2008] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31)
            NEPALI_YEAR_MONTHS[2009] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2010] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2011] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2012] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2013] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2014] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2015] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2016] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2017] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2018] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2019] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2020] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2021] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2022] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2023] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2024] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2025] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2026] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2027] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2028] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2029] = intArrayOf(0, 31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2030] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2031] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2032] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2033] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2034] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2035] = intArrayOf(0, 30, 32, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31)
            NEPALI_YEAR_MONTHS[2036] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2037] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2038] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2039] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2040] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2041] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2042] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2043] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2044] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2045] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2046] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2047] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2048] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2049] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2050] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2051] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2052] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2053] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2054] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2055] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2056] = intArrayOf(0, 31, 31, 32, 31, 32, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2057] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2058] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2059] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2060] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2061] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2062] = intArrayOf(0, 30, 32, 31, 32, 31, 31, 29, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2063] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2064] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2065] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2066] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 29, 31)
            NEPALI_YEAR_MONTHS[2067] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2068] = intArrayOf(0, 31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2069] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2070] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 29, 30, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2071] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2072] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2073] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2074] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2075] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2076] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2077] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2078] = intArrayOf(0, 31, 31, 31, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2079] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2080] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2081] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2082] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2083] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2084] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2085] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2086] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2087] = intArrayOf(0, 31, 31, 32, 32, 31, 31, 30, 29, 30, 29, 30, 30)
            NEPALI_YEAR_MONTHS[2088] = intArrayOf(0, 31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 31)
            NEPALI_YEAR_MONTHS[2089] = intArrayOf(0, 30, 32, 31, 32, 31, 30, 30, 30, 29, 30, 29, 31)
            NEPALI_YEAR_MONTHS[2090] = intArrayOf(0, 31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30)
        }

        // milliseconds from Java epoch to minimum known Nepali date, as entered above
        // (negative)
        private val MIN_MILLIS_FROM_JAVA_EPOCH: Long =
            -countDaysFromMinDay(2026, 9, 17) * UniversalDate.MILLIS_IN_DAY

        @JvmStatic
        private fun ConvertToEthiopian(gregorianYear: Int, gregorianMonth: Int, gregorianDay: Int, format: String): String {
            val ethiopian = gregorianToEthiopian(gregorianYear, gregorianMonth, gregorianDay)

            val strings = getStringsWithMonth(getMonthsArray("ethiopian_months"))

            val df = DateUtils.getFieldsForNonGregorianCalendar(
                ethiopian[0],
                ethiopian[1],
                ethiopian[2]
            )

            return DateUtils.format(df, format, strings)
        }

        /**
         * Convert Gregorian date to Ethiopian date using the fixed offset algorithm.
         * Ethiopian calendar: 13 months (12 × 30 days + 1 × 5 or 6 days).
         * Ethiopian New Year is September 11 (or 12 in Gregorian leap years).
         * Ethiopian year = Gregorian year - 8 (after Sep 11) or - 7 (before Sep 11).
         *
         * @return IntArray of [year, month, day] in Ethiopian calendar
         */
        @JvmStatic
        private fun gregorianToEthiopian(year: Int, month: Int, day: Int): IntArray {
            val jdn = gregorianToJdn(year, month, day)
            // Ethiopian epoch: Meskerem 1, Year 1 = August 27, 8 CE (proleptic Gregorian) = JDN 1724221
            val ethiopianEpoch = 1724221

            val ethYear = (4 * (jdn - ethiopianEpoch) + 1463) / 1461
            val startOfYear = ethiopianEpoch + 365 * (ethYear - 1) + (ethYear - 1) / 4
            val dayOfYear = jdn - startOfYear

            val ethMonth = dayOfYear / 30 + 1
            val ethDay = dayOfYear % 30 + 1

            return intArrayOf(ethYear, ethMonth, ethDay)
        }

        @JvmStatic
        private fun gregorianToJdn(year: Int, month: Int, day: Int): Int {
            val a = (14 - month) / 12
            val y = year + 4800 - a
            val m = month + 12 * a - 3
            return day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        }

        @JvmStatic
        private fun getStringsWithMonth(months: Array<String>): DateUtils.CalendarStrings {
            val strings = DateUtils.CalendarStrings()

            strings.monthNamesLong = months
            strings.monthNamesShort = months

            return strings
        }

        @JvmStatic
        fun ConvertToEthiopian(d: PlatformDate, format: String?): String {
            val fmt = format ?: "%e %B %Y"

            val fields = DateUtils.getFields(d)
            return ConvertToEthiopian(fields.year, fields.month, fields.day, fmt)
        }

        /**
         * Count the number of days from the minimum entered Nepali date
         * to the given Nepali date. If the given Nepali date is out
         * of range, an exception is thrown.
         *
         * @throws RuntimeException is entered date is out of range
         */
        @JvmStatic
        private fun countDaysFromMinDay(toYear: Int, toMonth: Int, toDay: Int): Int {
            if (toYear < MIN_YEAR || toYear > MAX_YEAR
                || toMonth < 1 || toMonth > 12
                || toDay < 1 || toDay > NEPALI_YEAR_MONTHS[toYear]!![toMonth]
            ) {
                throw RuntimeException("Date out of bounds")
            }

            var daysFromMinDay = -1

            for (year in MIN_YEAR..toYear) {
                val monthsInYear = NEPALI_YEAR_MONTHS[year]!!

                for (month in 1..12) {
                    val daysInMonth = monthsInYear[month]

                    for (day in 1..daysInMonth) {
                        daysFromMinDay++

                        if (year == toYear
                            && month == toMonth
                            && day == toDay
                        ) {
                            return daysFromMinDay
                        }
                    }
                }
            }

            throw RuntimeException("Calculation error!")
        }

        /**
         * Convert a Gregorian Date object to a Nepali date string,
         * formatted as 'd MMMM yyyy'.
         *
         * @param date       Gregorian Date to convert
         * @param format     Optional (null to not use) format. Defaults to "d MMMM yyyy" (%e %B %Y)
         * @return Nepali date string in 'd MMMM yyyy' format
         */
        @JvmStatic
        fun convertToNepaliString(date: PlatformDate, format: String?): String {
            var fmt = format
            if (fmt == null) {
                fmt = "%e %B %Y"
            }

            val dateUniv = CalendarUtils.fromMillis(date.time)
            val df = DateUtils.getFieldsForNonGregorianCalendar(
                dateUniv.year,
                dateUniv.month, dateUniv.day
            )

            val strings = getStringsWithMonth(getMonthsArray("nepali_months"))

            return DateUtils.format(df, fmt, strings)
        }

        @JvmStatic
        fun decrementMonth(date: UniversalDate): UniversalDate {
            var year = date.year
            var month = date.month
            var day = date.day

            month--
            if (month < 1) {
                month = 12
                year--
            }

            if (year < MIN_YEAR) {
                year = MAX_YEAR
            }

            if (day > NEPALI_YEAR_MONTHS[year]!![month]) {
                day = NEPALI_YEAR_MONTHS[year]!![month]
            }

            return UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
            )
        }

        @JvmStatic
        fun decrementYear(date: UniversalDate): UniversalDate {
            var year = date.year
            val month = date.month
            var day = date.day

            year--
            if (year < MIN_YEAR) {
                year = MAX_YEAR
            }

            if (day > NEPALI_YEAR_MONTHS[year]!![month]) {
                day = NEPALI_YEAR_MONTHS[year]!![month]
            }

            return UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
            )
        }

        /**
         * @param millisFromJavaEpoch Argument must be normalized to UTC to prevent
         *                            timezone issues when casting to a calendar date
         */
        @JvmStatic
        fun fromMillis(millisFromJavaEpoch: Long, currentTimeZone: TimeZone): UniversalDate {
            // Since epoch calculations are relative to UTC, take current timezone
            // into account. This prevents two time values that lie on the same day
            // in the given timezone from falling on different GMT days.
            val timezoneOffsetFromUTC = currentTimeZone.getOffset(millisFromJavaEpoch)
            // The 'millis since epoch' will be converted into a date in the
            // context of the current timezone, so add that offset in, ensuring
            // the date lies on the correct day
            val millisWithTimezoneOffset = timezoneOffsetFromUTC + millisFromJavaEpoch
            val millisFromMinDay = millisWithTimezoneOffset - MIN_MILLIS_FROM_JAVA_EPOCH
            val daysFromMinDay = millisFromMinDay / UniversalDate.MILLIS_IN_DAY

            var days: Long = -1

            for (year in MIN_YEAR..MAX_YEAR) {
                val monthsInYear = NEPALI_YEAR_MONTHS[year]!!

                for (month in 1..12) {
                    val daysInMonth = monthsInYear[month]

                    for (day in 1..daysInMonth) {
                        days++

                        if (days == daysFromMinDay) {
                            return UniversalDate(
                                year,
                                month,
                                day,
                                millisFromJavaEpoch
                            )
                        }
                    }
                }
            }

            throw RuntimeException("Date out of bounds")
        }

        @JvmStatic
        fun fromMillis(date: PlatformDate, timezone: String?): UniversalDate {
            val cd = Calendar.getInstance()
            cd.time = date
            if (timezone != null) {
                cd.timeZone = TimeZone.getTimeZone(timezone)
            } else if (DateUtils.timezone() != null) {
                cd.timeZone = DateUtils.timezone()
            }
            val dateInMillis = cd.time.time
            return fromMillis(dateInMillis, cd.timeZone)
        }

        @JvmStatic
        fun fromMillis(millisFromJavaEpoch: Long): UniversalDate {
            val date = PlatformDate(millisFromJavaEpoch)
            return fromMillis(date, null)
        }

        @JvmStatic
        fun incrementMonth(date: UniversalDate): UniversalDate {
            var year = date.year
            var month = date.month
            var day = date.day

            month++
            if (month > 12) {
                month = 1
                year++
            }

            if (year > MAX_YEAR) {
                year = MIN_YEAR
            }

            if (day > NEPALI_YEAR_MONTHS[year]!![month]) {
                day = NEPALI_YEAR_MONTHS[year]!![month]
            }

            return UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
            )
        }

        @JvmStatic
        fun incrementYear(date: UniversalDate): UniversalDate {
            var year = date.year
            val month = date.month
            var day = date.day

            year++
            if (year > MAX_YEAR) {
                year = MIN_YEAR
            }

            if (day > NEPALI_YEAR_MONTHS[year]!![month]) {
                day = NEPALI_YEAR_MONTHS[year]!![month]
            }

            return UniversalDate(
                year,
                month,
                day,
                toMillisFromJavaEpoch(year, month, day)
            )
        }

        @JvmStatic
        fun toMillisFromJavaEpoch(year: Int, month: Int, day: Int): Long {
            return toMillisFromJavaEpoch(year, month, day, TimeZone.getDefault())
        }

        @JvmStatic
        fun toMillisFromJavaEpoch(year: Int, month: Int, day: Int, currentTimeZone: TimeZone): Long {
            val daysFromMinDay = countDaysFromMinDay(year, month, day)
            val millisFromMinDay = daysFromMinDay.toLong() * UniversalDate.MILLIS_IN_DAY
            val timezoneOffsetFromUTC = currentTimeZone.getOffset(millisFromMinDay)
            val millisNormalizedToUTC = millisFromMinDay - timezoneOffsetFromUTC
            return millisNormalizedToUTC + MIN_MILLIS_FROM_JAVA_EPOCH
        }

        @JvmStatic
        fun getMonthsArray(key: String): Array<String> {
            return arrayDataSource.getArray(key)
        }

        @JvmStatic
        fun setArrayDataSource(arrayDataSource: ArrayDataSource) {
            CalendarUtils.arrayDataSource = arrayDataSource
        }

        @JvmStatic
        fun toMidnight(cal: Calendar) {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
    }
}
