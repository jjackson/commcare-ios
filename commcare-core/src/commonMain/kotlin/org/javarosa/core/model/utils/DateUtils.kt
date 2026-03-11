package org.javarosa.core.model.utils

import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.MathUtils
import kotlin.math.abs

/**
 * Static utility methods for Dates in j2me
 *
 * @author Clayton Sims
 */
object DateUtils {
    private const val MONTH_OFFSET = 1

    const val FORMAT_ISO8601: Int = 1
    const val FORMAT_ISO8601_WALL_TIME: Int = 10
    const val FORMAT_HUMAN_READABLE_SHORT: Int = 2
    const val FORMAT_HUMAN_READABLE_DAYS_FROM_TODAY: Int = 5
    const val FORMAT_TIMESTAMP_SUFFIX: Int = 7
    /** RFC 822  */
    const val FORMAT_TIMESTAMP_HTTP: Int = 9

    private var defaultCalendarStrings = CalendarStrings()
    private var tzProvider = TimezoneProvider()

    val HOUR_IN_MS: Long = 3_600_000L
    val DAY_IN_MS: Long = 86_400_000L

    private val EPOCH_DATE: PlatformDate = getDate(1970, 1, 1)!!

    private val EPOCH_TIME: Long = roundDate(EPOCH_DATE).getTime()

