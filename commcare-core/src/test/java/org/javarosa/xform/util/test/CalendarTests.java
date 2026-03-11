package org.javarosa.xform.util.test;

import org.javarosa.core.model.utils.DateUtils;
import org.javarosa.core.model.utils.PlatformTimeZone;
import org.javarosa.core.model.utils.TimezoneProvider;
import org.javarosa.core.services.locale.Localization;
import org.javarosa.core.services.locale.TableLocaleSource;
import org.javarosa.xform.util.CalendarUtils;
import org.javarosa.xform.util.UniversalDate;
import org.junit.Before;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
public class CalendarTests {

    @Before
    public void configureLocaleForCalendar() {
        Localization.getGlobalLocalizerAdvanced().addAvailableLocale("default");
        Localization.setLocale("default");
        TableLocaleSource localeData = new TableLocaleSource();
        localeData.setLocaleMapping("ethiopian_months",
                "Mäskäräm,T'ïk'ïmt,Hïdar,Tahsas,T'ïr,Yäkatit,Mägabit,Miyaziya,Gïnbot,Säne,Hämle,Nähäse,P'agume");
        localeData.setLocaleMapping("nepali_months",
                "Baishakh,Jestha,Ashadh,Shrawan,Bhadra,Ashwin,Kartik,Mangsir,Poush,Magh,Falgun,Chaitra");
        Localization.getGlobalLocalizerAdvanced().registerLocaleResource("default", localeData);
    }

    private static PlatformTimeZone wrap(TimeZone tz) {
        return new PlatformTimeZone(tz);
    }

    @Test
    public void testTimesFallOnSameDate() {
        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");

        Calendar nepaliMiddleOfDayDate = Calendar.getInstance(nepaliTimeZone);
        nepaliMiddleOfDayDate.set(2007, Calendar.JULY, 7, 18, 46);

        Calendar nepaliBeginningOfDayDate = Calendar.getInstance(nepaliTimeZone);
        nepaliBeginningOfDayDate.set(2007, Calendar.JULY, 7, 0, 0);

        UniversalDate middleOfDay = CalendarUtils.fromMillis(nepaliMiddleOfDayDate.getTimeInMillis(),
                wrap(nepaliTimeZone));
        UniversalDate beginningOfDay = CalendarUtils.fromMillis(nepaliBeginningOfDayDate.getTimeInMillis(),
                wrap(nepaliTimeZone));
        assertSameDate(middleOfDay, beginningOfDay);

        Calendar nepaliEndOfDayDate = Calendar.getInstance(nepaliTimeZone);
        nepaliEndOfDayDate.set(2007, Calendar.JULY, 7, 23, 59, 59);
        UniversalDate endOfDay = CalendarUtils.fromMillis(nepaliEndOfDayDate.getTimeInMillis(), wrap(nepaliTimeZone));
        assertSameDate(endOfDay, beginningOfDay);
    }

    // millis <=> date is different in every timezone
    @Test
    public void testConvertToNepaliString() {
        MockTimeZoneProvider mockTimeZoneProvider = new MockTimeZoneProvider(TimeZone.getTimeZone("Europe/Madrid"));
        DateUtils.setTimezoneProvider(mockTimeZoneProvider);
        PlatformTimeZone timeZone = mockTimeZoneProvider.getTimezone();
        // this is what Nepali widget uses to calculate the millis from date fields
        long millis = CalendarUtils.toMillisFromJavaEpoch(2081, 6, 16, timeZone);
        String nepaliDateStr = CalendarUtils.convertToNepaliString(new Date(millis), null);
        assertEquals( "16 Ashwin 2081", nepaliDateStr);


        mockTimeZoneProvider.setTimezone(TimeZone.getTimeZone("Asia/Kathmandu"));
        timeZone = mockTimeZoneProvider.getTimezone();
        millis = CalendarUtils.toMillisFromJavaEpoch(2081, 6, 16, timeZone);
        nepaliDateStr = CalendarUtils.convertToNepaliString(new Date(millis), null);
        assertEquals( "16 Ashwin 2081", nepaliDateStr);


        mockTimeZoneProvider.setTimezone(TimeZone.getTimeZone("America/Chicago"));
        timeZone = mockTimeZoneProvider.getTimezone();
        millis = CalendarUtils.toMillisFromJavaEpoch(2081, 6, 16, timeZone);
        nepaliDateStr = CalendarUtils.convertToNepaliString(new Date(millis), null);
        assertEquals( "16 Ashwin 2081", nepaliDateStr);
        DateUtils.resetTimezoneProvider();
    }

