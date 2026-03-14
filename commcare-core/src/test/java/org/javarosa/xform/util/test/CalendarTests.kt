package org.javarosa.xform.util.test

import org.javarosa.core.model.utils.DateUtils
import org.javarosa.core.model.utils.PlatformTimeZone
import org.javarosa.core.model.utils.TimezoneProvider
import org.javarosa.core.services.locale.Localization
import org.javarosa.core.services.locale.TableLocaleSource
import org.javarosa.xform.util.CalendarUtils
import org.javarosa.xform.util.UniversalDate
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class CalendarTests {

    @Before
    fun configureLocaleForCalendar() {
        Localization.getGlobalLocalizerAdvanced().addAvailableLocale("default")
        Localization.setLocale("default")
        val localeData = TableLocaleSource()
        localeData.setLocaleMapping("ethiopian_months",
                "Mäskäräm,T'ïk'ïmt,Hïdar,Tahsas,T'ïr,Yäkatit,Mägabit,Miyaziya,Gïnbot,Säne,Hämle,Nähäse,P'agume")
        localeData.setLocaleMapping("nepali_months",
                "Baishakh,Jestha,Ashadh,Shrawan,Bhadra,Ashwin,Kartik,Mangsir,Poush,Magh,Falgun,Chaitra")
        Localization.getGlobalLocalizerAdvanced().registerLocaleResource("default", localeData)
    }

    companion object {
        private fun wrap(tz: TimeZone): PlatformTimeZone = PlatformTimeZone(tz)

        private fun assertSameDate(a: UniversalDate, b: UniversalDate) {
            assertEquals(a.day, b.day)
            assertEquals(a.month, b.month)
            assertEquals(a.year, b.year)
        }
    }

    @Test
    fun testTimesFallOnSameDate() {
        val nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45")

        val nepaliMiddleOfDayDate = Calendar.getInstance(nepaliTimeZone)
        nepaliMiddleOfDayDate.set(2007, Calendar.JULY, 7, 18, 46)

        val nepaliBeginningOfDayDate = Calendar.getInstance(nepaliTimeZone)
        nepaliBeginningOfDayDate.set(2007, Calendar.JULY, 7, 0, 0)

        val middleOfDay = CalendarUtils.fromMillis(nepaliMiddleOfDayDate.timeInMillis, wrap(nepaliTimeZone))
        val beginningOfDay = CalendarUtils.fromMillis(nepaliBeginningOfDayDate.timeInMillis, wrap(nepaliTimeZone))
        assertSameDate(middleOfDay, beginningOfDay)

        val nepaliEndOfDayDate = Calendar.getInstance(nepaliTimeZone)
        nepaliEndOfDayDate.set(2007, Calendar.JULY, 7, 23, 59, 59)
        val endOfDay = CalendarUtils.fromMillis(nepaliEndOfDayDate.timeInMillis, wrap(nepaliTimeZone))
        assertSameDate(endOfDay, beginningOfDay)
    }

    @Test
    fun testConvertToNepaliString() {
        val mockTimeZoneProvider = MockTimeZoneProvider(TimeZone.getTimeZone("Europe/Madrid"))
        DateUtils.setTimezoneProvider(mockTimeZoneProvider)
        var timeZone = mockTimeZoneProvider.getTimezone()
        var millis = CalendarUtils.toMillisFromJavaEpoch(2081, 6, 16, timeZone)
        var nepaliDateStr = CalendarUtils.convertToNepaliString(Date(millis), null)
        assertEquals("16 Ashwin 2081", nepaliDateStr)

        mockTimeZoneProvider.setTimezone(TimeZone.getTimeZone("Asia/Kathmandu"))
        timeZone = mockTimeZoneProvider.getTimezone()
        millis = CalendarUtils.toMillisFromJavaEpoch(2081, 6, 16, timeZone)
        nepaliDateStr = CalendarUtils.convertToNepaliString(Date(millis), null)
        assertEquals("16 Ashwin 2081", nepaliDateStr)

        mockTimeZoneProvider.setTimezone(TimeZone.getTimeZone("America/Chicago"))
        timeZone = mockTimeZoneProvider.getTimezone()
        millis = CalendarUtils.toMillisFromJavaEpoch(2081, 6, 16, timeZone)
        nepaliDateStr = CalendarUtils.convertToNepaliString(Date(millis), null)
        assertEquals("16 Ashwin 2081", nepaliDateStr)
        DateUtils.resetTimezoneProvider()
    }

    @Test
    fun testDateCalcsAreSensitiveToCurrentTimezone() {
        val nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45")
        val mexicanTimeZone = TimeZone.getTimeZone("GMT-06:00")
        val nepalCal = Calendar.getInstance(nepaliTimeZone)
        nepalCal.set(2007, Calendar.JULY, 7, 18, 46)
        val mexicoCal = Calendar.getInstance(mexicanTimeZone)
        mexicoCal.set(2007, Calendar.JULY, 7, 18, 46)

        val mexicanDate = CalendarUtils.fromMillis(mexicoCal.timeInMillis, wrap(mexicanTimeZone))
        val nepaliDate = CalendarUtils.fromMillis(nepalCal.timeInMillis, wrap(nepaliTimeZone))
        assertSameDate(nepaliDate, mexicanDate)
    }

    @Test
    fun testUnpackingDateInDifferentTimezone() {
        val nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45")
        val mexicanTimeZone = TimeZone.getTimeZone("GMT-06:00")
        val mexicoCal = Calendar.getInstance(mexicanTimeZone)
        mexicoCal.set(2007, Calendar.JULY, 7, 18, 46)

        val mexicanDate = CalendarUtils.fromMillis(mexicoCal.timeInMillis, wrap(mexicanTimeZone))
        val time = CalendarUtils.toMillisFromJavaEpoch(mexicanDate.year, mexicanDate.month, mexicanDate.day,
                wrap(mexicanTimeZone))
        val rebuiltDateInUsingDifferentTimezone = CalendarUtils.fromMillis(time, wrap(nepaliTimeZone))
        assertSameDate(rebuiltDateInUsingDifferentTimezone, mexicanDate)
    }

    @Test
    fun testBaseDateSerialization() {
        val nycTimeZone = TimeZone.getTimeZone("America/New_York")

        val dayInNewYork = Calendar.getInstance(nycTimeZone)
        dayInNewYork.set(2007, Calendar.JULY, 7)
        val nycTime = CalendarUtils.fromMillis(dayInNewYork.timeInMillis, wrap(nycTimeZone))

        var time = CalendarUtils.toMillisFromJavaEpoch(nycTime.year, nycTime.month, nycTime.day, wrap(nycTimeZone))
        val unpackedNycTime = CalendarUtils.fromMillis(time, wrap(nycTimeZone))
        assertSameDate(nycTime, unpackedNycTime)

        val nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45")
        time = CalendarUtils.toMillisFromJavaEpoch(nycTime.year, nycTime.month, nycTime.day, wrap(nepaliTimeZone))
        val unpackedNepaliTime = CalendarUtils.fromMillis(time, wrap(nepaliTimeZone))
        assertSameDate(nycTime, unpackedNepaliTime)
    }

    @Test
    fun serializeUniversalDateViaMillisTest() {
        // India
        val indiaTimeZone = TimeZone.getTimeZone("GMT+05:00")
        val nepaliDate = UniversalDate(2073, 5, 2, 0)
        var normalizedTime = CalendarUtils.toMillisFromJavaEpoch(2073, 5, 2, wrap(indiaTimeZone))
        var date = Date(normalizedTime)
        var deserializedNepaliDate = CalendarUtils.fromMillis(date.time, wrap(indiaTimeZone))
        assertSameDate(nepaliDate, deserializedNepaliDate)

        // Boston
        val bostonTimeZone = TimeZone.getTimeZone("GMT-04:00")
        normalizedTime = CalendarUtils.toMillisFromJavaEpoch(2073, 5, 2, wrap(bostonTimeZone))
        date = Date(normalizedTime)
        deserializedNepaliDate = CalendarUtils.fromMillis(date.time, wrap(bostonTimeZone))
        assertSameDate(nepaliDate, deserializedNepaliDate)
    }

    private inner class MockTimeZoneProvider(private var timeZone: TimeZone) : TimezoneProvider() {

        fun setTimezone(timeZone: TimeZone) {
            this.timeZone = timeZone
        }

        override fun getTimezone(): PlatformTimeZone = PlatformTimeZone(timeZone)
    }
}
