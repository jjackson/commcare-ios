package org.javarosa.core.model

import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.data.UncastData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Cross-platform tests for FormDef basic operations.
 * Runs on both JVM and iOS targets.
 */
class FormDefTest {

    @Test
    fun createFormDefWithTitle() {
        val formDef = FormDef()
        formDef.setTitle("My Test Form")
        assertEquals("My Test Form", formDef.getTitle())
    }

    @Test
    fun formDefDefaultTitleIsNull() {
        val formDef = FormDef()
        assertNull(formDef.getTitle())
    }

    @Test
    fun setAndGetName() {
        val formDef = FormDef()
        formDef.setName("registration_form")
        assertEquals("registration_form", formDef.getName())
    }

    @Test
    fun setAndGetId() {
        val formDef = FormDef()
        formDef.setID(42)
        assertEquals(42, formDef.getID())
    }

    @Test
    fun defaultIdIsNegativeOne() {
        val formDef = FormDef()
        assertEquals(-1, formDef.getID())
    }

    @Test
    fun setMainInstance() {
        val formDef = FormDef()
        val root = TreeElement("data", 0)
        val nameNode = TreeElement("name", 0)
        nameNode.setValue(UncastData("Test"))
        root.addChild(nameNode)

        val instance = FormInstance(root)
        formDef.setInstance(instance)

        val mainInstance = formDef.getMainInstance()
        assertNotNull(mainInstance)
    }

    @Test
    fun mainInstanceRootHasCorrectStructure() {
        val formDef = FormDef()
        val root = TreeElement("data", 0)
        val field1 = TreeElement("field1", 0)
        field1.setValue(UncastData("value1"))
        root.addChild(field1)
        val field2 = TreeElement("field2", 0)
        field2.setValue(UncastData("value2"))
        root.addChild(field2)

        val instance = FormInstance(root)
        formDef.setInstance(instance)

        val mainInstance = formDef.getMainInstance()
        assertNotNull(mainInstance)
        val instanceRoot = mainInstance.getRoot()
        assertEquals("data", instanceRoot.getName())
        assertEquals(2, instanceRoot.getNumChildren())
    }

    @Test
    fun mainInstanceIsNullBeforeSetInstance() {
        val formDef = FormDef()
        assertNull(formDef.getMainInstance())
    }

    @Test
    fun setChildren() {
        val formDef = FormDef()
        val children = ArrayList<IFormElement>()
        formDef.setChildren(children)
        assertEquals(0, formDef.getChildren().size)
    }

    @Test
    fun setChildrenWithNull() {
        val formDef = FormDef()
        formDef.setChildren(null)
        // Should not throw; initializes to empty list
        assertNotNull(formDef.getChildren())
        assertEquals(0, formDef.getChildren().size)
    }

    @Test
    fun formInstanceGetChildValues() {
        val formDef = FormDef()
        val root = TreeElement("data", 0)
        val nameNode = TreeElement("name", 0)
        nameNode.setValue(UncastData("Alice"))
        root.addChild(nameNode)
        val ageNode = TreeElement("age", 0)
        ageNode.setValue(UncastData("25"))
        root.addChild(ageNode)

        val instance = FormInstance(root)
        formDef.setInstance(instance)

        val instanceRoot = formDef.getMainInstance()!!.getRoot()
        val nameChild = instanceRoot.getChild("name", 0)
        assertNotNull(nameChild)
        assertEquals("Alice", nameChild.getValue()?.uncast()?.getString())

        val ageChild = instanceRoot.getChild("age", 0)
        assertNotNull(ageChild)
        assertEquals("25", ageChild.getValue()?.uncast()?.getString())
    }
}
