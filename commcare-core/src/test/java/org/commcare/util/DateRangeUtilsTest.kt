package org.commcare.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DateRangeUtilsTest {

    @Test
    @Throws(Exception::class)
    fun testDateConversion() {
        val dateRange = "2020-02-15 to 2021-03-18"
        val formattedDateRange = DateRangeUtils.formatDateRangeAnswer(dateRange)
        assertEquals("__range__2020-02-15__2021-03-18", formattedDateRange)
        assertEquals(dateRange, DateRangeUtils.getHumanReadableDateRange(formattedDateRange))
    }
}
