package org.javarosa.core.model.utils

import org.javarosa.core.services.locale.Localization
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.MathUtils
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import org.javarosa.core.model.utils.PlatformDate
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Static utility methods for Dates in j2me
 *
 * @author Clayton Sims
 */
object DateUtils {
    private val MONTH_OFFSET = 1 - Calendar.JANUARY

    const val FORMAT_ISO8601: Int = 1
    const val FORMAT_ISO8601_WALL_TIME: Int = 10
    const val FORMAT_HUMAN_READABLE_SHORT: Int = 2
    const val FORMAT_HUMAN_READABLE_DAYS_FROM_TODAY: Int = 5
    const val FORMAT_TIMESTAMP_SUFFIX: Int = 7
    /** RFC 822  */
    const val FORMAT_TIMESTAMP_HTTP: Int = 9

    private var defaultCalendarStrings = CalendarStrings()
    private var tzProvider = TimezoneProvider()

    @JvmField
    val HOUR_IN_MS: Long = TimeUnit.HOURS.toMillis(1)
    @JvmField
    val DAY_IN_MS: Long = TimeUnit.DAYS.toMillis(1)

    private val EPOCH_DATE: PlatformDate = getDate(1970, 1, 1)!!

    private val EPOCH_TIME: Long = roundDate(EPOCH_DATE).time

