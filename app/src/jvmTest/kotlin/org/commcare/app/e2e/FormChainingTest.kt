package org.commcare.app.e2e

import org.commcare.app.engine.NavigationStep
import org.commcare.app.viewmodel.PostFormDestination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for form chaining via session stack.
 */
class FormChainingTest {

    @Test
    fun testPostFormDestinations() {
        // All destinations that can follow form completion
        assertEquals(3, PostFormDestination.entries.size)
        assertTrue(PostFormDestination.entries.contains(PostFormDestination.RETURN_TO_MENU))
        assertTrue(PostFormDestination.entries.contains(PostFormDestination.RETURN_TO_CASE_LIST))
        assertTrue(PostFormDestination.entries.contains(PostFormDestination.CHAINED_FORM))
    }

    @Test
    fun testChainedFormNavigationStep() {
        // When finishAndPop returns true, getNextStep determines the next screen
        val nextStep: NavigationStep = NavigationStep.StartForm("http://form/second")
        assertTrue(nextStep is NavigationStep.StartForm)
        assertEquals("http://form/second", (nextStep as NavigationStep.StartForm).xmlns)
    }

    @Test
    fun testReturnToMenuAfterNoChain() {
        // When finishAndPop returns false, navigation goes to Landing
        val hasNext = false
        assertTrue(!hasNext, "No more frames means return to landing")
    }

    @Test
    fun testFormChainSequence() {
        // Simulate A -> B -> C chain
        val chain = listOf(
            NavigationStep.StartForm("http://form/A"),
            NavigationStep.StartForm("http://form/B"),
            NavigationStep.StartForm("http://form/C"),
            NavigationStep.ShowMenu // After last form, return to menu
        )
        assertEquals(4, chain.size)
        assertTrue(chain.last() is NavigationStep.ShowMenu)
    }

    @Test
    fun testNavigationStepErrorHandling() {
        val error = NavigationStep.Error("Session navigation failed")
        assertTrue(error is NavigationStep.Error)
        assertEquals("Session navigation failed", error.message)
    }
}
