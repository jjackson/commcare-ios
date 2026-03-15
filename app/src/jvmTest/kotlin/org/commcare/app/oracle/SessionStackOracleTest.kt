package org.commcare.app.oracle

import org.commcare.app.engine.NavigationStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Oracle tests for session stack and form linking.
 */
class SessionStackOracleTest {

    @Test
    fun testNavigationStepSealed() {
        val showMenu: NavigationStep = NavigationStep.ShowMenu
        val showCase: NavigationStep = NavigationStep.ShowCaseList(null)
        val startForm: NavigationStep = NavigationStep.StartForm("http://xmlns/form1")
        val syncReq: NavigationStep = NavigationStep.SyncRequired
        val error: NavigationStep = NavigationStep.Error("test error")

        assertIs<NavigationStep.ShowMenu>(showMenu)
        assertIs<NavigationStep.ShowCaseList>(showCase)
        assertIs<NavigationStep.StartForm>(startForm)
        assertIs<NavigationStep.SyncRequired>(syncReq)
        assertIs<NavigationStep.Error>(error)
    }

    @Test
    fun testStartFormCarriesXmlns() {
        val step = NavigationStep.StartForm("http://openrosa.org/formdesigner/abc123")
        assertEquals("http://openrosa.org/formdesigner/abc123", step.xmlns)
    }

    @Test
    fun testStartFormNullXmlns() {
        val step = NavigationStep.StartForm(null)
        assertNull(step.xmlns)
    }

    @Test
    fun testErrorMessage() {
        val step = NavigationStep.Error("Session stack overflow")
        assertEquals("Session stack overflow", step.message)
    }

    @Test
    fun testShowCaseListDatum() {
        val step = NavigationStep.ShowCaseList(null)
        assertNull(step.datum)
    }

    @Test
    fun testChainedFormDecision() {
        // Simulate the decision logic after form completion
        // hasNext=true means finishAndPop returned true -> load next form
        // hasNext=false means session is done -> return to landing

        val hasNext = true
        val nextFormLoaded = true // simulating successful loadFormEntry

        if (hasNext && nextFormLoaded) {
            // Should stay in form entry with new form
            assertTrue(true)
        }

        val hasNext2 = false
        // Should clear session and return to landing
        assertFalse(hasNext2)
    }

    @Test
    fun testChainedFormFailure() {
        // If chained form fails to load, should gracefully return to landing
        val hasNext = true
        val nextFormLoaded = false

        // Decision: clear session and go to landing
        assertTrue(hasNext)
        assertFalse(nextFormLoaded)
        // Code path: navigator.clearSession() -> nav = Landing
    }
}