    class CalendarStrings @JvmOverloads constructor(
        @JvmField var monthNamesLong: Array<String> = arrayOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        ),
        @JvmField var monthNamesShort: Array<String> = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        ),
        @JvmField var dayNamesLong: Array<String> = arrayOf(
            "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
        ),
        @JvmField var dayNamesShort: Array<String> = arrayOf(
            "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"
        )
    )

    class DateFields {
        @JvmField var year: Int = 1970
        @JvmField var month: Int = 1    //1-12
        @JvmField var day: Int = 1      //1-31
        @JvmField var hour: Int = 0     //0-23
        @JvmField var minute: Int = 0   //0-59
        @JvmField var second: Int = 0   //0-59
        @JvmField var secTicks: Int = 0 //0-999 (ms)
        @JvmField var timezoneOffsetInMillis: Int = 0 //(ms)
        var noValidation: Boolean = false // true or false. Set to true when using foreign calendars

        /** NOTE: CANNOT BE USED TO SPECIFY A DATE  */
        @JvmField var dow: Int = 0     //1-7;

        fun check(): Boolean {
            return noValidation ||
                    (inRange(month, 1, 12) && inRange(day, 1, daysInMonth(month - MONTH_OFFSET, year)) &&
                            inRange(hour, 0, 23) && inRange(minute, 0, 59) && inRange(second, 0, 59) && inRange(secTicks, 0, 999))
        }
    }

    // Used by Formplayer
    @JvmStatic
    fun setTimezoneProvider(provider: TimezoneProvider) {
        tzProvider = provider
    }

    @JvmStatic
    fun resetTimezoneProvider() {
        tzProvider = TimezoneProvider()
    }

    private fun timezoneOffset(): Int {
        return tzProvider.getTimezoneOffsetMillis()
    }

    @JvmStatic
    fun timezone(): TimeZone? {
        return tzProvider.getTimezone()
    }

    @JvmStatic
    fun getFieldsForNonGregorianCalendar(year: Int, monthOfYear: Int, dayOfMonth: Int): DateFields {
        val nonGregorian = DateFields()
        nonGregorian.year = year
        nonGregorian.month = monthOfYear
        nonGregorian.day = dayOfMonth
        return nonGregorian
    }

    @JvmStatic
    fun getFields(d: PlatformDate): DateFields {
        return getFields(d, null as String?)
    }

    @JvmStatic
    fun getFields(d: PlatformDate, timezone: String?): DateFields {
        val cd = Calendar.getInstance()
        cd.time = d
        if (timezone != null) {
            cd.timeZone = TimeZone.getTimeZone(timezone)
        } else if (timezone() != null) {
            cd.timeZone = timezone()
        } else if (timezoneOffset() != -1) {
            return getFields(d, timezoneOffset())
        }
        return getFields(cd, cd.timeZone.getOffset(d.time))
    }

    private fun getFields(d: PlatformDate, timezoneOffset: Int): DateFields {
        val cd = Calendar.getInstance()
        cd.timeZone = TimeZone.getTimeZone("UTC")
        cd.time = d
        cd.add(Calendar.MILLISECOND, timezoneOffset)
        return getFields(cd, timezoneOffset)
    }

    private fun getFields(cal: Calendar, timezoneOffset: Int): DateFields {
        val fields = DateFields()
        fields.year = cal.get(Calendar.YEAR)
        fields.month = cal.get(Calendar.MONTH) + MONTH_OFFSET
        fields.day = cal.get(Calendar.DAY_OF_MONTH)
        fields.hour = cal.get(Calendar.HOUR_OF_DAY)
        fields.minute = cal.get(Calendar.MINUTE)
        fields.second = cal.get(Calendar.SECOND)
        fields.secTicks = cal.get(Calendar.MILLISECOND)
        fields.dow = cal.get(Calendar.DAY_OF_WEEK)
        fields.timezoneOffsetInMillis = timezoneOffset
        return fields
    }

    /**
     * Turn year, month, date into Date object.
     *
     * @return Date or null, depending if arguments are in the valid date range
     */
    @JvmStatic
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
    @JvmStatic
    fun getDate(df: DateFields): PlatformDate {
        return getDate(df, null)!!
    }

    /**
     * Turn DateField information into Date object, taking default or inputted
     * timezone into account.
     */
    private fun getDate(df: DateFields, timezone: String?): PlatformDate? {
        val cd = Calendar.getInstance()

        if (timezone != null) {
            cd.timeZone = TimeZone.getTimeZone(timezone)
        } else if (timezone() != null) {
            cd.timeZone = timezone()
        } else if (timezoneOffset() != -1) {
            return getDate(df, timezoneOffset())
        }

        cd.set(Calendar.YEAR, df.year)
        cd.set(Calendar.MONTH, df.month - MONTH_OFFSET)
        cd.set(Calendar.DAY_OF_MONTH, df.day)
        cd.set(Calendar.HOUR_OF_DAY, df.hour)
        cd.set(Calendar.MINUTE, df.minute)
        cd.set(Calendar.SECOND, df.second)
        cd.set(Calendar.MILLISECOND, df.secTicks)

        return cd.time
    }

    private fun getDate(df: DateFields, timezoneOffset: Int): PlatformDate {
        val cd = Calendar.getInstance()
        cd.timeZone = TimeZone.getTimeZone("UTC")

        cd.set(Calendar.YEAR, df.year)
        cd.set(Calendar.MONTH, df.month - MONTH_OFFSET)
        cd.set(Calendar.DAY_OF_MONTH, df.day)
        cd.set(Calendar.HOUR_OF_DAY, df.hour)
        cd.set(Calendar.MINUTE, df.minute)
        cd.set(Calendar.SECOND, df.second)
        cd.set(Calendar.MILLISECOND, df.secTicks)

        cd.add(Calendar.MILLISECOND, -1 * timezoneOffset)

        return cd.time
    }

    /* ==== FORMATTING DATES/TIMES TO STANDARD STRINGS ==== */

    @JvmStatic
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

    @JvmStatic
    fun formatDate(d: PlatformDate?, format: Int): String {
        return if (d == null) "" else formatDate(getFields(d, if (format == FORMAT_TIMESTAMP_HTTP) "UTC" else null), format)!!
    }

    @JvmStatic
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
            offset = TimeZone.getDefault().getOffset(1, f.year, f.month - 1, f.day, f.dow, 0)
        }

        //NOTE: offset is in millis
        if (offset == 0) {
            time += "Z"
        } else {
            //Start with sign
            val offsetSign = if (offset > 0) "+" else "-"

            val value = Math.abs(offset) / 1000 / 60

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

    @JvmStatic
    fun format(d: PlatformDate, format: String): String {
        return format(getFields(d), format)
    }

    @JvmStatic
    fun format(f: DateFields, format: String): String {
        return format(f, format, defaultCalendarStrings)
    }

    @JvmStatic
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

    @JvmStatic
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

    @JvmStatic
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

    @JvmStatic
    fun parseTime(str: String): PlatformDate? {
        return parseTime(str, false)
    }

    @JvmStatic
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

    @JvmStatic
    fun getOffsetInStandardFormat(offsetInMillis: Int): String {
        val hours = offsetInMillis / 1000 / 60 / 60
        var offsetStr: String
        if (hours > 0) {
            offsetStr = "+" + intPad(hours, 2)
        } else if (hours == 0) {
            offsetStr = "Z"
        } else {
            offsetStr = "-" + intPad(Math.abs(hours), 2)
        }

        val totalMinutes = offsetInMillis / 1000 / 60
        val remainderMinutes = Math.abs(totalMinutes) % 60
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
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        c.time = PlatformDate(getDate(df, "UTC")!!.time + (((60 * timeOffset.hour) + timeOffset.minute) * 60 * 1000))

        // c is now in the timezone of the parsed value, so put
        // it in the local timezone.

        c.timeZone = TimeZone.getDefault()

        val adjusted = getFields(c.time)

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
                    if (!Character.isDigit(ch) && ch != '.') break
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
    @JvmStatic
    fun roundDate(d: PlatformDate): PlatformDate {
        val f = getFields(d)
        return getDate(f.year, f.month, f.day)!!
    }

    @JvmStatic
    fun today(): PlatformDate {
        return roundDate(PlatformDate())
    }

    /* ==== CALENDAR FUNCTIONS ==== */

    @JvmStatic
    fun daysInMonth(month: Int, year: Int): Int {
        return when (month) {
            Calendar.APRIL, Calendar.JUNE, Calendar.SEPTEMBER, Calendar.NOVEMBER -> 30
            Calendar.FEBRUARY -> 28 + (if (isLeap(year)) 1 else 0)
            else -> 31
        }
    }

    @JvmStatic
    fun isLeap(year: Int): Boolean {
        return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    }

    /* ==== Parsing to Human Text ==== */

    private fun formatDaysFromToday(f: DateFields): String {
        val d = getDate(f)
        val daysAgo = daysSinceEpoch(PlatformDate()) - daysSinceEpoch(d)

        return when {
            daysAgo == 0 -> Localization.get("date.today")
            daysAgo == 1 -> Localization.get("date.yesterday")
            daysAgo == 2 -> Localization.get("date.twoago", arrayOf(daysAgo.toString()))
            daysAgo in 3..6 -> Localization.get("date.nago", arrayOf(daysAgo.toString()))
            daysAgo == -1 -> Localization.get("date.tomorrow")
            daysAgo in -6..-2 -> Localization.get("date.nfromnow", arrayOf((-daysAgo).toString()))
            else -> formatDate(f, FORMAT_HUMAN_READABLE_SHORT)!!
        }
    }

    /* ==== DATE OPERATIONS ==== */

    @JvmStatic
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

            val cd = Calendar.getInstance()
            cd.time = ref

            val current_dow = when (cd.get(Calendar.DAY_OF_WEEK)) {
                Calendar.SUNDAY -> 0
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                else -> throw RuntimeException() //something is wrong
            }

            val diff = (((current_dow - target_dow) + (7 + offset)) % 7 - offset) + (7 * nAgo) - (if (beginning) 0 else 6) //booyah
            d = PlatformDate(ref.time - diff * DAY_IN_MS)
        } else if (type == "month") {
            //not supported
        } else {
            throw IllegalArgumentException()
        }

        return d
    }

    @JvmStatic
    fun getMonthsDifference(earlierDate: PlatformDate, laterDate: PlatformDate): Int {
        val span = PlatformDate(laterDate.time - earlierDate.time)
        val firstDate = PlatformDate(0)
        val calendar = Calendar.getInstance()
        calendar.time = firstDate
        val firstYear = calendar.get(Calendar.YEAR)
        val firstMonth = calendar.get(Calendar.MONTH)

        calendar.time = span
        val spanYear = calendar.get(Calendar.YEAR)
        val spanMonth = calendar.get(Calendar.MONTH)
        return (spanYear - firstYear) * 12 + (spanMonth - firstMonth)
    }

    @JvmStatic
    fun daysSinceEpoch(date: PlatformDate): Int {
        return MathUtils.divLongNotSuck(roundDate(date).time - EPOCH_TIME + DAY_IN_MS / 2, DAY_IN_MS).toInt()
    }

    @JvmStatic
    fun fractionalDaysSinceEpoch(a: PlatformDate): Double {
        @Suppress("DEPRECATION")
        val timeZoneAdjust = ((a.timezoneOffset - EPOCH_DATE.timezoneOffset) * 60 * 1000).toLong()
        return ((a.time - EPOCH_DATE.time) - timeZoneAdjust) / DAY_IN_MS.toDouble()
    }

    /**
     * add n days to date d
     */
    @JvmStatic
    fun dateAdd(d: PlatformDate, n: Int): PlatformDate {
        return roundDate(PlatformDate(roundDate(d).time + DAY_IN_MS * n + DAY_IN_MS / 2))
        //half-day offset is needed to handle differing DST offsets!
    }

    /**
     * return the number of days between a and b, positive if b is later than a
     */
    @JvmStatic
    fun dateDiff(a: PlatformDate, b: PlatformDate): Int {
        return MathUtils.divLongNotSuck(roundDate(b).time - roundDate(a).time + DAY_IN_MS / 2, DAY_IN_MS).toInt()
        //half-day offset is needed to handle differing DST offsets!
    }

    /* ==== UTILITY ==== */

    @JvmStatic
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

    @JvmStatic
    fun formatDateToTimeStamp(date: PlatformDate?): String {
        return formatDateTime(date, FORMAT_ISO8601)
    }

    @JvmStatic
    fun getShortStringValue(`val`: PlatformDate?): String {
        return formatDate(`val`, FORMAT_HUMAN_READABLE_SHORT)
    }

    @JvmStatic
    fun getXMLStringValue(`val`: PlatformDate?): String {
        return formatDate(`val`, FORMAT_ISO8601)
    }

    @JvmStatic
    fun get24HourTimeFromDate(d: PlatformDate?): String {
        return formatTime(d, FORMAT_HUMAN_READABLE_SHORT)
    }

    @JvmStatic
    fun getDateFromString(value: String): PlatformDate? {
        return parseDate(value)
    }

    @JvmStatic
    fun getDateTimeFromString(value: String): PlatformDate? {
        return parseDateTime(value)
    }

    @JvmStatic
    fun stringContains(string: String?, substring: String?): Boolean {
        if (string == null || substring == null) {
            return false
        }
        return string.contains(substring)
    }

    // TODO: Move this method to DateUtils
    @JvmStatic
    fun convertTimeInMsToISO8601(ms: Long): String {
        return if (ms == 0L) {
            ""
        } else {
            val df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'")
            df.timeZone = TimeZone.getTimeZone("UTC")
            df.format(ms)
        }
    }
}
