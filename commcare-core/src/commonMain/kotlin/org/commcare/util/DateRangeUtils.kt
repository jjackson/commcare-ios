package org.commcare.util

import org.commcare.modern.util.Pair
import org.javarosa.core.model.utils.PlatformDate
import org.javarosa.core.model.utils.platformDefaultTimeZone
import org.javarosa.core.util.PlatformParseException
import org.javarosa.core.model.utils.platformFormatPlatformDate
import org.javarosa.core.model.utils.platformParseDate
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
     * @throws PlatformParseException if the given humanReadableDateRange is not in 'yyyy-mm-dd to yyyy-mm-dd' format
     */
    @JvmStatic
    @Throws(PlatformParseException::class)
    fun parseHumanReadableDate(humanReadableDateRange: String): Pair<Long, Long>? {
        if (humanReadableDateRange.contains(DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER)) {
            val humanReadableDateRangeSplit = humanReadableDateRange.split(DATE_RANGE_ANSWER_HUMAN_READABLE_DELIMITER)
            if (humanReadableDateRangeSplit.size == 2) {
                val startDate = platformParseDate(humanReadableDateRangeSplit[0], DATE_FORMAT)
                    ?: throw PlatformParseException("Cannot parse start date: ${humanReadableDateRangeSplit[0]}", 0)
                val endDate = platformParseDate(humanReadableDateRangeSplit[1], DATE_FORMAT)
                    ?: throw PlatformParseException("Cannot parse end date: ${humanReadableDateRangeSplit[1]}", 0)
                return Pair(
                    getTimeFromDateOffsettingTz(startDate),
                    getTimeFromDateOffsettingTz(endDate)
                )
            }
        }
        throw PlatformParseException(
            "Argument $humanReadableDateRange should be formatted as 'yyyy-mm-dd to yyyy-mm-dd'", 0
        )
    }

    private fun getTimeFromDateOffsettingTz(date: PlatformDate): Long {
        val tzOffset = platformDefaultTimeZone().getOffset(date.getTime())
        return date.getTime() - tzOffset
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
    @Throws(PlatformParseException::class)
    fun formatDateRangeAnswer(humanReadableDateRange: String): String {
        val selection = parseHumanReadableDate(humanReadableDateRange)!!
        val startDate = getDateFromTime(selection.first)
        val endDate = getDateFromTime(selection.second)
        return DATE_RANGE_ANSWER_PREFIX + startDate + DATE_RANGE_ANSWER_DELIMITER + endDate
    }

    // Converts given time as yyyy-mm-dd
    @JvmStatic
    fun getDateFromTime(time: Long): String {
        return platformFormatPlatformDate(PlatformDate(time), DATE_FORMAT)
    }
}
