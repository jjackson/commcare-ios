package org.javarosa.core.model.instance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cross-platform tests for TreeReference — the path system for form data.
 */
class TreeReferenceCrossPlatformTest {

    @Test
    fun testRootRef() {
        val root = TreeReference.rootRef()
        assertNotNull(root)
        assertEquals(0, root.size())
    }

    @Test
    fun testSelfRef() {
        val self = TreeReference.selfRef()
        assertNotNull(self)
        assertEquals(0, self.size())
    }

    @Test
    fun testAddNameAndSize() {
        val ref = TreeReference.rootRef()
        ref.add("data", 0)
        ref.add("question", 0)

        assertEquals(2, ref.size())
        assertEquals("data", ref.getName(0))
        assertEquals("question", ref.getName(1))
    }

    @Test
    fun testMultiplicity() {
        val ref = TreeReference.rootRef()
        ref.add("item", 0)
        ref.add("item", 1)
        ref.add("item", 2)

        assertEquals(3, ref.size())
        assertEquals(0, ref.getMultiplicity(0))
        assertEquals(1, ref.getMultiplicity(1))
        assertEquals(2, ref.getMultiplicity(2))
    }

    @Test
    fun testClone() {
        val original = TreeReference.rootRef()
        original.add("data", 0)
        original.add("name", 0)

        val clone = original.clone()
        assertEquals(original.size(), clone.size())
        assertEquals(original.getName(0), clone.getName(0))
        assertEquals(original.getName(1), clone.getName(1))

        // Verify independence
        clone.add("extra", 0)
        assertEquals(2, original.size())
        assertEquals(3, clone.size())
    }

    @Test
    fun testEquality() {
        val ref1 = TreeReference.rootRef()
        ref1.add("data", 0)
        ref1.add("name", 0)

        val ref2 = TreeReference.rootRef()
        ref2.add("data", 0)
        ref2.add("name", 0)

        assertEquals(ref1, ref2)
        assertEquals(ref1.hashCode(), ref2.hashCode())
    }

    @Test
    fun testInequality() {
        val ref1 = TreeReference.rootRef()
        ref1.add("data", 0)

        val ref2 = TreeReference.rootRef()
        ref2.add("other", 0)

        assertFalse(ref1 == ref2)
    }

    @Test
    fun testGenericize() {
        val ref = TreeReference.rootRef()
        ref.add("data", 0)
        ref.add("item", 5)

        val generic = ref.genericize()
        assertEquals(2, generic.size())
        assertEquals(TreeReference.INDEX_UNBOUND, generic.getMultiplicity(1))
    }

    @Test
    fun testParentRef() {
        val ref = TreeReference.rootRef()
        ref.add("data", 0)
        ref.add("group", 0)
        ref.add("question", 0)

        val parent = ref.getParentRef()
        assertNotNull(parent)
        assertEquals(2, parent.size())
        assertEquals("group", parent.getName(1))
    }

    @Test
    fun testToString() {
        val ref = TreeReference.rootRef()
        ref.add("data", 0)
        ref.add("name", 0)

        val str = ref.toString()
        assertNotNull(str)
        assertTrue(str.contains("data"), "toString should contain 'data': $str")
        assertTrue(str.contains("name"), "toString should contain 'name': $str")
    }

    @Test
    fun testIntersect() {
        val ref1 = TreeReference.rootRef()
        ref1.add("data", 0)
        ref1.add("group", 0)
        ref1.add("name", 0)

        val ref2 = TreeReference.rootRef()
        ref2.add("data", 0)
        ref2.add("group", 0)
        ref2.add("age", 0)

        val intersection = ref1.intersect(ref2)
        assertNotNull(intersection)
        assertEquals(2, intersection.size())
        assertEquals("data", intersection.getName(0))
        assertEquals("group", intersection.getName(1))
    }

    @Test
    fun testContextualize() {
        val ref = TreeReference.rootRef()
        ref.add("data", TreeReference.INDEX_UNBOUND)
        ref.add("name", 0)

        val context = TreeReference.rootRef()
        context.add("data", 3)
        context.add("name", 0)

        val result = ref.contextualize(context)
        assertNotNull(result)
        assertEquals(3, result.getMultiplicity(0))
    }

    @Test
    fun testBuildRefFromTreeElement() {
        val root = TreeElement("data")
        val child = TreeElement("question")
        root.addChild(child)

        val ref = TreeReference.buildRefFromTreeElement(child)
        assertNotNull(ref)
        assertTrue(ref.size() > 0)
    }

    @Test
    fun testInstanceName() {
        val ref = TreeReference(null, 0)
        assertNull(ref.getInstanceName())

        val instanceRef = TreeReference("myInstance", 0)
        assertEquals("myInstance", instanceRef.getInstanceName())
    }
}
