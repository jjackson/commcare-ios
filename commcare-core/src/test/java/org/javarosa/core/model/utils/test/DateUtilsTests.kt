package org.javarosa.core.model.utils.test

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.model.utils.DateUtils.DateFields
import org.javarosa.test_utils.MockTimezoneProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.HashMap
import java.util.TimeZone

class DateUtilsTests {

    companion object {
        private lateinit var currentTime: Date
        private const val HOUR_IN_MILLIS = 60 * 60 * 1000

        @JvmStatic
        @BeforeClass
        fun setUp() {
            currentTime = Date()
        }

        private fun testTimeParsingHelper(timezoneId: String) {
            val c = Calendar.getInstance()
            val tz = TimeZone.getTimeZone(timezoneId)
            c.timeZone = tz
            c.set(1970, 0, 1, 22, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            val expectedDate = c.time

            val tzProvider = MockTimezoneProvider()
            DateUtils.setTimezoneProvider(tzProvider)
            tzProvider.setOffset(tz.getOffset(expectedDate.time))

            assertEquals(expectedDate.time, DateUtils.parseTime("22:00")!!.time)
            DateUtils.resetTimezoneProvider()
        }

        private fun testDateTimeParsingHelper(timezoneId: String) {
            val c = Calendar.getInstance()
            val tz = TimeZone.getTimeZone(timezoneId)
            c.timeZone = tz
            c.set(2017, 0, 2, 2, 0, 0)
            c.set(Calendar.MILLISECOND, 0)
            val expectedDate = c.time

            val tzProvider = MockTimezoneProvider()
            DateUtils.setTimezoneProvider(tzProvider)
            tzProvider.setOffset(tz.getOffset(expectedDate.time))

            assertEquals(expectedDate.time,
                    DateUtils.parseDateTime("2017-01-02T02:00:00")!!.time)
            DateUtils.resetTimezoneProvider()
        }

        private fun offsetStringTestHelper(offsetInMillis: Int, expectedOffsetString: String) {
            assertEquals(expectedOffsetString, DateUtils.getOffsetInStandardFormat(offsetInMillis))
        }
    }

    /**
     * This test ensures that the Strings returned
     * by the getXMLStringValue function are in
     * the proper XML compliant format.
     */
    @Test
    fun testGetXMLStringValueFormat() {
        val currentDate = DateUtils.getXMLStringValue(currentTime)
        assertEquals("The date string was not of the proper length", currentDate.length, "YYYY-MM-DD".length)
        assertEquals("The date string does not have proper year formatting", currentDate.indexOf("-"), "YYYY-".indexOf("-"))

        try {
            Integer.parseInt(currentDate.substring(0, 4))
        } catch (e: NumberFormatException) {
            fail("The Year value was not a valid integer")
        }

        try {
            Integer.parseInt(currentDate.substring(5, 7))
        } catch (e: NumberFormatException) {
            fail("The Month value was not a valid integer")
        }

        try {
            Integer.parseInt(currentDate.substring(8, 10))
        } catch (e: NumberFormatException) {
            fail("The Day value was not a valid integer")
        }
    }

    @Test
    fun testTimeParses() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        testTime("10:00", 1000L * 60 * 60 * 10)
        testTime("10:00Z", 1000L * 60 * 60 * 10)

        testTime("10:00+02", 1000L * 60 * 60 * 8)
        testTime("10:00-02", 1000L * 60 * 60 * 12)

        testTime("10:00+02:30", 1000L * 60 * 6 * 75)
        testTime("10:00-02:30", 1000L * 60 * 6 * 125)

        val offsetTwoHours = TimeZone.getTimeZone("GMT+02")
        TimeZone.setDefault(offsetTwoHours)

        testTime("10:00", 1000L * 60 * 60 * 10)
        testTime("10:00Z", 1000L * 60 * 60 * 12)

        testTime("10:00+02", 1000L * 60 * 60 * 10)
        testTime("10:00-02", 1000L * 60 * 60 * 14)

        testTime("10:00+02:30", 1000L * 60 * 6 * 95)
        testTime("10:00-02:30", 1000L * 60 * 6 * 145)

        val offsetMinusTwoHours = TimeZone.getTimeZone("GMT-02")
        TimeZone.setDefault(offsetMinusTwoHours)

        testTime("14:00", 1000L * 60 * 60 * 14)
        testTime("14:00Z", 1000L * 60 * 60 * 12)

        testTime("14:00+02", 1000L * 60 * 60 * 10)
        testTime("14:00-02", 1000L * 60 * 60 * 14)

        testTime("14:00+02:30", 1000L * 60 * 6 * 95)
        testTime("14:00-02:30", 1000L * 60 * 6 * 145)

        val offsetPlusHalf = TimeZone.getTimeZone("GMT+0230")
        TimeZone.setDefault(offsetPlusHalf)

        testTime("14:00", 1000L * 60 * 6 * 140)
        testTime("14:00Z", 1000L * 60 * 6 * 165)

        testTime("14:00+02", 1000L * 60 * 6 * 145)
        testTime("14:00-02", 1000L * 60 * 6 * 185)

        testTime("14:00+02:30", 1000L * 60 * 6 * 140)
        testTime("14:00-02:30", 1000L * 60 * 6 * 190)

        testTime("14:00+04:00", 1000L * 60 * 6 * 125)

        TimeZone.setDefault(null)
    }