    private static void assertSameDate(UniversalDate a, UniversalDate b) {
        assertEquals(a.day, b.day);
        assertEquals(a.month, b.month);
        assertEquals(a.year, b.year);
    }

    @Test
    public void testDateCalcsAreSensitiveToCurrentTimezone() {
        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");
        TimeZone mexicanTimeZone = TimeZone.getTimeZone("GMT-06:00");
        Calendar nepalCal = Calendar.getInstance(nepaliTimeZone);
        nepalCal.set(2007, Calendar.JULY, 7, 18, 46);
        Calendar mexicoCal = Calendar.getInstance(mexicanTimeZone);
        mexicoCal.set(2007, Calendar.JULY, 7, 18, 46);

        UniversalDate mexicanDate = CalendarUtils.fromMillis(mexicoCal.getTimeInMillis(), wrap(mexicanTimeZone));
        UniversalDate nepaliDate = CalendarUtils.fromMillis(nepalCal.getTimeInMillis(), wrap(nepaliTimeZone));
        assertSameDate(nepaliDate, mexicanDate);
    }

    @Test
    public void testUnpackingDateInDifferentTimezone() {
        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");
        TimeZone mexicanTimeZone = TimeZone.getTimeZone("GMT-06:00");
        Calendar mexicoCal = Calendar.getInstance(mexicanTimeZone);
        mexicoCal.set(2007, Calendar.JULY, 7, 18, 46);

        UniversalDate mexicanDate = CalendarUtils.fromMillis(mexicoCal.getTimeInMillis(), wrap(mexicanTimeZone));
        long time = CalendarUtils.toMillisFromJavaEpoch(mexicanDate.year, mexicanDate.month, mexicanDate.day,
                wrap(mexicanTimeZone));
        UniversalDate rebuiltDateInUsingDifferentTimezone = CalendarUtils.fromMillis(time, wrap(nepaliTimeZone));
        assertSameDate(rebuiltDateInUsingDifferentTimezone, mexicanDate);
    }

    @Test
    public void testBaseDateSerialization() {
        TimeZone nycTimeZone = TimeZone.getTimeZone("America/New_York");

        Calendar dayInNewYork = Calendar.getInstance(nycTimeZone);
        dayInNewYork.set(2007, Calendar.JULY, 7);
        UniversalDate nycTime = CalendarUtils.fromMillis(dayInNewYork.getTimeInMillis(), wrap(nycTimeZone));

        long time = CalendarUtils.toMillisFromJavaEpoch(nycTime.year, nycTime.month, nycTime.day, wrap(nycTimeZone));
        UniversalDate unpackedNycTime = CalendarUtils.fromMillis(time, wrap(nycTimeZone));
        assertSameDate(nycTime, unpackedNycTime);

        TimeZone nepaliTimeZone = TimeZone.getTimeZone("GMT+05:45");
        time = CalendarUtils.toMillisFromJavaEpoch(nycTime.year, nycTime.month, nycTime.day, wrap(nepaliTimeZone));
        UniversalDate unpackedNepaliTime = CalendarUtils.fromMillis(time, wrap(nepaliTimeZone));
        assertSameDate(nycTime, unpackedNepaliTime);
    }

    @Test
    public void serializeUniversalDateViaMillisTest() {
        // India
        TimeZone indiaTimeZone = TimeZone.getTimeZone("GMT+05:00");
        UniversalDate nepaliDate = new UniversalDate(2073, 5, 2, 0);
        long normalizedTime = CalendarUtils.toMillisFromJavaEpoch(2073, 5, 2, wrap(indiaTimeZone));
        Date date = new Date(normalizedTime);
        UniversalDate deserializedNepaliDate = CalendarUtils.fromMillis(date.getTime(), wrap(indiaTimeZone));
        assertSameDate(nepaliDate, deserializedNepaliDate);

        // Boston
        TimeZone bostonTimeZone = TimeZone.getTimeZone("GMT-04:00");
        normalizedTime = CalendarUtils.toMillisFromJavaEpoch(2073, 5, 2, wrap(bostonTimeZone));
        date = new Date(normalizedTime);
        deserializedNepaliDate = CalendarUtils.fromMillis(date.getTime(), wrap(bostonTimeZone));
        assertSameDate(nepaliDate, deserializedNepaliDate);
    }

    private class MockTimeZoneProvider extends TimezoneProvider {

        private TimeZone timeZone;

        public MockTimeZoneProvider(TimeZone timeZone) {
            this.timeZone = timeZone;
        }

        public void setTimezone(TimeZone timeZone) {
            this.timeZone = timeZone;
        }

        @Override
        public PlatformTimeZone getTimezone() {
            return new PlatformTimeZone(timeZone);
        }
    }
}
