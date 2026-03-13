package org.commcare.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionUtilsTest {

    @Test
    fun containsAny() {
        val superList = arrayListOf("Apple", "Mango", "Banana")

        assertFalse("Empty List can't contain any fruits from superList",
            CollectionUtils.containsAny(superList, ArrayList()))

        val subListOne = arrayListOf("Orange")
        assertFalse("List doesn't contain any fruits from superList",
            CollectionUtils.containsAny(superList, subListOne))

        val subListTwo = arrayListOf("Orange", "Mango")
        assertTrue("List contains Mango from superList",
            CollectionUtils.containsAny(superList, subListTwo))
    }
}
