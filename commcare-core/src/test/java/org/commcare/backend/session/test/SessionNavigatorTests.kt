package org.commcare.backend.session.test

import org.commcare.session.SessionFrame
import org.commcare.session.SessionNavigator
import org.commcare.suite.model.EntityDatum
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.MockSessionNavigationResponder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests for SessionNavigator.java
 *
 * @author amstone
 */
class SessionNavigatorTests {

    private lateinit var mApp: MockApp
    private lateinit var mSessionNavigationResponder: MockSessionNavigationResponder
    private lateinit var sessionNavigator: SessionNavigator

    @Before
    fun setUp() {
        mApp = MockApp("/session-tests-template/")
        mSessionNavigationResponder = MockSessionNavigationResponder(mApp.getSession())
        sessionNavigator = SessionNavigator(mSessionNavigationResponder)
    }

    private fun triggerSessionStepAndCheckResultCode(expectedResultCode: Int) {
        sessionNavigator.startNextSessionStep()
        assertEquals(expectedResultCode, mSessionNavigationResponder.lastResultCode)
    }

    @Test
    fun testNavWithoutAutoSelect() {
        val session = mApp.getSession()

        // Before anything has been done in the session, the sessionNavigatorResponder should be
        // directed to get a command
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // Simulate selecting module m0
        session.setCommand("m0")

        // The sessionNavigatorResponder should be prompted to get another command, representing
        // form choice
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // Simulate selecting a form
        session.setCommand("m0-f1")

        // After a form is chosen for which auto selection is not turned on, the
        // sessionNavigatorResponder should be prompted to start entity selection
        triggerSessionStepAndCheckResultCode(SessionNavigator.START_ENTITY_SELECTION)

        // Simulate going back
        session.stepBack()

        // Confirm that we now need a command again
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // Simulate selecting the registration form instead
        session.setCommand("m0-f0")

        // The session should need a computed datum (a new case id). However, for computed datums,
        // the session navigator sets it itself; no callout to sessionNavigatorResponder is needed.
        // After setting a computed datum, the session navigator makes a recursive call to
        // startNextSessionStep(), so the sessionNavigatorResponder should now have been prompted
        // to start form entry
        triggerSessionStepAndCheckResultCode(SessionNavigator.START_FORM_ENTRY)
    }

    @Test
    fun testAutoSelectEnabledWithTwoCases() {
        val session = mApp.getSession()

        // Before anything is done in the session, should need a command
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // Simulate selecting module m0
        session.setCommand("m0")

        // Should now need a form selection
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // Simulate selecting a form with auto-select enabled
        session.setCommand("m0-f2")

        // Confirm that the next datum has auto-select enabled
        val nextNeededDatum = session.getNeededDatum()
        assertTrue((nextNeededDatum as EntityDatum).isAutoSelectEnabled())

        // Since there are 2 cases in the case list (user_restore.xml contains 2 cases of type
        // 'pregnancy', which is the case type that m0-f2's nodeset filters for), the
        // sessionNavigatorResponder will be prompted to start entity select, even though
        // auto-select is enabled
        triggerSessionStepAndCheckResultCode(SessionNavigator.START_ENTITY_SELECTION)
    }

    @Test
    fun testAutoSelectEnabledWithOneCase() {
        val session = mApp.getSession()

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        session.setCommand("m1")
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m1-f1")

        // Confirm that the next datum has auto-select enabled and has a confirm detail defined
        val nextNeededDatum = session.getNeededDatum() as EntityDatum
        assertTrue(nextNeededDatum.isAutoSelectEnabled())
        assertNotNull(nextNeededDatum.getLongDetail())

        // Since there is one case in the case list (user_restore.xml contains one case of type
        // 'child', which is the case type that m1-f1's nodeset filters for), and auto-select is
        // enabled, the sessionNavigationResponder should be prompted to launch the confirm detail
        // screen for the auto-selected case
        triggerSessionStepAndCheckResultCode(SessionNavigator.LAUNCH_CONFIRM_DETAIL)
    }

