package org.javarosa.core.model.instance

import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.UncastData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for TreeElement — core data model of CommCare forms.
 */
class TreeElementCrossPlatformTest {

    @Test
    fun testCreateEmptyTreeElement() {
        val te = TreeElement()
        assertNull(te.getName())
        assertEquals(0, te.getMult())
    }

    @Test
    fun testCreateNamedTreeElement() {
        val te = TreeElement("question1")
        assertEquals("question1", te.getName())
        assertEquals(0, te.getMult())
    }

    @Test
    fun testCreateWithMultiplicity() {
        val te = TreeElement("repeat", 3)
        assertEquals("repeat", te.getName())
        assertEquals(3, te.getMult())
    }

    @Test
    fun testAddAndGetChild() {
        val parent = TreeElement("data")
        val child1 = TreeElement("name")
        val child2 = TreeElement("age")

        parent.addChild(child1)
        parent.addChild(child2)

        assertEquals(2, parent.getNumChildren())
        assertNotNull(parent.getChild("name", 0))
        assertNotNull(parent.getChild("age", 0))
        assertNull(parent.getChild("missing", 0))
    }

    @Test
    fun testGetChildAt() {
        val parent = TreeElement("data")
        val child1 = TreeElement("first")
        val child2 = TreeElement("second")

        parent.addChild(child1)
        parent.addChild(child2)

        assertEquals("first", parent.getChildAt(0)?.getName())
        assertEquals("second", parent.getChildAt(1)?.getName())
    }

    @Test
    fun testSetAndGetValue() {
        val te = TreeElement("answer")
        assertNull(te.getValue())

        te.setValue(StringData("hello"))
        assertNotNull(te.getValue())
        assertEquals("hello", te.getValue()!!.getDisplayText())
    }

    @Test
    fun testSetIntegerValue() {
        val te = TreeElement("count")
        te.setValue(IntegerData(42))
        assertEquals("42", te.getValue()!!.getDisplayText())
    }

    @Test
    fun testSetAndGetAttribute() {
        val te = TreeElement("node")
        te.setAttribute(null, "id", "abc123")

        val attr = te.getAttribute(null, "id")
        assertNotNull(attr)
        assertEquals("abc123", attr.getValue()?.getDisplayText())
    }

    @Test
    fun testRemoveChild() {
        val parent = TreeElement("data")
        val child = TreeElement("temp")
        parent.addChild(child)
        assertEquals(1, parent.getNumChildren())

        parent.removeChild(child)
        assertEquals(0, parent.getNumChildren())
    }

    @Test
    fun testGetRef() {
        val parent = TreeElement("data")
        val child = TreeElement("question")
        parent.addChild(child)

        val ref = child.getRef()
        assertNotNull(ref)
    }

    @Test
    fun testDeepCopy() {
        val original = TreeElement("data")
        val child = TreeElement("name")
        child.setValue(StringData("Alice"))
        original.addChild(child)

        val copy = original.deepCopy(true)
        assertEquals("data", copy.getName())
        assertEquals(1, copy.getNumChildren())
        assertEquals("Alice", copy.getChildAt(0)?.getValue()?.getDisplayText())

        // Verify it's a deep copy — modifying copy doesn't affect original
        copy.getChildAt(0)?.setValue(StringData("Bob"))
        assertEquals("Alice", original.getChildAt(0)?.getValue()?.getDisplayText())
    }

    @Test
    fun testLeafDetection() {
        val parent = TreeElement("group")
        assertTrue(parent.isLeaf)

        parent.addChild(TreeElement("child"))
        assertTrue(!parent.isLeaf)
    }

    @Test
    fun testMultipleChildrenSameName() {
        val parent = TreeElement("data")
        parent.addChild(TreeElement("item", 0))
        parent.addChild(TreeElement("item", 1))
        parent.addChild(TreeElement("item", 2))

        assertEquals(3, parent.getNumChildren())
        val items = parent.getChildrenWithName("item")
        assertEquals(3, items.size)
    }

    @Test
    fun testRelevantFlag() {
        val te = TreeElement("q")
        assertTrue(te.isRelevant)

        te.setRelevant(false)
        assertTrue(!te.isRelevant)
    }
}
