package org.javarosa.core.util.test

import org.javarosa.core.util.DataUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class DataUtilTest {

    @Test
    fun intersectionTest() {
        val setOne = arrayListOf("one", "two")
        val setTwo = arrayListOf("one", "three")
        val intersectSet = DataUtil.intersection(setOne, setTwo)

        // for safety, we want to return a whole new vector
        assertFalse(intersectSet === setOne)
        assertFalse(intersectSet === setTwo)

        // for safety, don't modify ingoing vector arguments
        assertTrue(setOne.contains("one"))
        assertTrue(setOne.contains("two"))

        assertTrue(setTwo.contains("one"))
        assertTrue(setTwo.contains("three"))

        // make sure proper intersection is computed
        assertTrue(intersectSet.contains("one"))

        assertFalse(intersectSet.contains("two"))
        assertFalse(intersectSet.contains("three"))
    }
}
