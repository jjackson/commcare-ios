package org.javarosa.core.model.test

import org.javarosa.core.test.FormParseInit
import org.javarosa.xform.parse.XFormParseException
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Tests for cyclic references
 *
 * @author wpride
 */
class CyclicReferenceTests {

    /**
     * Test that XPath cyclic reference that references parent throws usable error
     */
    @Test
    fun testCyclicReferenceWithGroup() {
        try {
            FormParseInit("/xform_tests/group_cyclic_reference.xml")
        } catch (e: XFormParseException) {
            val detailMessage = e.message!!
            // Assert that we're using the shortest cycle algorithm
            assertTrue(detailMessage.contains("Logic is cyclical"))
            // There should only be three newlines since only the three core cyclic references were included
            val newlineCount = detailMessage.length - detailMessage.replace("\n", "").length
            assertTrue(newlineCount == 3)
            return
        }
        fail("Cyclical reference did not throw XFormParseException")
    }

    /**
     * Test that XPath cyclic reference that references parent throws usable error
     */
    @Test
    fun testCyclicalReferenceRegression() {
        testCyclicReferenceForPath("/xform_tests/real_form_with_cycle_errors.xml", 4)
        testCyclicReferenceForPath("/xform_tests/real_form2_with_cycle_errors.xml", 2)
    }

    private fun testCyclicReferenceForPath(formPath: String, numberOfCyclicReferences: Int) {
        try {
            FormParseInit(formPath)
        } catch (e: XFormParseException) {
            val detailMessage = e.message!!
            // Assert that we're using the shortest cycle algorithm
            assertTrue(detailMessage.contains("Logic is cyclical"))
            // number of newlines should be equal to number of core cyclic references were included
            val newlineCount = detailMessage.length - detailMessage.replace("\n", "").length
            assertTrue(newlineCount == numberOfCyclicReferences)
            return
        }
        fail("Cyclical reference did not throw XFormParseException")
    }
}
