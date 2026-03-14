package org.commcare.util.reference.test

import org.commcare.cases.util.StringUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * @author $|-|!˅@M
 */
class FuzzySearchTest {

    @Test
    fun testFuzzySearch() {
        // Our current implementation allows a maximum difference of 2 between strings to be matched.

        assertTrue(StringUtils.fuzzyMatch("Rama", "Rmaa").first)
        assertTrue(StringUtils.fuzzyMatch("Mehrotras", "Mehrotra").first)
        assertTrue(StringUtils.fuzzyMatch("Clayton", "Cyton").first)
        assertTrue(StringUtils.fuzzyMatch("Test", "Tasty").first)

        // false since Rama and Rmaa has a difference of 3 characters 'a', 'm' and ','
        assertFalse(StringUtils.fuzzyMatch("Rama", "Rmaa,").first)

        // true since we check Mehr with Mehrot,
        assertTrue(StringUtils.fuzzyMatch("Mehr", "Mehrotra").first)

        // false since to check fuzzy search source must have atleast 4 characters.
        assertFalse(StringUtils.fuzzyMatch("Meh", "Mehrotra").first)

        // false even though we have an exact substring match,
        // Cuz fuzzy search starts checking from 0th location.
        assertFalse(StringUtils.fuzzyMatch("Test", "CrazyTest").first)

        // false since aply and cape has a difference of 3 edits add 'c', remove 'l', replace 'y' with 'e'
        assertFalse(StringUtils.fuzzyMatch("aply", "cape").first)
    }
}
