package org.javarosa.core.model.test

import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.instance.TreeReference
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for FormIndex.
 *
 * These tests are not based on any underlying form and use fake tree refs to construct the form
 * indices. This is just meant to test the high-level behavior of form indices themselves.
 *
 * @author Aliza Stone
 */
class FormIndexTests {

    /**
     * Visualization of the hierarchy created between these indices:
     *
     * 0
     * 1
     *   0
     *   1_1
     *   1_2
     */
    private lateinit var index0x1x0x1_1x1_2: FormIndex
    private lateinit var index1x0x1_1x1_2: FormIndex
    private lateinit var index0x1_1x1_2: FormIndex
    private lateinit var index1_1x1_2: FormIndex
    private lateinit var index1_2: FormIndex

    @Before
    fun setUp() {
        // 1_2
        index1_2 = FormIndex(1, 2, TreeReference.rootRef())
        // 1_1, 1_2
        index1_1x1_2 = FormIndex(index1_2, 1, 1, TreeReference.rootRef())
        // 0, 1_1, 1_2
        index0x1_1x1_2 = FormIndex(index1_1x1_2, 0, TreeReference.rootRef())
        // 1, 0, 1_1, 1_2
        index1x0x1_1x1_2 = FormIndex(index0x1_1x1_2, 1, TreeReference.rootRef())
        // 0, 1, 0, 1_1, 1_2
        index0x1x0x1_1x1_2 = FormIndex(index1x0x1_1x1_2, 0, TreeReference.rootRef())
    }

    @Test
    fun testGetNextLevel() {
        var current: FormIndex? = index0x1x0x1_1x1_2
        current = current!!.nextLevel
        assertEquals(index1x0x1_1x1_2, current)
        current = current!!.nextLevel
        assertEquals(index0x1_1x1_2, current)
        current = current!!.nextLevel
        assertEquals(index1_1x1_2, current)
        current = current!!.nextLevel
        assertEquals(index1_2, current)
        assertNull(current!!.nextLevel)
    }

    @Test
    fun testGetLocalIndex() {
        assertEquals(1, index1_2.getLocalIndex())
        assertEquals(0, index0x1_1x1_2.getLocalIndex())
        assertEquals(1, index1x0x1_1x1_2.getLocalIndex())
    }

    @Test
    fun testGetInstanceIndex() {
        assertEquals(2, index1_2.getInstanceIndex())
        assertEquals(1, index1_1x1_2.getInstanceIndex())
        assertEquals(-1, index0x1_1x1_2.getInstanceIndex())
    }

    @Test
    fun testIsInForm() {
        assertFalse(FormIndex.createBeginningOfFormIndex().isInForm())
        assertFalse(FormIndex.createEndOfFormIndex().isInForm())
        assertTrue(index0x1x0x1_1x1_2.isInForm())
    }

    @Test
    fun testGetLastRepeatInstanceIndex_easyCase() {
        assertEquals(2, index0x1x0x1_1x1_2.getLastRepeatInstanceIndex())
    }

    @Test
    fun testGetLastRepeatInstanceIndex_notVeryLastIndex() {
        // Add indices to the end of the hierarchy that do NOT have an instance index
        val index3 = FormIndex(3, TreeReference.rootRef())
        val index2x3 = FormIndex(index3, 2, TreeReference.rootRef())
        index1_2.nextLevel = index2x3

        assertEquals(2, index0x1x0x1_1x1_2.getLastRepeatInstanceIndex())
    }

    @Test
    fun testGetLastRepeatInstanceIndex_nonePresent() {
        // Change to not have a next level for this test
        index0x1_1x1_2.nextLevel = null
        assertEquals(-1, index0x1x0x1_1x1_2.getLastRepeatInstanceIndex())
    }

    /**
     * Extended hierarchy created for this test
     *
     * 0
     * 1
     *   0
     *   1_1
     *   1_2
     *      0
     *      1
     *      2_1
     */
    @Test
    fun testGetLastRepeatInstanceIndex_nestedRepeats() {
        val extensionIndex2_1 = FormIndex(2, 1, TreeReference.rootRef())
        val extensionIndex1x2_1 = FormIndex(extensionIndex2_1, 1, TreeReference.rootRef())
        val extensionIndex0x1x2_1 = FormIndex(extensionIndex1x2_1, 0, TreeReference.rootRef())

        // reset the last index of the original hierarchy to point to the first extension index
        // as its next level
        index1_2.nextLevel = extensionIndex0x1x2_1

        assertEquals(1, index0x1x0x1_1x1_2.getLastRepeatInstanceIndex())
    }
}
