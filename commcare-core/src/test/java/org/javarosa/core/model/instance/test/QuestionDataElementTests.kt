package org.javarosa.core.model.instance.test

import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class QuestionDataElementTests {

    companion object {
        private const val stringElementName = "String Data Element"

        private lateinit var stringData: StringData
        private lateinit var integerData: IntegerData
        private lateinit var stringElement: TreeElement
        private lateinit var intElement: TreeElement

        @BeforeClass
        @JvmStatic
        fun setUp() {
            stringData = StringData("Answer Value")
            integerData = IntegerData(4)

            intElement = TreeElement("intElement")
            intElement.setValue(integerData)

            stringElement = TreeElement(stringElementName)
            stringElement.setValue(stringData)
        }
    }

    @Test
    fun testIsLeaf() {
        assertTrue("Question Data Element returned negative for being a leaf", stringElement.isLeaf)
    }

    @Test
    fun testGetName() {
        assertEquals("Question Data Element 'string' did not properly get its name",
            stringElement.getName(), stringElementName)
    }

    @Test
    fun testSetName() {
        val newName = "New Name"
        stringElement.setName(newName)
        assertEquals("Question Data Element 'string' did not properly set its name",
            stringElement.getName(), newName)
    }

    @Test
    fun testGetValue() {
        val data = stringElement.getValue()
        assertEquals("Question Data Element did not return the correct value", data, stringData)
    }

    @Test
    fun testSetValue() {
        stringElement.setValue(integerData)
        assertEquals("Question Data Element did not set value correctly",
            stringElement.getValue(), integerData)

        try {
            stringElement.setValue(null)
        } catch (e: Exception) {
            fail("Question Data Element did not allow for its value to be set as null")
        }

        assertEquals("Question Data Element did not return a null value correctly",
            stringElement.getValue(), null)
    }

    @Test
    fun testAcceptsVisitor() {
        var visitorAccepted = false
        var dispatchedWrong = false

        val sampleVisitor = object : ITreeVisitor {
            override fun visit(tree: FormInstance) {
                dispatchedWrong = true
            }

            override fun visit(element: AbstractTreeElement) {
                visitorAccepted = true
            }
        }

        stringElement.accept(sampleVisitor)
        assertTrue("The visitor's visit method was not called correctly by the QuestionDataElement",
            visitorAccepted)
        assertFalse("The visitor was dispatched incorrectly by the QuestionDataElement",
            dispatchedWrong)
    }
}
