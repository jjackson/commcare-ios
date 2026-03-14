package org.javarosa.core.model.instance

import org.javarosa.core.model.data.UncastData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

/**
 * Cross-platform tests for TreeElement creation, child manipulation,
 * and attribute access. Runs on both JVM and iOS targets.
 */
class TreeElementTest {

    @Test
    fun createTreeElementWithName() {
        val element = TreeElement("person")
        assertEquals("person", element.getName())
    }

    @Test
    fun createTreeElementWithNameAndMultiplicity() {
        val element = TreeElement("item", 3)
        assertEquals("item", element.getName())
        assertEquals(3, element.getMult())
    }

    @Test
    fun defaultMultiplicity() {
        val element = TreeElement("node")
        assertEquals(TreeReference.DEFAULT_MUTLIPLICITY, element.getMult())
    }

    @Test
    fun addChildAndRetrieve() {
        val parent = TreeElement("parent", 0)
        val child = TreeElement("child", 0)
        parent.addChild(child)

        assertEquals(1, parent.getNumChildren())
        val retrieved = parent.getChild("child", 0)
        assertNotNull(retrieved)
        assertEquals("child", retrieved.getName())
    }

    @Test
    fun addMultipleChildren() {
        val parent = TreeElement("root", 0)
        val child1 = TreeElement("alpha", 0)
        val child2 = TreeElement("beta", 0)
        val child3 = TreeElement("gamma", 0)
        parent.addChild(child1)
        parent.addChild(child2)
        parent.addChild(child3)

        assertEquals(3, parent.getNumChildren())
    }

    @Test
    fun getChildReturnsNullForMissing() {
        val parent = TreeElement("root", 0)
        val child = TreeElement("exists", 0)
        parent.addChild(child)

        assertNull(parent.getChild("nonexistent", 0))
    }

    @Test
    fun getChildrenWithName() {
        val parent = TreeElement("root", 0)
        val a1 = TreeElement("item", 0)
        val a2 = TreeElement("item", 1)
        val b = TreeElement("other", 0)
        parent.addChild(a1)
        parent.addChild(a2)
        parent.addChild(b)

        val items = parent.getChildrenWithName("item")
        assertEquals(2, items.size)
    }

    @Test
    fun getChildMultiplicity() {
        val parent = TreeElement("root", 0)
        parent.addChild(TreeElement("rep", 0))
        parent.addChild(TreeElement("rep", 1))
        parent.addChild(TreeElement("rep", 2))

        assertEquals(3, parent.getChildMultiplicity("rep"))
    }

    @Test
    fun isLeafForLeafElement() {
        val leaf = TreeElement("leaf", 0)
        assertTrue(leaf.isLeaf)
    }

    @Test
    fun isLeafForParentElement() {
        val parent = TreeElement("parent", 0)
        parent.addChild(TreeElement("child", 0))
        assertFalse(parent.isLeaf)
    }

    @Test
    fun setAndGetValue() {
        val element = TreeElement("field", 0)
        element.setValue(UncastData("test value"))
        val value = element.getValue()
        assertNotNull(value)
        assertEquals("test value", value.uncast().getString())
    }

    @Test
    fun setAttributeAndRetrieve() {
        val element = TreeElement("node", 0)
        element.setAttribute(null, "id", "123")

        val attrValue = element.getAttributeValue(null, "id")
        assertEquals("123", attrValue)
    }

    @Test
    fun setMultipleAttributes() {
        val element = TreeElement("node", 0)
        element.setAttribute(null, "id", "1")
        element.setAttribute(null, "type", "case")

        assertEquals("1", element.getAttributeValue(null, "id"))
        assertEquals("case", element.getAttributeValue(null, "type"))
        assertEquals(2, element.getAttributeCount())
    }

    @Test
    fun getAttributeReturnsNullForMissing() {
        val element = TreeElement("node", 0)
        assertNull(element.getAttributeValue(null, "nonexistent"))
    }

    @Test
    fun attributeByIndex() {
        val element = TreeElement("node", 0)
        element.setAttribute(null, "color", "red")

        assertEquals("color", element.getAttributeName(0))
        assertEquals("red", element.getAttributeValue(0))
    }

    @Test
    fun removeChild() {
        val parent = TreeElement("root", 0)
        val child = TreeElement("child", 0)
        parent.addChild(child)
        assertEquals(1, parent.getNumChildren())

        parent.removeChild(child)
        assertEquals(0, parent.getNumChildren())
    }

    @Test
    fun getChildAt() {
        val parent = TreeElement("root", 0)
        val first = TreeElement("first", 0)
        val second = TreeElement("second", 0)
        parent.addChild(first)
        parent.addChild(second)

        assertEquals("first", parent.getChildAt(0)?.getName())
        assertEquals("second", parent.getChildAt(1)?.getName())
    }

    @Test
    fun hasChildren() {
        val empty = TreeElement("empty", 0)
        assertFalse(empty.hasChildren())

        val parent = TreeElement("parent", 0)
        parent.addChild(TreeElement("child", 0))
        assertTrue(parent.hasChildren())
    }

    @Test
    fun deepCopy() {
        val root = TreeElement("root", 0)
        val child = TreeElement("child", 0)
        child.setValue(UncastData("value"))
        root.addChild(child)

        val copy = root.deepCopy(true)
        assertEquals("root", copy.getName())
        assertEquals(1, copy.getNumChildren())
        val copiedChild = copy.getChild("child", 0)
        assertNotNull(copiedChild)
        assertEquals("value", copiedChild.getValue()?.uncast()?.getString())
    }

    @Test
    fun setDataType() {
        val element = TreeElement("field", 0)
        element.setDataType(org.javarosa.core.model.Constants.DATATYPE_TEXT)
        // No getter for dataType on TreeElement interface, but setting should not throw
    }

    @Test
    fun parentIsSetOnAddChild() {
        val parent = TreeElement("parent", 0)
        val child = TreeElement("child", 0)
        parent.addChild(child)

        // child's parent should be set by addChild
        assertNotNull(child.getParent())
        assertEquals("parent", (child.getParent() as TreeElement).getName())
    }
}
