package org.javarosa.core.model.data.test

import org.javarosa.core.model.QuestionDef
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.helper.Selection
import org.junit.BeforeClass
import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SelectOneDataTests {
    companion object {
        private lateinit var question: QuestionDef
        private lateinit var one: Selection
        private lateinit var two: Selection

        @BeforeClass
        @JvmStatic
        fun setUp() {
            question = QuestionDef()
            question.setID(57)

            for (i in 0 until 3) {
                question.addSelectChoice(SelectChoice("", "Selection$i", "Selection$i", false))
            }

            one = Selection("Selection1")
            one.attachChoice(question)
            two = Selection("Selection2")
            two.attachChoice(question)
        }
    }

    @Test
    fun testGetData() {
        val data = SelectOneData(one)
        assertEquals("SelectOneData's getValue returned an incorrect SelectOne", data.getValue(), one)
    }

    @Test
    fun testSetData() {
        val data = SelectOneData(one)
        data.setValue(two)

        assertTrue("SelectOneData did not set value properly. Maintained old value.", data.getValue() != one)
        assertEquals("SelectOneData did not properly set value ", data.getValue(), two)

        data.setValue(one)
        assertTrue("SelectOneData did not set value properly. Maintained old value.", data.getValue() != two)
        assertEquals("SelectOneData did not properly reset value ", data.getValue(), one)
    }

    @Test
    fun testNullData() {
        var exceptionThrown = false
        val data = SelectOneData()
        data.setValue(one)
        try {
            data.setValue(null)
        } catch (e: NullPointerException) {
            exceptionThrown = true
        }
        assertTrue("SelectOneData failed to throw an exception when setting null data", exceptionThrown)
        assertTrue("SelectOneData overwrote existing value on incorrect input", data.getValue() == one)
    }
}
