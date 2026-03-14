package org.commcare.backend.session.test

import org.commcare.modern.session.SessionWrapper
import org.commcare.session.SessionFrame
import org.commcare.test.utilities.MockApp
import org.javarosa.core.model.Constants
import org.javarosa.core.model.instance.ExternalDataInstance
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests navigating through a CommCareSession (setting datum values and commands, using stepBack(),
 * etc.) for a sample app
 *
 * @author amstone
 */
class BasicSessionNavigationTests {

    private lateinit var mApp: MockApp
    private lateinit var session: SessionWrapper

    @Before
    fun setUp() {
        mApp = MockApp("/session-tests-template/")
        session = mApp.getSession()
    }

    @Test
    fun testNeedsCommandFirst() {
        // Before anything is done in the session, should need a command
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        // After setting first command to m0, should still need another command because the 3 forms
        // within m0 have different datum needs, so will prioritize getting the next command first
        session.setCommand("m0")
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        // After choosing the form, will need a case id
        session.setCommand("m0-f1")
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())

        // Stepping back after choosing a command should go back only 1 level
        session.stepBack()
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        assertEquals("m0", session.getCommand())

        // After choosing registration form, should need computed case id
        session.setCommand("m0-f0")
        assertEquals(SessionFrame.STATE_DATUM_COMPUTED, session.getNeededData())

        session.setComputedDatum()
        assertNull(session.getNeededData())
    }

    @Test
    fun testNeedsCaseFirst() {
        // Before anything is done in the session, should need a command
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        // After setting first command to m2, should need a case id, because both forms within m2 need one
        session.setCommand("m2")
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())

        // After setting case id, should need to choose a form
        session.setEntityDatum("case_id", "case_two")
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        // Should be ready to go after choosing a form
        session.setCommand("m2-f1")
        assertNull(session.getNeededData())
    }

    @Test
    fun testStepBackBasic() {
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m1")
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m1-f3")
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("case_id", "case_one")
        assertNull(session.getNeededDatum())

        // Should result in needing a case_id again
        session.stepBack()
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
    }

    @Test
    fun testStepBackWithExtraValue() {
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m1")
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m1-f3")
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("case_id", "case_one")
        assertNull(session.getNeededDatum())
        session.setEntityDatum("return_to", "m1")

        // Should pop 2 values off of the session stack in order to return to the last place
        // where there was a user-inputted decision
        session.stepBack()
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
    }

    @Test
    fun testStepBackWithComputedDatum() {
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m0")
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m0-f0")
        assertEquals(SessionFrame.STATE_DATUM_COMPUTED, session.getNeededData())
        session.setComputedDatum()
        assertNull(session.getNeededData())

        // Should pop 2 values off of the session stack so that the next needed data isn't a
        // computed value
        session.stepBack()
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
    }

    @Test
    fun testStepToSyncRequest() {
        session.setCommand("patient-case-search")
        assertEquals(SessionFrame.STATE_QUERY_REQUEST, session.getNeededData())

        val dataInstance = SessionStackTests.buildRemoteExternalDataInstance(
            this.javaClass, session, "/session-tests-template/patient_query_result.xml"
        )
        session.setQueryDatum(dataInstance)

        // case_id
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("case_id", "123")

        // time to make sync request
        assertEquals(SessionFrame.STATE_SYNC_REQUEST, session.getNeededData())
    }

    /**
     * Try selecting case already in local case db
     */
    @Test
    fun testStepToIrrelevantSyncRequest() {
        session.setCommand("patient-case-search")
        assertEquals(SessionFrame.STATE_QUERY_REQUEST, session.getNeededData())

        val dataInstance = SessionStackTests.buildRemoteExternalDataInstance(
            this.javaClass, session, "/session-tests-template/patient_query_result.xml"
        )
        session.setQueryDatum(dataInstance)

        // case_id
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
        // select case present in user_restore
        session.setEntityDatum("case_id", "case_one")

        // assert that relevancy condition of post request is false
        assertNull(session.getNeededData())
    }

    @Test
    fun testInvokeEmptySyncRequest() {
        val session = mApp.getSession()

        session.setCommand("empty-remote-request")
        assertEquals(SessionFrame.STATE_SYNC_REQUEST, session.getNeededData())
    }

    @Test
    fun testStepToSyncRequestRelevancy() {
        session.setCommand("irrelevant-remote-request")
        assertNull(session.getNeededData())

        session.setCommand("relevant-remote-request")
        assertEquals(SessionFrame.STATE_SYNC_REQUEST, session.getNeededData())

        session.setCommand("dynamic-relevancy-remote-request")
        session.setEntityDatum("case_id", "")
        assertNull(session.getNeededData())
        session.setEntityDatum("case_id", "case_one")
        assertEquals(SessionFrame.STATE_SYNC_REQUEST, session.getNeededData())
    }

    @Test
    fun testStepToSyncRequestInEntry_multiple() {
        // menu with multiple entries
        testStepToSyncRequestInEntry("m2")
    }

    @Test
    fun testStepToSyncRequestInEntry_single() {
        // menu with single entry
        testStepToSyncRequestInEntry("m3")
    }

    fun testStepToSyncRequestInEntry(menuCommand: String) {
        session.setCommand(menuCommand)
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("case_id", "case_one")

        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())
        session.setCommand("m2-f2")

        assertEquals(SessionFrame.STATE_SYNC_REQUEST, session.getNeededData())
        // simulate sync request success
        session.addExtraToCurrentFrameStep(Constants.EXTRA_POST_SUCCESS, true)

        assertNull(session.getNeededData())
    }
}
