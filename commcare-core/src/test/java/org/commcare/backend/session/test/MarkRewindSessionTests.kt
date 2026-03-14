package org.commcare.backend.session.test

import org.commcare.session.SessionFrame
import org.commcare.test.utilities.CaseTestUtils
import org.commcare.test.utilities.MockApp
import org.junit.Assert.*
import org.junit.Test

/**
 * @author Phillip Mates (pmates@dimagi.com)
 */
class MarkRewindSessionTests {

    /**
     * Test rewinding and set needed datum occurs correctly
     */
    @Test
    fun basicMarkRewindTest() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("child-visit")
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("mother_case_1", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("mother_case_1", "nancy")

        // perform 'claim' action
        val shortDetail = session.getPlatform()!!.getDetail("case-list")!!
        val action = shortDetail.getCustomActions(session.getEvaluationContext())[0]
        // queue up action
        var didRewindOrNewFrame = session.executeStackOperations(action.stackOperations!!, session.getEvaluationContext())
        assertFalse(didRewindOrNewFrame)

        // test backing out of action
        session.stepBack()
        assertEquals("child_case_1", session.getNeededDatum()!!.getDataId())
        assertEquals(
            SessionFrame.STATE_DATUM_VAL,
            session.getFrame().getSteps()[session.getFrame().getSteps().size - 1].getType()
        )

        // queue up action again
        session.executeStackOperations(action.stackOperations!!, session.getEvaluationContext())

        // finish action
        didRewindOrNewFrame = session.finishExecuteAndPop(session.getEvaluationContext())
        assertTrue(didRewindOrNewFrame)

        // ensure we don't need any more data to perform the visit
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('session')/session/data/child_case_1", "billy"
        )
        didRewindOrNewFrame = session.finishExecuteAndPop(session.getEvaluationContext())
        assertFalse(didRewindOrNewFrame)
        assertTrue(session.getFrame().isDead())
    }

    @Test
    fun markAndRewindInCreateTest() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("create-rewind-behavior")
        assertNull(session.getNeededData())

        val didRewindOrNewFrame = session.finishExecuteAndPop(session.getEvaluationContext())
        assertTrue(didRewindOrNewFrame)

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("child_case_1", session.getNeededDatum()!!.getDataId())

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('session')/session/data/mother_case_1", "real mother"
        )
    }

    @Test
    fun rewindInCreateWithouMarkTest() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("create-rewind-without-mark")
        assertNull(session.getNeededData())

        val didRewindOrNewFrame = session.finishExecuteAndPop(session.getEvaluationContext())
        assertTrue(didRewindOrNewFrame)

        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('session')/session/data/child_case_1", "billy"
        )
    }

    /**
     * Test that rewinding without a mark in the stack is a null op
     */
    @Test
    fun returningValuesFromFramesTest() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        // start with the registration
        session.setCommand("m0")
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())

        assertEquals("mother_case_1", session.getNeededDatum()!!.getDataId())

        // manually set the needed datum instead of computing it
        session.setEntityDatum("mother_case_1", "nancy")

        // execute the stack ops for the m0-f0 entry
        session.setCommand("m0-f0")
        var didRewindOrNewFrame = session.finishExecuteAndPop(session.getEvaluationContext())
        assertTrue(didRewindOrNewFrame)

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('session')/session/data/mother_case_1", "nancy"
        )

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("child_case_1", session.getNeededDatum()!!.getDataId())
    }

    /**
     * Test nested mark/rewinds
     */
    @Test
    fun nestedMarkRewindTest() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("nested-mark-and-rewinds-part-i")
        session.finishExecuteAndPop(session.getEvaluationContext())
        assertEquals(SessionFrame.STATE_COMMAND_ID, session.getNeededData())

        session.setCommand("nested-mark-and-rewinds-part-ii")
        session.finishExecuteAndPop(session.getEvaluationContext())

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("child_case_1", session.getNeededDatum()!!.getDataId())

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('session')/session/data/mother_case_1", "the mother case id"
        )
    }

    @Test
    fun pushIdRewindToCurrentFrame() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("push-rewind-to-current-id-frame-part-i")
        session.finishExecuteAndPop(session.getEvaluationContext())
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())

        session.setCommand("push-rewind-to-current-id-frame-part-ii")
        session.finishExecuteAndPop(session.getEvaluationContext())

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("child_case_1", session.getNeededDatum()!!.getDataId())

        CaseTestUtils.xpathEvalAndAssert(
            session.getEvaluationContext(),
            "instance('session')/session/data/mother_case_1", "the mother case id"
        )
    }

    @Test
    fun rewindWithoutValue() {
        val mockApp = MockApp("/stack-frame-copy-app/")
        val session = mockApp.getSession()

        session.setCommand("push-rewind-to-current-id-frame-part-i")
        session.finishExecuteAndPop(session.getEvaluationContext())
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())

        session.setCommand("rewind-without-value")
        session.finishExecuteAndPop(session.getEvaluationContext())

        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("mother_case_1", session.getNeededDatum()!!.getDataId())
    }
}