    private fun testTime(input: String, test: Long) {
        try {
            val d = DateUtils.parseTime(input)!!
            val offset = getOffset()
            val value = d.time + offset

            assertEquals("Fail: $input(${TimeZone.getDefault().displayName})", test, value)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Error: $input${e.message}")
        }
    }

    private fun getOffset(): Long {
        val df = DateFields()
        val d = DateUtils.getDate(df)
        return -d.time
    }

    @Test
    fun testParity() {
        testCycle(Date(1300139579000L))
        testCycle(Date(0))

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        testCycle(Date(1300139579000L))
        testCycle(Date(0))

        val offsetTwoHours = TimeZone.getTimeZone("GMT+02")
        TimeZone.setDefault(offsetTwoHours)

        testCycle(Date(1300139579000L))
        testCycle(Date(0))

        val offTwoHalf = TimeZone.getTimeZone("GMT+0230")
        TimeZone.setDefault(offTwoHalf)

        testCycle(Date(1300139579000L))
        testCycle(Date(0))

        val offMinTwoHalf = TimeZone.getTimeZone("GMT-0230")
        TimeZone.setDefault(offMinTwoHalf)

        testCycle(Date(1300139579000L))
        testCycle(Date(0))
    }

    private fun testCycle(input: Date) {
        try {
            val formatted = DateUtils.formatDateTime(input, DateUtils.FORMAT_ISO8601)
            val out = DateUtils.parseDateTime(formatted)!!
            assertEquals("Fail:", input.time, out.time)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Error: $input${e.message}")
        }
    }

    @Test
    fun testFormat() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

        val novFifth2016 = Calendar.getInstance()
        novFifth2016.set(2016, Calendar.NOVEMBER, 5)
        val novFifthDate = novFifth2016.time
        val novFifth2016Fields = DateUtils.getFields(novFifthDate, null)
        val escapesResults = HashMap<String, String>()
        escapesResults["%a"] = "Sat"
        escapesResults["%A"] = "Saturday"
        escapesResults["%b"] = "Nov"
        escapesResults["%B"] = "November"
        escapesResults["%d"] = "05"
        escapesResults["%e"] = "5"
        escapesResults["%w"] = "6"
        escapesResults["%Z"] = "Z"

        for (escape in escapesResults.keys) {
            val result = escapesResults[escape]
            val formatted = DateUtils.format(novFifth2016Fields, escape)
            assertEquals("Fail: '$escape' rendered unexpectedly", result, formatted)
        }

        var didFail = false
        try {
            DateUtils.format(novFifth2016Fields, "%c")
        } catch (e: RuntimeException) {
            didFail = true
        }
        assertTrue(didFail)

