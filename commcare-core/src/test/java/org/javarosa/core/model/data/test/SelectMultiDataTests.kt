package org.javarosa.core.model.data.test

import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.helper.Selection
import org.junit.BeforeClass
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SelectMultiDataTests {
    companion object {
        private lateinit var question: QuestionDef

        private lateinit var one: Selection
        private lateinit var two: Selection
        private lateinit var three: Selection

        private lateinit var firstTwo: ArrayList<Selection>
        private lateinit var lastTwo: ArrayList<Selection>
        private lateinit var invalid: ArrayList<Any>

        @BeforeClass
        @JvmStatic
        fun setUp() {
            question = QuestionDef()

            for (i in 0 until 4) {
                question.addSelectChoice(SelectChoice("", "Selection$i", "Selection $i", false))
            }

            one = Selection("Selection 1")
            one.attachChoice(question)
            two = Selection("Selection 2")
            two.attachChoice(question)
            three = Selection("Selection 3")
            three.attachChoice(question)

            firstTwo = arrayListOf(one, two)
            lastTwo = arrayListOf(two, three)

            invalid = arrayListOf(three, 12, one)
        }
    }

    @Test
    fun testGetData() {
        val data = SelectOneData(one)
        assertEquals("SelectOneData's getValue returned an incorrect SelectOne", data.getValue(), one)
    }

    @Test
    fun testSetData() {
        val data = SelectMultiData(firstTwo)
        data.setValue(lastTwo)

        assertTrue("SelectMultiData did not set value properly. Maintained old value.", data.getValue() != firstTwo)
        assertEquals("SelectMultiData did not properly set value ", data.getValue(), lastTwo)

        data.setValue(firstTwo)
        assertTrue("SelectMultiData did not set value properly. Maintained old value.", data.getValue() != lastTwo)
        assertEquals("SelectMultiData did not properly reset value ", data.getValue(), firstTwo)
    }

    @Test
    fun testNullData() {
        var exceptionThrown = false
        val data = SelectMultiData()
        data.setValue(firstTwo)
        try {
            data.setValue(null)
        } catch (e: NullPointerException) {
            exceptionThrown = true
        }
        assertTrue("SelectMultiData failed to throw an exception when setting null data", exceptionThrown)
        assertTrue("SelectMultiData overwrote existing value on incorrect input", data.getValue() == firstTwo)
    }

    @Test
    fun testVectorImmutability() {
        val data = SelectMultiData(firstTwo)
        val copy = firstTwo.toTypedArray()
        firstTwo[0] = two
        firstTwo.removeAt(1)

        @Suppress("UNCHECKED_CAST")
        val internal = data.getValue() as ArrayList<Selection>

        assertVectorIdentity("External Reference: ", internal, copy)

        data.setValue(lastTwo)
        @Suppress("UNCHECKED_CAST")
        val start = data.getValue() as ArrayList<Selection>

        val external = start.toTypedArray()

        start.removeAt(1)
        start[0] = one

        @Suppress("UNCHECKED_CAST")
        assertVectorIdentity("Internal Reference: ", data.getValue() as ArrayList<Selection>, external)
    }

    private fun assertVectorIdentity(messageHeader: String, v: ArrayList<Selection>, a: Array<Selection>) {
        assertEquals(
            "${messageHeader}SelectMultiData's internal representation was violated. ArrayList size changed.",
            v.size, a.size
        )

        for (i in v.indices) {
            val internalValue = v[i]
            val copyValue = a[i]
            assertEquals(
                "${messageHeader}SelectMultiData's internal representation was violated. Element ${i}changed.",
                internalValue, copyValue
            )
        }
    }

    @Test
    fun testBadDataTypes() {
        var failure = false
        var data = SelectMultiData(firstTwo)
        try {
            @Suppress("UNCHECKED_CAST")
            data.setValue(invalid as ArrayList<Selection>)
            @Suppress("UNCHECKED_CAST")
            data = SelectMultiData(invalid as ArrayList<Selection>)
        } catch (e: Exception) {
            failure = true
        }
        assertTrue("SelectMultiData did not throw a proper exception while being set to invalid data.", failure)

        val values = firstTwo.toTypedArray()
        @Suppress("UNCHECKED_CAST")
        assertVectorIdentity("Ensure not overwritten: ", data.getValue() as ArrayList<Selection>, values)
    }
}
