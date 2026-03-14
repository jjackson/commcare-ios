package org.javarosa.core.model.data.test

import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.utils.DateUtils
import org.junit.Before
import org.junit.Test

import java.util.Date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DateDataTests {
    private lateinit var today: Date
    private lateinit var notToday: Date

    @Before
    fun setUp() {
        today = DateUtils.roundDate(Date())
        notToday = DateUtils.roundDate(Date(today.time - today.time / 2))
    }

    @Test
    fun testGetData() {
        val data = DateData(today)
        assertEquals("DateData's getValue returned an incorrect date", data.getValue(), today)
        val temp = Date(today.time)
        today.time = 1234
        assertEquals("DateData's getValue was mutated incorrectly", data.getValue(), temp)

        val rep = data.getValue() as Date
        rep.time = rep.time - 1000

        assertEquals("DateData's getValue was mutated incorrectly", data.getValue(), temp)
    }

    @Test
    fun testSetData() {
        val data = DateData(notToday)
        data.setValue(today)

        assertTrue("DateData did not set value properly. Maintained old value.", data.getValue() != notToday)
        assertEquals("DateData did not properly set value ", data.getValue(), today)

        data.setValue(notToday)
        assertTrue("DateData did not set value properly. Maintained old value.", data.getValue() != today)
        assertEquals("DateData did not properly reset value ", data.getValue(), notToday)

        val temp = Date(notToday.time)
        notToday.time = notToday.time - 1324

        assertEquals("DateData's value was mutated incorrectly", data.getValue(), temp)
    }

    @Test
    fun testDisplay() {
        // We don't actually want this, because the Date's getDisplayText code should be moved to a library
    }

    @Test
    fun testNullData() {
        var exceptionThrown = false
        val data = DateData()
        data.setValue(today)
        try {
            data.setValue(null)
        } catch (e: NullPointerException) {
            exceptionThrown = true
        }
        assertTrue("DateData failed to throw an exception when setting null data", exceptionThrown)
        assertTrue("DateData overwrote existing value on incorrect input", data.getValue() == today)
    }
}