        TimeZone.setDefault(null)
    }

    @Test
    fun testFormatWithDefaultTimezone() {
        val format = "%Y-%m-%d %H:%M:%S%Z"

        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2017, 0, 2, 2, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val d = c.time

        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        var expectedDateTime = "2017-01-02 02:00:00Z"
        assertEquals(expectedDateTime, DateUtils.format(d, format))

        TimeZone.setDefault(TimeZone.getTimeZone("EST"))
        expectedDateTime = "2017-01-01 21:00:00-05"
        assertEquals(expectedDateTime, DateUtils.format(d, format))

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Katmandu"))
        expectedDateTime = "2017-01-02 07:45:00+05:45"
        assertEquals(expectedDateTime, DateUtils.format(d, format))

        TimeZone.setDefault(TimeZone.getTimeZone("IST"))
        expectedDateTime = "2017-01-02 07:30:00+05:30"
        assertEquals(expectedDateTime, DateUtils.format(d, format))

        TimeZone.setDefault(null)
    }

    @Test
    fun testFormatWithProviderTimezone() {
        val format = "%Y-%m-%d %H:%M:%S%Z"

        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2017, 0, 2, 2, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val d = c.time

        val tzProvider = MockTimezoneProvider()
        DateUtils.setTimezoneProvider(tzProvider)

        tzProvider.setOffset(0)
        var expectedDateTime1HourAhead = "2017-01-02 02:00:00Z"
        assertEquals(expectedDateTime1HourAhead, DateUtils.format(d, format))

        tzProvider.setOffset(HOUR_IN_MILLIS)
        expectedDateTime1HourAhead = "2017-01-02 03:00:00+01"
        assertEquals(expectedDateTime1HourAhead, DateUtils.format(d, format))
    }

    @Test
    fun testFormatDateTimeWithOffset() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2017, 0, 2, 2, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val d = c.time

        val tzProvider = MockTimezoneProvider()
        DateUtils.setTimezoneProvider(tzProvider)

        tzProvider.setOffset(HOUR_IN_MILLIS)
        val expectedDateTime1HourAhead = "2017-01-02T03:00:00.000+01"
        assertEquals(expectedDateTime1HourAhead, DateUtils.formatDateTime(d, DateUtils.FORMAT_ISO8601))

        tzProvider.setOffset(-3 * HOUR_IN_MILLIS)
        val expectedDateTime3HoursBehind = "2017-01-01T23:00:00.000-03"
        assertEquals(expectedDateTime3HoursBehind, DateUtils.formatDateTime(d, DateUtils.FORMAT_ISO8601))

        tzProvider.setOffset(0)
        val expectedDateTimeUTC = "2017-01-02T02:00:00.000Z"
        assertEquals(expectedDateTimeUTC, DateUtils.formatDateTime(d, DateUtils.FORMAT_ISO8601))

        DateUtils.resetTimezoneProvider()
    }

    @Test
    fun testFormatDateWithOffset() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2017, 0, 2, 2, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val d = c.time

        val tzProvider = MockTimezoneProvider()
        DateUtils.setTimezoneProvider(tzProvider)

        tzProvider.setOffset(HOUR_IN_MILLIS)
        val expectedDate1HourAhead = "2017-01-02"
        assertEquals(expectedDate1HourAhead, DateUtils.formatDate(d, DateUtils.FORMAT_ISO8601))

        tzProvider.setOffset(-3 * HOUR_IN_MILLIS)
        val expectedDate3HoursBehind = "2017-01-01"
        assertEquals(expectedDate3HoursBehind, DateUtils.formatDate(d, DateUtils.FORMAT_ISO8601))

        tzProvider.setOffset(0)
        val expectedDateUTC = "2017-01-02"
        assertEquals(expectedDateUTC, DateUtils.formatDate(d, DateUtils.FORMAT_ISO8601))

        DateUtils.resetTimezoneProvider()
    }

    @Test
    fun testFormatTimeWithOffset() {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        c.set(2017, 0, 2, 2, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        val d = c.time

        val tzProvider = MockTimezoneProvider()
        DateUtils.setTimezoneProvider(tzProvider)

        tzProvider.setOffset(HOUR_IN_MILLIS)
        val expectedTime1HourAhead = "03:00:00.000+01"
        assertEquals(expectedTime1HourAhead, DateUtils.formatTime(d, DateUtils.FORMAT_ISO8601))

        tzProvider.setOffset(-3 * HOUR_IN_MILLIS)
        val expectedTime3HoursBehind = "23:00:00.000-03"
        assertEquals(expectedTime3HoursBehind, DateUtils.formatTime(d, DateUtils.FORMAT_ISO8601))

        tzProvider.setOffset(0)
        val expectedTimeUTC = "02:00:00.000Z"
        assertEquals(expectedTimeUTC, DateUtils.formatTime(d, DateUtils.FORMAT_ISO8601))

        DateUtils.resetTimezoneProvider()
    }

    @Test
    fun testTimeParsingWithOffset() {
        testTimeParsingHelper("UTC")
        testTimeParsingHelper("EST")
        testTimeParsingHelper("Africa/Johannesburg")
        testTimeParsingHelper("Asia/Katmandu")
    }

    @Test
    fun testDateTimeParsingWithOffset() {
        testDateTimeParsingHelper("UTC")
        testDateTimeParsingHelper("EST")
        testDateTimeParsingHelper("Africa/Johannesburg")
        testDateTimeParsingHelper("Asia/Katmandu")
    }

    @Test
    fun testGetOffsetString() {
        var offset = 6 * 60 * 60 * 1000
        var expected = "+06"
        offsetStringTestHelper(offset, expected)

        offset = 0
        expected = "Z"
        offsetStringTestHelper(offset, expected)

        offset = (-5.5 * 60 * 60 * 1000).toInt()
        expected = "-05:30"
        offsetStringTestHelper(offset, expected)

        offset = (5.75 * 60 * 60 * 1000).toInt()
        expected = "+05:45"
        offsetStringTestHelper(offset, expected)
    }

}
