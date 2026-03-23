package org.commcare.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for CalendarWidget: Ethiopian and Nepali calendar support.
 */
class CalendarWidgetTest {

    @Test
    fun testEthiopianCalendarDetected() {
        assertTrue(CalendarWidget.isAlternativeCalendar("ethiopian"))
        assertTrue(CalendarWidget.isAlternativeCalendar("some ethiopian calendar"))
    }

    @Test
    fun testNepaliCalendarDetected() {
        assertTrue(CalendarWidget.isAlternativeCalendar("nepali"))
        assertTrue(CalendarWidget.isAlternativeCalendar("nepali-date"))
    }

    @Test
    fun testGregorianNotAlternative() {
        assertFalse(CalendarWidget.isAlternativeCalendar(null))
        assertFalse(CalendarWidget.isAlternativeCalendar(""))
        assertFalse(CalendarWidget.isAlternativeCalendar("default"))
    }

    @Test
    fun testEthiopianMonthNames() {
        val months = CalendarWidget.getMonthNames("ethiopian")
        assertEquals(13, months.size, "Ethiopian calendar has 13 months")
        assertEquals("Meskerem", months[0])
        assertEquals("Pagume", months[12])
    }

    @Test
    fun testNepaliMonthNames() {
        val months = CalendarWidget.getMonthNames("nepali")
        assertEquals(12, months.size, "Nepali calendar has 12 months")
        assertEquals("Baishakh", months[0])
        assertEquals("Chaitra", months[11])
    }

    @Test
    fun testUnknownCalendarEmptyMonths() {
        val months = CalendarWidget.getMonthNames(null)
        assertTrue(months.isEmpty())
        val months2 = CalendarWidget.getMonthNames("gregorian")
        assertTrue(months2.isEmpty())
    }

    @Test
    fun testFormatEthiopianDate() {
        val formatted = CalendarWidget.formatConvertedDate(2016, 1, 15, "ethiopian")
        assertEquals("Meskerem 15, 2016", formatted)
    }

    @Test
    fun testFormatNepaliDate() {
        val formatted = CalendarWidget.formatConvertedDate(2082, 6, 20, "nepali")
        assertEquals("Ashwin 20, 2082", formatted)
    }

    @Test
    fun testFormatFallbackForUnknownCalendar() {
        val formatted = CalendarWidget.formatConvertedDate(2026, 3, 5, null)
        assertEquals("2026-03-05", formatted)
    }

    @Test
    fun testGetCalendarNameEthiopian() {
        assertEquals("Ethiopian", CalendarWidget.getCalendarName("ethiopian"))
    }

    @Test
    fun testGetCalendarNameNepali() {
        assertEquals("Nepali", CalendarWidget.getCalendarName("nepali"))
    }

    @Test
    fun testGetCalendarNameNull() {
        assertNull(CalendarWidget.getCalendarName(null))
        assertNull(CalendarWidget.getCalendarName(""))
        assertNull(CalendarWidget.getCalendarName("default"))
    }

    @Test
    fun testEthiopianMonthOutOfRange() {
        // Month 14 is out of range for Ethiopian (13 months)
        // Should fall back to ISO format
        val formatted = CalendarWidget.formatConvertedDate(2016, 14, 1, "ethiopian")
        assertEquals("2016-14-01", formatted)
    }
}
