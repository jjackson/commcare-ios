package org.commcare.backend.session.test

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionDescriptorUtil
import org.commcare.session.SessionFrame
import org.commcare.test.utilities.MockApp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Created by willpride on 9/15/16.
 */
class StackRegressionTests {
    /**
     * This is the stack state we would be in after end of form navigation
     * - this test ensures that we correctly resolve the unknown state (a
     * case selection)
     */
    @Test
    fun testGoBackAfterEndOfFormNavigation() {
        val mockApp = MockApp("/nav_back/")
        val session = mockApp.getSession()
        val sandbox = session.getSandbox()
        val blankSession = SessionWrapper(session.getPlatform(), sandbox)
        val descriptor = "COMMAND_ID m1 " +
                "STATE_UNKNOWN case_id test_id"
        SessionDescriptorUtil.loadSessionFromDescriptor(descriptor, blankSession)
        assertEquals(SessionFrame.STATE_COMMAND_ID, blankSession.getNeededData())
        blankSession.stepBack()
        assertEquals(SessionFrame.STATE_DATUM_VAL, blankSession.getNeededData())
        blankSession.stepBack()
        assertEquals(SessionFrame.STATE_COMMAND_ID, blankSession.getNeededData())
    }

    @Test
    fun testKeepComputedDatum() {
        val mockApp = MockApp("/nav_back/")
        val session = mockApp.getSession()
        val sandbox = session.getSandbox()
        val blankSession = SessionWrapper(session.getPlatform(), sandbox)
        val descriptor = "COMMAND_ID m0-f0 " +
                "COMPUTED_DATUM case_id_new_mother_0 test_id " +
                "COMPUTED_DATUM return_to m1"
        SessionDescriptorUtil.loadSessionFromDescriptor(descriptor, blankSession)
        assertEquals(2, blankSession.getData().size)
    }
}