    class CalendarStrings(
        var monthNamesLong: Array<String> = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        ),
        var monthNamesShort: Array<String> = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        ),
        var dayNamesLong: Array<String> = arrayOf(
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        ),
        var dayNamesShort: Array<String> = arrayOf(
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
        )
    )

    class DateFields {
        var year: Int = 1970
        var month: Int = 1    //1-12
        var day: Int = 1      //1-31
        var hour: Int = 0     //0-23
        var minute: Int = 0   //0-59
        var second: Int = 0   //0-59
        var secTicks: Int = 0 //0-999 (ms)
        var timezoneOffsetInMillis: Int = 0 //(ms)
        var noValidation: Boolean = false // true or false. Set to true when using foreign calendars

        /** NOTE: CANNOT BE USED TO SPECIFY A DATE  */
        var dow: Int = 0     //1-7;

        fun check(): Boolean {
            return noValidation ||
                    (inRange(month, 1, 12) && inRange(day, 1, daysInMonth(month - MONTH_OFFSET, year)) &&
                            inRange(hour, 0, 23) && inRange(minute, 0, 59) && inRange(second, 0, 59) && inRange(secTicks, 0, 999))
        }
    }

    // Used by Formplayer
    fun setTimezoneProvider(provider: TimezoneProvider) {
        tzProvider = provider
    }

    fun resetTimezoneProvider() {
        tzProvider = TimezoneProvider()
    }

    private fun timezoneOffset(): Int {
        return tzProvider.getTimezoneOffsetMillis()
    }

    fun timezoneId(): String? {
        return tzProvider.getTimezoneId()
    }

    fun getFieldsForNonGregorianCalendar(year: Int, monthOfYear: Int, dayOfMonth: Int): DateFields {
        val nonGregorian = DateFields()
        nonGregorian.year = year
        nonGregorian.month = monthOfYear
        nonGregorian.day = dayOfMonth
        return nonGregorian
    }

    fun getFields(d: PlatformDate): DateFields {
        return getFields(d, null as String?)
    }

    fun getFields(d: PlatformDate, timezone: String?): DateFields {
        val tzId = timezone ?: timezoneId()
        if (tzId == null && timezoneOffset() != -1) {
            return getFieldsWithOffset(d, timezoneOffset())
        }
        val arr = platformExtractFields(d, tzId)
        return fieldsFromArray(arr)
    }

    private fun getFieldsWithOffset(d: PlatformDate, timezoneOffset: Int): DateFields {
        // Extract in UTC, then apply offset
        val arr = platformExtractFields(
            PlatformDate(d.getTime() + timezoneOffset),
            "UTC"
        )
        val fields = fieldsFromArray(arr)
        fields.timezoneOffsetInMillis = timezoneOffset
        return fields
    }

    private fun fieldsFromArray(arr: IntArray): DateFields {
        val fields = DateFields()
        fields.year = arr[0]
        fields.month = arr[1]
        fields.day = arr[2]
        fields.hour = arr[3]
        fields.minute = arr[4]
        fields.second = arr[5]
        fields.secTicks = arr[6]
        fields.dow = arr[7]
        fields.timezoneOffsetInMillis = arr[8]
        return fields
    }

    /**
     * Turn year, month, date into Date object.
     *
     * @return Date or null, depending if arguments are in the valid date range
     */
    fun getDate(year: Int, month: Int, day: Int): PlatformDate? {
        val f = DateFields()
        f.year = year
        f.month = month
        f.day = day
        return if (f.check()) getDate(f) else null
    }

    /**
     * Turn DateField information into Date object, using default
     * timezone.
     */
    fun getDate(df: DateFields): PlatformDate {
        return getDate(df, null)!!
    }

    /**
     * Turn DateField information into Date object, taking default or inputted
     * timezone into account.
     */
    private fun getDate(df: DateFields, timezone: String?): PlatformDate? {
        val tzId = timezone ?: timezoneId()
        if (tzId == null && timezoneOffset() != -1) {
            return getDateWithOffset(df, timezoneOffset())
        }
        return platformCreateDate(
            df.year, df.month, df.day,
            df.hour, df.minute, df.second, df.secTicks,
            tzId
        )
    }

    private fun getDateWithOffset(df: DateFields, timezoneOffset: Int): PlatformDate {
        val utcDate = platformCreateDate(
            df.year, df.month, df.day,
            df.hour, df.minute, df.second, df.secTicks,
            "UTC"
        )
        return PlatformDate(utcDate.getTime() - timezoneOffset)
    }

    /* ==== FORMATTING DATES/TIMES TO STANDARD STRINGS ==== */

    fun formatDateTime(d: PlatformDate?, format: Int): String {
        if (d == null) {
            return ""
        }

        val fields = getFields(d, if (format == FORMAT_TIMESTAMP_HTTP) "UTC" else null)

        val delim = when (format) {
            FORMAT_ISO8601 -> "T"
            FORMAT_TIMESTAMP_SUFFIX -> ""
            FORMAT_TIMESTAMP_HTTP -> " "
            else -> " "
        }

        return formatDate(fields, format) + delim + formatTime(fields, format)
    }

    fun formatDate(d: PlatformDate?, format: Int): String {
        return if (d == null) "" else formatDate(getFields(d, if (format == FORMAT_TIMESTAMP_HTTP) "UTC" else null), format)!!
    }

    fun formatTime(d: PlatformDate?, format: Int): String {
        return if (d == null) "" else formatTime(getFields(d, if (format == FORMAT_TIMESTAMP_HTTP) "UTC" else null), format)!!
    }

    private fun formatDate(f: DateFields, format: Int): String? {
        return when (format) {
            FORMAT_ISO8601 -> formatDateISO8601(f)
            FORMAT_HUMAN_READABLE_SHORT -> formatDateColloquial(f)
            FORMAT_HUMAN_READABLE_DAYS_FROM_TODAY -> formatDaysFromToday(f)
            FORMAT_TIMESTAMP_SUFFIX -> formatDateSuffix(f)
            FORMAT_TIMESTAMP_HTTP -> formatDateHttp(f)
            else -> null
        }
    }

    private fun formatTime(f: DateFields, format: Int): String? {
        return when (format) {
            FORMAT_ISO8601 -> formatTimeISO8601(f)
            FORMAT_ISO8601_WALL_TIME -> formatTimeISO8601(f, true)
            FORMAT_HUMAN_READABLE_SHORT -> formatTimeColloquial(f)
            FORMAT_TIMESTAMP_SUFFIX -> formatTimeSuffix(f)
            FORMAT_TIMESTAMP_HTTP -> formatTimeHttp(f)
            else -> null
        }
    }

    /** RFC 822  */
    private fun formatDateHttp(f: DateFields): String {
        return format(f, "%a, %d %b %Y")
    }

    /** RFC 822  */
    private fun formatTimeHttp(f: DateFields): String {
        return format(f, "%H:%M:%S GMT")
    }

    private fun formatDateISO8601(f: DateFields): String {
        return "${f.year}-${intPad(f.month, 2)}-${intPad(f.day, 2)}"
    }

    private fun formatDateColloquial(f: DateFields): String {
        var year = f.year.toString()

        //Normal Date
        if (year.length == 4) {
            year = year.substring(2, 4)
        }
        //Otherwise we have an old or bizzarre date, don't try to do anything

        return "${intPad(f.day, 2)}/${intPad(f.month, 2)}/$year"
    }

    private fun formatDateSuffix(f: DateFields): String {
        return "${f.year}${intPad(f.month, 2)}${intPad(f.day, 2)}"
    }

    private fun formatTimeISO8601(f: DateFields): String {
        return formatTimeISO8601(f, false)
    }

    private fun formatTimeISO8601(f: DateFields, suppressTimezone: Boolean): String {
        var time = "${intPad(f.hour, 2)}:${intPad(f.minute, 2)}:${intPad(f.second, 2)}.${intPad(f.secTicks, 3)}"
        if (suppressTimezone) {
            return time
        }

        val offset: Int
        if (timezoneOffset() != -1) {
            offset = timezoneOffset()
        } else {
            //Time Zone ops (1 in the first field corresponds to 'CE' ERA)
            offset = platformGetDefaultTimezoneOffsetMs(1, f.year, f.month - 1, f.day, f.dow, 0)
        }

        //NOTE: offset is in millis
        if (offset == 0) {
            time += "Z"
        } else {
            //Start with sign
            val offsetSign = if (offset > 0) "+" else "-"

            val value = abs(offset) / 1000 / 60

            val hrs = intPad(value / 60, 2)
            val mins = if (value % 60 != 0) ":" + intPad(value % 60, 2) else ""

            time += offsetSign + hrs + mins
        }
        return time
    }

    private fun formatTimeColloquial(f: DateFields): String {
        return "${intPad(f.hour, 2)}:${intPad(f.minute, 2)}"
    }

    private fun formatTimeSuffix(f: DateFields): String {
        return "${intPad(f.hour, 2)}${intPad(f.minute, 2)}${intPad(f.second, 2)}"
    }

    fun format(d: PlatformDate, format: String): String {
        return format(getFields(d), format)
    }

    fun format(f: DateFields, format: String): String {
        return format(f, format, defaultCalendarStrings)
    }

    fun format(f: DateFields, format: String, stringsSource: CalendarStrings): String {
        val sb = StringBuilder()

        var i = 0
        while (i < format.length) {
            var c = format[i]

            if (c == '%') {
                i++
                if (i >= format.length) {
                    throw RuntimeException("date format string ends with %")
                } else {
                    c = format[i]
                }

                when (c) {
                    '%' -> sb.append("%")
                    'Y' -> sb.append(intPad(f.year, 4))
                    'y' -> sb.append(intPad(f.year, 4).substring(2))
                    'm' -> sb.append(intPad(f.month, 2))
                    'n' -> sb.append(f.month)
                    'B' -> sb.append(stringsSource.monthNamesLong[f.month - 1])
                    'b' -> sb.append(stringsSource.monthNamesShort[f.month - 1])
                    'd' -> sb.append(intPad(f.day, 2))
                    'e' -> sb.append(f.day)
                    'H' -> sb.append(intPad(f.hour, 2))
                    'h' -> sb.append(f.hour)
                    'M' -> sb.append(intPad(f.minute, 2))
                    'S' -> sb.append(intPad(f.second, 2))
                    '3' -> sb.append(intPad(f.secTicks, 3))
                    'A' -> sb.append(stringsSource.dayNamesLong[f.dow - 1])
                    'a' -> sb.append(stringsSource.dayNamesShort[f.dow - 1])
                    'w' -> sb.append(f.dow - 1)
                    'Z' -> sb.append(getOffsetInStandardFormat(f.timezoneOffsetInMillis))
                    in listOf('c', 'C', 'D', 'F', 'g', 'G', 'I', 'j', 'k', 'l', 'p', 'P', 'r', 'R', 's', 't', 'T', 'u', 'U', 'V', 'W', 'x', 'X', 'z') ->
                        throw RuntimeException("unsupported escape in date format string [%$c]")
                    else -> throw RuntimeException("unrecognized escape in date format string [%$c]")
                }
            } else {
                sb.append(c)
            }
            i++
        }

        return sb.toString()
    }

    /* ==== PARSING DATES/TIMES FROM STANDARD STRINGS ==== */

    fun parseDateTime(str: String): PlatformDate? {
        val fields = DateFields()
        val i = str.indexOf("T")
        if (i != -1) {
            if (!parseDateAndStore(str.substring(0, i), fields) || !parseTimeAndStore(str.substring(i + 1), fields)) {
                return null
            }
        } else {
            if (!parseDateAndStore(str, fields)) {
                return null
            }
        }
        return getDate(fields)
    }

    fun parseDate(str: String): PlatformDate? {
        val fields = DateFields()
        if (!parseDateAndStore(str, fields)) {
            return null
        }
        return getDate(fields)
    }

    private fun parseDateAndStore(dateStr: String, df: DateFields): Boolean {
        val pieces = DataUtil.splitOnDash(dateStr)
        if (pieces.size != 3) {
            return false
        }

        try {
            df.year = pieces[0].toInt()
            df.month = pieces[1].toInt()
            df.day = pieces[2].toInt()
        } catch (nfe: NumberFormatException) {
            return false
        }

        return df.check()
    }

    fun parseTime(str: String): PlatformDate? {
        return parseTime(str, false)
    }

    fun parseTime(str: String, ignoreTimezone: Boolean): PlatformDate? {
        var timeStr = str
        if (!ignoreTimezone && (timezoneOffset() != -1 && !timeStr.contains("+") && !timeStr.contains("-") && !timeStr.contains("Z"))) {
            timeStr = timeStr + getOffsetInStandardFormat(timezoneOffset())
        }

        val fields = DateFields()
        if (!parseTimeAndStore(timeStr, fields)) {
            return null
        }
        return getDate(fields)
    }

    fun getOffsetInStandardFormat(offsetInMillis: Int): String {
        val hours = offsetInMillis / 1000 / 60 / 60
        var offsetStr: String
        if (hours > 0) {
            offsetStr = "+" + intPad(hours, 2)
        } else if (hours == 0) {
            offsetStr = "Z"
        } else {
            offsetStr = "-" + intPad(abs(hours), 2)
        }

        val totalMinutes = offsetInMillis / 1000 / 60
        val remainderMinutes = abs(totalMinutes) % 60
        if (remainderMinutes != 0) {
            offsetStr += ":" + intPad(remainderMinutes, 2)
        }

        return offsetStr
    }

    private fun parseTimeAndStore(timeStr: String, df: DateFields): Boolean {
        var mutableTimeStr = timeStr
        // get timezone information first
        var timeOffset: DateFields? = null

        if (mutableTimeStr[mutableTimeStr.length - 1] == 'Z') {
            // UTC!
            mutableTimeStr = mutableTimeStr.substring(0, mutableTimeStr.length - 1)
            timeOffset = DateFields()
        } else if (mutableTimeStr.contains("+") || mutableTimeStr.contains("-")) {
            timeOffset = DateFields()

            var pieces = DataUtil.splitOnPlus(mutableTimeStr)

            var offsetSign = -1

            if (pieces.size > 1) {
                // offsetSign is already correct
            } else {
                pieces = DataUtil.splitOnDash(mutableTimeStr)
                offsetSign = 1
            }

            mutableTimeStr = pieces[0]

            val offset = pieces[1]
            var hours = offset
            if (offset.contains(":")) {
                val tzPieces = DataUtil.splitOnColon(offset)
                hours = tzPieces[0]
                val mins = tzPieces[1].toInt()
                timeOffset.minute = mins * offsetSign
            }
            timeOffset.hour = hours.toInt() * offsetSign
        }

        // Do the actual parse for the real time values;
        if (!parseRawTime(mutableTimeStr, df)) {
            return false
        }

        if (!df.check()) {
            return false
        }

        // Time is good, if there was no timezone info, just return that;
        if (timeOffset == null) {
            return true
        }

        // Now apply any relevant offsets from the timezone.
        val utcDate = getDate(df, "UTC")!!
        val adjustedTime = utcDate.getTime() + (((60 * timeOffset.hour) + timeOffset.minute) * 60 * 1000)
        val localDate = PlatformDate(adjustedTime)

        // Extract fields in local timezone
        val adjusted = getFields(localDate)

        df.hour = adjusted.hour
        df.minute = adjusted.minute
        df.second = adjusted.second
        df.secTicks = adjusted.secTicks

        return df.check()
    }

    private fun parseRawTime(timeStr: String, df: DateFields): Boolean {
        val pieces = DataUtil.splitOnColon(timeStr)

        if (pieces.size != 2 && pieces.size != 3) {
            return false
        }

        try {
            df.hour = pieces[0].toInt()
            df.minute = pieces[1].toInt()

            // if seconds part present, parse it
            if (pieces.size == 3) {
                var secStr = pieces[2]
                var idx: Int = 0
                // only grab prefix of seconds piece that includes digits and decimal(s)
                while (idx < secStr.length) {
                    val ch = secStr[idx]
                    if (!ch.isDigit() && ch != '.') break
                    idx++
                }
                secStr = secStr.substring(0, idx)
                val fsec = secStr.toDouble()
                // split seconds into whole and decimal components
                df.second = fsec.toInt()
                df.secTicks = (1000.0 * (fsec - df.second)).toInt()
            }
        } catch (nfe: NumberFormatException) {
            return false
        }

        return df.check()
    }

    /* ==== DATE UTILITY FUNCTIONS ==== */

    /**
     * @return new Date object with same date but time set to midnight (in current timezone)
     */
    fun roundDate(d: PlatformDate): PlatformDate {
        val f = getFields(d)
        return getDate(f.year, f.month, f.day)!!
    }

    fun today(): PlatformDate {
        return roundDate(PlatformDate())
    }

    /* ==== CALENDAR FUNCTIONS ==== */

    // Month constants (0-based, matching java.util.Calendar)
    private const val JANUARY = 0
    private const val FEBRUARY = 1
    private const val APRIL = 3
    private const val JUNE = 5
    private const val SEPTEMBER = 8
    private const val NOVEMBER = 10

    fun daysInMonth(month: Int, year: Int): Int {
        return when (month) {
            APRIL, JUNE, SEPTEMBER, NOVEMBER -> 30
            FEBRUARY -> 28 + (if (isLeap(year)) 1 else 0)
            else -> 31
        }
    }

    fun isLeap(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    /* ==== Parsing to Human Text ==== */

    private fun formatDaysFromToday(f: DateFields): String {
        val d = getDate(f)
        val daysAgo = daysSinceEpoch(PlatformDate()) - daysSinceEpoch(d)

        return when {
            daysAgo == 0 -> platformLocalize("date.today")
            daysAgo == 1 -> platformLocalize("date.yesterday")
            daysAgo == 2 -> platformLocalize("date.twoago", arrayOf(daysAgo.toString()))
            daysAgo in 3..6 -> platformLocalize("date.nago", arrayOf(daysAgo.toString()))
            daysAgo == -1 -> platformLocalize("date.tomorrow")
            daysAgo in -6..-2 -> platformLocalize("date.nfromnow", arrayOf((-daysAgo).toString()))
            else -> formatDate(f, FORMAT_HUMAN_READABLE_SHORT)!!
        }
    }

    /* ==== DATE OPERATIONS ==== */

    fun getPastPeriodDate(
        ref: PlatformDate, type: String, start: String, beginning: Boolean,
        includeToday: Boolean, nAgo: Int
    ): PlatformDate? {
        var d: PlatformDate? = null

        if (type == "week") {
            var target_dow = -1
            val offset = if (includeToday) 1 else 0

            target_dow = when (start) {
                "sun" -> 0
                "mon" -> 1
                "tue" -> 2
                "wed" -> 3
                "thu" -> 4
                "fri" -> 5
                "sat" -> 6
                else -> -1
            }

            if (target_dow == -1) {
                throw RuntimeException()
            }

            val current_dow = platformGetDayOfWeek(ref) - 1 // Convert 1-based to 0-based

            val diff = (((current_dow - target_dow) + (7 + offset)) % 7 - offset) + (7 * nAgo) - (if (beginning) 0 else 6) //booyah
            d = PlatformDate(ref.getTime() - diff * DAY_IN_MS)
        } else if (type == "month") {
            //not supported
        } else {
            throw IllegalArgumentException()
        }

        return d
    }

    fun getMonthsDifference(earlierDate: PlatformDate, laterDate: PlatformDate): Int {
        val span = PlatformDate(laterDate.getTime() - earlierDate.getTime())
        val firstDate = PlatformDate(0)

        val firstFields = platformExtractFields(firstDate, null)
        val spanFields = platformExtractFields(span, null)

        val firstYear = firstFields[0]
        val firstMonth = firstFields[1]
        val spanYear = spanFields[0]
        val spanMonth = spanFields[1]

        return (spanYear - firstYear) * 12 + (spanMonth - firstMonth)
    }

    fun daysSinceEpoch(date: PlatformDate): Int {
        return MathUtils.divLongNotSuck(roundDate(date).getTime() - EPOCH_TIME + DAY_IN_MS / 2, DAY_IN_MS).toInt()
    }

    fun fractionalDaysSinceEpoch(a: PlatformDate): Double {
        val timeZoneAdjust = ((platformGetTimezoneOffsetMinutes(a) - platformGetTimezoneOffsetMinutes(EPOCH_DATE)) * 60 * 1000).toLong()
        return ((a.getTime() - EPOCH_DATE.getTime()) - timeZoneAdjust) / DAY_IN_MS.toDouble()
    }

    /**
     * add n days to date d
     */
    fun dateAdd(d: PlatformDate, n: Int): PlatformDate {
        return roundDate(PlatformDate(roundDate(d).getTime() + DAY_IN_MS * n + DAY_IN_MS / 2))
        //half-day offset is needed to handle differing DST offsets!
    }

    /**
     * return the number of days between a and b, positive if b is later than a
     */
    fun dateDiff(a: PlatformDate, b: PlatformDate): Int {
        return MathUtils.divLongNotSuck(roundDate(b).getTime() - roundDate(a).getTime() + DAY_IN_MS / 2, DAY_IN_MS).toInt()
        //half-day offset is needed to handle differing DST offsets!
    }

    /* ==== UTILITY ==== */

    fun intPad(n: Int, pad: Int): String {
        var s = n.toString()
        while (s.length < pad)
            s = "0$s"
        return s
    }

    private fun inRange(x: Int, min: Int, max: Int): Boolean {
        return x in min..max
    }

    /* ==== GARBAGE (backward compatibility; too lazy to remove them now) ==== */

    fun formatDateToTimeStamp(date: PlatformDate?): String {
        return formatDateTime(date, FORMAT_ISO8601)
    }

    fun getShortStringValue(`val`: PlatformDate?): String {
        return formatDate(`val`, FORMAT_HUMAN_READABLE_SHORT)
    }

    fun getXMLStringValue(`val`: PlatformDate?): String {
        return formatDate(`val`, FORMAT_ISO8601)
    }

    fun get24HourTimeFromDate(d: PlatformDate?): String {
        return formatTime(d, FORMAT_HUMAN_READABLE_SHORT)
    }

    fun getDateFromString(value: String): PlatformDate? {
        return parseDate(value)
    }

    fun getDateTimeFromString(value: String): PlatformDate? {
        return parseDateTime(value)
    }

    fun stringContains(string: String?, substring: String?): Boolean {
        if (string == null || substring == null) {
            return false
        }
        return string.contains(substring)
    }

    fun convertTimeInMsToISO8601(ms: Long): String {
        return platformFormatMsAsISO8601(ms)
    }
}
