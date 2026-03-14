package org.commcare.backend.suite.model.test

import org.cli.MockSessionUtils
import org.commcare.core.interfaces.MemoryVirtualDataInstanceStorage
import org.commcare.session.SessionFrame
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.StackFrameStep
import org.commcare.test.utilities.MockApp
import org.commcare.test.utilities.PersistableSandbox
import org.commcare.util.screen.QueryScreen
import org.javarosa.core.util.ListMultimap
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Created by amstone326 on 8/7/15.
 */
class StackFrameStepTests {

    private lateinit var mSandbox: PersistableSandbox

    private lateinit var commandIdStepV1: StackFrameStep
    private lateinit var commandIdStepV2: StackFrameStep

    private lateinit var caseIdStepV1: StackFrameStep
    private lateinit var caseIdStepV2: StackFrameStep

    private lateinit var formXmlnsStepV1: StackFrameStep
    private lateinit var formXmlnsStepV2: StackFrameStep

    private lateinit var datumComputedStepV1: StackFrameStep
    private lateinit var datumComputedStepV2: StackFrameStep

    private lateinit var stepWithExtras: StackFrameStep
    private lateinit var stepWithBadExtras: StackFrameStep

    @Before
    fun setUp() {
        mSandbox = PersistableSandbox()

        commandIdStepV1 = StackFrameStep(SessionFrame.STATE_COMMAND_ID, "id", null)
        commandIdStepV2 = StackFrameStep(SessionFrame.STATE_COMMAND_ID, "id", null)

        caseIdStepV1 = StackFrameStep(SessionFrame.STATE_DATUM_VAL, "id", "case_val")
        caseIdStepV2 = StackFrameStep(SessionFrame.STATE_DATUM_VAL, "id", null)

        formXmlnsStepV1 = StackFrameStep(SessionFrame.STATE_FORM_XMLNS, "xmlns_id1", null)
        formXmlnsStepV2 = StackFrameStep(SessionFrame.STATE_FORM_XMLNS, "xmlns_id2", null)

        datumComputedStepV1 = StackFrameStep(SessionFrame.STATE_DATUM_COMPUTED, "datum_val_id", "datum_val1")
        datumComputedStepV2 = StackFrameStep(SessionFrame.STATE_DATUM_COMPUTED, "datum_val_id", "datum_val2")

        // frame steps can store externalizable data, such as ints, Strings,
        // or anything that implements Externalizable
        stepWithExtras = StackFrameStep(SessionFrame.STATE_DATUM_COMPUTED, "datum_val_id", "datum_val2")
        stepWithExtras.addExtra("key", 123)
        stepWithExtras.addExtra("key", 234)

        // Demonstrate how frame steps can't store non-externalizable data in extras
        stepWithBadExtras = StackFrameStep(SessionFrame.STATE_DATUM_COMPUTED, "datum_val_id", "datum_val2")
        stepWithBadExtras.addExtra("key", ByteArrayInputStream(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun equalityTests() {
        assertTrue(
            "Identical StackFrameSteps were not equal",
            commandIdStepV1 == commandIdStepV2
        )

        assertFalse(
            "StackFrameStep was equal to null",
            commandIdStepV1 == null
        )

        assertFalse(
            "StackFrameSteps with different command types were equal",
            commandIdStepV1 == caseIdStepV2
        )

        assertFalse(
            "StackFrameSteps with different ids were equal",
            formXmlnsStepV1 == formXmlnsStepV2
        )

        assertFalse(
            "StackFrameSteps with different values were equal",
            datumComputedStepV1 == datumComputedStepV2
        )

        assertFalse(
            "StackFrameSteps where one value is null and one is non-null were equal",
            caseIdStepV1 == caseIdStepV2
        )
        assertFalse(
            "StackFrameSteps where one value is null and one is non-null were equal",
            caseIdStepV2 == caseIdStepV1
        )
    }

    @Test
    fun serializationTest() {
        var serializedStep = PersistableSandbox.serialize(commandIdStepV1)
        var deserialized = mSandbox.deserialize(serializedStep, StackFrameStep::class.java)
        assertTrue(
            "Serialization resulted in altered StackFrameStep",
            commandIdStepV1 == deserialized
        )

        serializedStep = PersistableSandbox.serialize(stepWithExtras)
        deserialized = mSandbox.deserialize(serializedStep, StackFrameStep::class.java)
        assertTrue("", stepWithExtras == deserialized)

        var failed = false
        try {
            PersistableSandbox.serialize(stepWithBadExtras)
        } catch (e: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    /**
     * Confirm that when stepping back after a stack push, we remove all pushed data
     */
    @Test
    fun stepBackFromStackPush() {
        val mApp = MockApp("/case_title_form_loading/")
        val session = mApp.getSession()
        session.setCommand("m0")
        session.setComputedDatum()
        val entityDatum = session.getNeededDatum() as EntityDatum
        val actions = session.getDetail(entityDatum.getShortDetail())!!.getCustomActions(session.getEvaluationContext())
        if (actions == null || actions.isEmpty()) {
            Assert.fail("Detail screen stack action was missing from app!")
        }
        // We're using the second action for this screen which requires us to still need another datum
        val dblManagement = actions[1]
        assertEquals(1, session.getFrame().getSteps().size)
        session.executeStackOperations(dblManagement.stackOperations!!, session.getEvaluationContext())
        assertEquals(5, session.getFrame().getSteps().size)
        session.stepBack()
        assertEquals(1, session.getFrame().getSteps().size)
    }

    /**
     * Test that stacks with queries in them work and preserve all the data elements they contain
     * even if they have duplicate keys.
     */
    @Test
    fun stackWithQueries() {
        val mApp = MockApp("/queries_in_entry_and_stack/")
        val session = mApp.getSession()
        session.setCommand("m0-f0")

        // check that session datum requirement is satisfied
        assertEquals(SessionFrame.STATE_DATUM_VAL, session.getNeededData())
        assertEquals("case_id", session.getNeededDatum()!!.getDataId())
        session.setEntityDatum("case_id", "case_one")

        assertEquals(SessionFrame.STATE_QUERY_REQUEST, session.getNeededData())
        // construct the screen
        // mock the query response
        val response = this.javaClass.getResourceAsStream("/case_claim_example/query_response.xml")
        val screen = QueryScreen(
            "username", "password",
            System.out, MemoryVirtualDataInstanceStorage(), MockSessionUtils(response)
        )
        screen.init(session)

        // perform the query
        val success = screen.handleInputAndUpdateSession(session, "", false, null, true)
        assertTrue(success)

        assertNull(session.getNeededDatum())

        // execute entry stack
        session.finishExecuteAndPop(session.getEvaluationContext())

        // validate that the query step has all the correct entries (including 2 case_id entries)
        val steps = session.getFrame().getSteps()
        assertEquals(4, steps.size)
        val queryFrame = steps[2]
        assertEquals(SessionFrame.STATE_QUERY_REQUEST, queryFrame.getElementType())

        val expected = ListMultimap.create<String, String>()
        expected.put("case_type", "patient")
        expected.put("x_commcare_data_registry", "test")
        expected.put("case_id", "case_one")
        expected.put("case_id", "dupe1")
        assertEquals(expected, queryFrame.getExtras())
    }
}
