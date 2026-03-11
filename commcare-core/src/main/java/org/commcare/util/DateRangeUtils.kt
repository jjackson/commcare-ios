package org.commcare.util

import org.commcare.modern.util.Pair
import java.text.ParseException
import java.text.SimpleDateFormat
import org.javarosa.core.model.utils.PlatformDate
import java.util.Locale
import kotlin.jvm.JvmStatic

object DateRangeUtils {

    // Changing this will require changing this format on ES end as well
    const val DATE_RANGE_ANSWER_PREFIX = "__range__"
    const val DATE_RANGE_ANSWER_DELIMITER = "__"
    const val DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER = " to "
    private const val DATE_FORMAT = "yyyy-MM-dd"

    /**
     * @param humanReadableDateRange human readable format for date range as 'startDate to endDate'
     * @return a Pair of start time and end time that can be supplied to MaterialDatePicker to set a date range,
     * @throws ParseException if the given humanReadableDateRange is not in 'yyyy-mm-dd to yyyy-mm-dd' format
     */
    @JvmStatic
    @Throws(ParseException::class)
    fun parseHumanReadableDate(humanReadableDateRange: String): Pair<Long, Long>? {
        if (humanReadableDateRange.contains(DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER)) {
            val humanReadableDateRangeSplit = humanReadableDateRange.split(DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER)
            if (humanReadableDateRangeSplit.size == 2) {
                val sdf = SimpleDateFormat(DATE_FORMAT, Locale.US)
                val startDate = sdf.parse(humanReadableDateRangeSplit[0])
                val endDate = sdf.parse(humanReadableDateRangeSplit[1])
                return Pair(
                    getTimeFromDateOffsettingTz(startDate),
                    getTimeFromDateOffsettingTz(endDate)
                )
            }
        }
        throw ParseException(
            "Argument $humanReadableDateRange should be formatted as 'yyyy-mm-dd to yyyy-mm-dd'", 0
        )
    }

    @Suppress("DEPRECATION")
    private fun getTimeFromDateOffsettingTz(date: PlatformDate): Long {
        return date.time - date.timezoneOffset * 60 * 1000
    }

    /**
     * Formats __range__startDate__endDate as 'startDate to EndDate'
     *
     * @param dateRangeAnswer A date range value in form of '__range__startDate__endDate'
     * @return human readable format 'startDate to EndDate' for given dateRangeAnswer
     */
    @JvmStatic
    fun getHumanReadableDateRange(dateRangeAnswer: String?): String? {
        if (dateRangeAnswer != null && dateRangeAnswer.startsWith(DATE_RANGE_ANSWER_PREFIX)) {
            val dateRangeSplit = dateRangeAnswer.split(DATE_RANGE_ANSWER_DELIMITER)
            if (dateRangeSplit.size == 4) {
                return getHumanReadableDateRange(dateRangeSplit[2], dateRangeSplit[3])
            }
        }
        return dateRangeAnswer
    }

    // Formats as 'startDate to endDate'
    @JvmStatic
    fun getHumanReadableDateRange(startDate: String, endDate: String): String {
        return startDate + DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER + endDate
    }

    // Formats as '__range__startDate__endDate'
    @JvmStatic
    fun formatDateRangeAnswer(startDate: String, endDate: String): String {
        return DATE_RANGE_ANSWER_PREFIX + startDate + DATE_RANGE_ANSWER_DELIMITER + endDate
    }

    @JvmStatic
    @Throws(ParseException::class)
    fun formatDateRangeAnswer(humanReadableDateRange: String): String {
        val selection = parseHumanReadableDate(humanReadableDateRange)!!
        val startDate = getDateFromTime(selection.first)
        val endDate = getDateFromTime(selection.second)
        return DATE_RANGE_ANSWER_PREFIX + startDate + DATE_RANGE_ANSWER_DELIMITER + endDate
    }

    // Converts given time as yyyy-mm-dd
    @JvmStatic
    fun getDateFromTime(time: Long): String {
        return SimpleDateFormat(DATE_FORMAT, Locale.US).format(PlatformDate(time))
    }
}
