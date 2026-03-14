package org.commcare.backend.session.test

import org.commcare.session.SessionFrame
import org.commcare.test.utilities.MockApp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class ChildModuleNavigationTests {

    /**
     * Ensure that session nav takes all menu entries with the same id into account
     * when determining the next needed datum
     */
    @Test
    fun testNeedsCommandFirst() {
        val app = MockApp("/session-tests-template/")
        val session = app.getSession()
        session.setCommand("parent-module")

        // since there are two entries registered under 'parent-module' that
        // need different case data, we need to select the entry before the case
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("adolescent-form")

        // check that after choosing the entry we now need the correct case data
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("adolescent_case_id", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("adolescent_case_id", "Al")
    }
}