    @Test
    fun testStepBackOverAutoselectWithoutCaseDetail() {
        val session = mApp.getSession()

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m1")

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m1-f4")

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        val nextNeededDatum = session.getNeededDatum() as EntityDatum
        assertTrue(nextNeededDatum.isAutoSelectEnabled())
        assertNull(nextNeededDatum.getLongDetail())

        triggerSessionStepAndCheckResultCode(SessionNavigator.START_FORM_ENTRY)

        // Test that going back from here results in being back to BEFORE we selected the entry
        // with the auto-selecting case
        session.stepBack()
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
    }

    @Test
    fun testStepBackOverAutoselectWithCaseDetail() {
        val session = mApp.getSession()

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m1")

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m1-f1")

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        val nextNeededDatum = session.getNeededDatum() as EntityDatum
        assertTrue(nextNeededDatum.isAutoSelectEnabled())
        assertNotNull(nextNeededDatum.getLongDetail())

        triggerSessionStepAndCheckResultCode(SessionNavigator.LAUNCH_CONFIRM_DETAIL)

        // Simulate pressing the "Continue" button on the case detail, by setting a value
        // for the needed datum. We should now be ready for form entry
        session.setEntityDatum(nextNeededDatum, "case_one")
        triggerSessionStepAndCheckResultCode(SessionNavigator.START_FORM_ENTRY)

        // Test that going back from here results in being back at the case detail screen (and
        // NOT back before we selected the entry with the auto-selecting case)
        session.stepBack()
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
    }

    @Test
    fun testAutoSelectEnabledWithOneCaseAndNoDetail() {
        val session = mApp.getSession()

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        session.setCommand("m1")
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m1-f2")

        // Confirm that the next datum has auto-select enabled, but does NOT have a confirm detail
        // defined
        val nextNeededDatum = session.getNeededDatum() as EntityDatum
        assertTrue(nextNeededDatum.isAutoSelectEnabled())
        assertNull(nextNeededDatum.getLongDetail())

        // Since there is one case in the case list and auto-select is enabled, but there is no
        // confirm detail screen, the sessionNavigationResponder should be prompted to go directly
        // to form entry
        triggerSessionStepAndCheckResultCode(SessionNavigator.START_FORM_ENTRY)
    }

    @Test
    fun testSettingAndGettingSessionExtras() {
        val LAST_QUERY_KEY = "last-query-key"
        val COLOR_KEY = "color-key"
        val session = mApp.getSession()

        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // step forward and assign some extras to the current top of session stack
        session.setCommand("m0")
        session.addExtraToCurrentFrameStep(LAST_QUERY_KEY, "the lorax")
        // you can store any externalizable object in the extras of a frame step
        session.addExtraToCurrentFrameStep(COLOR_KEY, 253)
        session.addExtraToCurrentFrameStep(COLOR_KEY, 153)
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)

        // set forward again, set more extras
        session.setCommand("m0-f2")
        session.addExtraToCurrentFrameStep(LAST_QUERY_KEY, "the cat in the hat")
        assertEquals("the cat in the hat", session.getCurrentFrameStepExtra(LAST_QUERY_KEY))
        triggerSessionStepAndCheckResultCode(SessionNavigator.START_ENTITY_SELECTION)

        // step back and assert that initial extras are still there
        session.stepBack()
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        assertEquals("m0", session.getCommand())
        assertEquals("the lorax", session.getCurrentFrameStepExtra(LAST_QUERY_KEY))
        assertEquals(listOf(253, 153), session.getCurrentFrameStepExtras()!![COLOR_KEY])

        // step back and then forward into frame w/ same command and
        // assert that extras are no longer present
        session.stepBack()
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        session.setCommand("m0")
        triggerSessionStepAndCheckResultCode(SessionNavigator.GET_COMMAND)
        assertNull(session.getCurrentFrameStepExtra(LAST_QUERY_KEY))
    }
}
