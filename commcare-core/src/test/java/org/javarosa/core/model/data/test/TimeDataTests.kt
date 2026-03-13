package org.javarosa.core.model.data.test

import org.javarosa.core.model.data.TimeData
import org.javarosa.core.model.data.UncastData
import org.junit.BeforeClass
import org.junit.Test

import java.util.Date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class TimeDataTests {
    companion object {
        private lateinit var now: Date
        private lateinit var minusOneHour: Date

        @BeforeClass
        @JvmStatic
        fun setUp() {
            now = Date()
            minusOneHour = Date(Date().time - (1000 * 60))
        }
    }

    @Test
    fun testGetData() {
        val data = TimeData(now)
        assertEquals("TimeData's getValue returned an incorrect Time", data.getValue(), now)
        val temp = Date(now.time)
        now.time = 1234
        assertEquals("TimeData's getValue was mutated incorrectly", data.getValue(), temp)

        val rep = data.getValue() as Date
        rep.time = rep.time - 1000

        assertEquals("TimeData's getValue was mutated incorrectly", data.getValue(), temp)
    }

    @Test
    fun testSetData() {
        val data = TimeData(now)
        data.setValue(minusOneHour)

        assertTrue("TimeData did not set value properly. Maintained old value.", data.getValue() != now)
        assertEquals("TimeData did not properly set value ", data.getValue(), minusOneHour)

        data.setValue(now)
        assertTrue("TimeData did not set value properly. Maintained old value.", data.getValue() != minusOneHour)
        assertEquals("TimeData did not properly reset value ", data.getValue(), now)

        val temp = Date(now.time)
        now.time = now.time - 1324

        assertEquals("TimeData's value was mutated incorrectly", data.getValue(), temp)
    }

    @Test
    fun testNullData() {
        var exceptionThrown = false
        val data = TimeData()
        data.setValue(now)
        try {
            data.setValue(null)
        } catch (e: NullPointerException) {
            exceptionThrown = true
        }
        assertTrue("TimeData failed to throw an exception when setting null data", exceptionThrown)
        assertTrue("TimeData overwrote existing value on incorrect input", data.getValue() == now)
    }

    @Test
    fun testIdentity() {
        val data = TimeData().cast(UncastData("10:00"))
        assertEquals("10:00", data.getDisplayText())
    }
}
