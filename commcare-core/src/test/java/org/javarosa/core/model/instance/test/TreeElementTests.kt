package org.javarosa.core.model.instance.test

import org.javarosa.core.model.instance.TreeElement
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Tests the functionality of TreeElements
 *
 * @author wpride
 */
class TreeElementTests {

    private lateinit var element: TreeElement
    private lateinit var childOne: TreeElement
    private lateinit var childTwo: TreeElement

    @Before
    fun setup() {
        element = TreeElement("root")
        childOne = TreeElement("H2a")
        childTwo = TreeElement("H3B")

        element.addChild(childOne)
        element.addChild(childTwo)
    }

    @Test
    fun testHashCollision() {
        // childOne and childTwo should have the same hash, but still resolve correctly
        val getOne = element.getChild("H2a", 0)
        val getTwo = element.getChild("H3B", 0)
        assertEquals(childOne, getOne)
        assertEquals(childTwo, getTwo)
    }
}
