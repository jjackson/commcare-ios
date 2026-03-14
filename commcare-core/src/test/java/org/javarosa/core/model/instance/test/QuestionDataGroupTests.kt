package org.javarosa.core.model.instance.test

import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class QuestionDataGroupTests {

    private lateinit var stringData: StringData
    private lateinit var integerData: IntegerData
    private lateinit var stringElement: TreeElement
    private lateinit var intElement: TreeElement
    private lateinit var group: TreeElement

    companion object {
        private const val stringElementName = "String Data Element"
        private const val groupName = "TestGroup"
    }

    @Before
    fun setUp() {
        stringData = StringData("Answer Value")
        integerData = IntegerData(4)

        intElement = TreeElement("intElement")
        intElement.setValue(integerData)

        stringElement = TreeElement(stringElementName)
        stringElement.setValue(stringData)

        group = TreeElement(groupName)
    }

    @Test
    fun testIsLeaf() {
        assertTrue("A Group with no children should report being a leaf", group.isLeaf)
        group.addChild(stringElement)
        assertFalse("A Group with children should not report being a leaf", group.isLeaf)
    }

    @Test
    fun testGetName() {
        val name = "TestGroup"
        assertEquals("Question Data Group did not properly get its name", group.getName(), name)
        group.addChild(stringElement)
        assertEquals("Question Data Group's name was changed improperly", group.getName(), name)
    }

    @Test
    fun testSetName() {
        val name = "TestGroup"
        group = TreeElement(name)
        val newName = "TestGroupNew"
        group.setName(newName)
        assertEquals("Question Data Group did not properly get its name", group.getName(), newName)
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

    @Test
    fun testSuperclassMethods() {
        // stringElement should not have a root at this point.
        // TODO: Implement tests for the 'attribute' system.
    }

    @Test
    fun testAddLeafChild() {
        var added = false
        try {
            group.addChild(stringElement)
            group.getChildAt(0)
            assertTrue("Added element was not in Question Data Group's children!",
                group.getChildAt(0) == stringElement)
        } catch (e: RuntimeException) {
            if (!added) {
                fail("Group did not report success adding a valid child")
            }
        }

        try {
            val leafGroup = TreeElement("leaf group")
            group.addChild(leafGroup)
            assertTrue("Added element was not in Question Data Group's children",
                group.getChildAt(1) == leafGroup)
        } catch (e: RuntimeException) {
            if (!added) {
                fail("Group did not report success adding a valid child")
            }
        }
    }

    @Test
    fun testAddTreeChild() {
        val subElement = TreeElement("SubElement")
        subElement.addChild(stringElement)
        subElement.addChild(intElement)
    }
}
