package org.javarosa.core.model.data.test

import org.javarosa.core.model.data.IntegerData
import org.junit.BeforeClass
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class IntegerDataTests {
    companion object {
        private var one: Int = 0
        private var two: Int = 0

        @BeforeClass
        @JvmStatic
        fun setUp() {
            one = 1
            two = 2
        }
    }

    @Test
    fun testGetData() {
        val data = IntegerData(one)
        assertEquals("IntegerData's getValue returned an incorrect integer", data.getValue(), one)
    }

    @Test
    fun testSetData() {
        val data = IntegerData(one)
        data.setValue(two)

        assertTrue("IntegerData did not set value properly. Maintained old value.", data.getValue() != one)
        assertEquals("IntegerData did not properly set value ", data.getValue(), two)

        data.setValue(one)
        assertTrue("IntegerData did not set value properly. Maintained old value.", data.getValue() != two)
        assertEquals("IntegerData did not properly reset value ", data.getValue(), one)
    }

    @Test
    fun testNullData() {
        var exceptionThrown = false
        val data = IntegerData()
        data.setValue(one)
        try {
            data.setValue(null)
        } catch (e: NullPointerException) {
            exceptionThrown = true
        }
        assertTrue("IntegerData failed to throw an exception when setting null data", exceptionThrown)
        assertTrue("IntegerData overwrote existing value on incorrect input", data.getValue() == one)
    }
}
