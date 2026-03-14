package org.javarosa.core.model.data.test

import org.javarosa.core.model.data.StringData
import org.junit.BeforeClass
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class StringDataTests {
    companion object {
        private lateinit var stringA: String
        private lateinit var stringB: String

        @BeforeClass
        @JvmStatic
        fun setUp() {
            stringA = "string A"
            stringB = "string B"
        }
    }

    @Test
    fun testGetData() {
        val data = StringData(stringA)
        assertEquals("StringData's getValue returned an incorrect String", data.getValue(), stringA)
    }

    @Test
    fun testSetData() {
        val data = StringData(stringA)
        data.setValue(stringB)

        assertTrue("StringData did not set value properly. Maintained old value.", data.getValue() != stringA)
        assertEquals("StringData did not properly set value ", data.getValue(), stringB)

        data.setValue(stringA)
        assertTrue("StringData did not set value properly. Maintained old value.", data.getValue() != stringB)
        assertEquals("StringData did not properly reset value ", data.getValue(), stringA)
    }

    @Test
    fun testNullData() {
        var exceptionThrown = false
        val data = StringData()
        data.setValue(stringA)
        try {
            data.setValue(null)
        } catch (e: NullPointerException) {
            exceptionThrown = true
        }
        assertTrue("StringData failed to throw an exception when setting null data", exceptionThrown)
        assertTrue("StringData overwrote existing value on incorrect input", data.getValue() == stringA)
    }
}
