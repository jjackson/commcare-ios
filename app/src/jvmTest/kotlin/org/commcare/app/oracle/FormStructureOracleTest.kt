package org.commcare.app.oracle

import org.commcare.app.engine.FormEntrySession
import org.commcare.app.viewmodel.FormEntryViewModel
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Oracle tests for Wave 2: repeat groups, field-list groups, skip logic, constraints.
 */
class FormStructureOracleTest {

    private val runner = OracleTestRunner()

    // --- Repeat group tests ---

    @Test
    fun testRepeatGroupNavigation() {
        val formDef = loadForm("/test_repeat_and_relevancy.xml")!!
        formDef.initialize(true, null)
        val model = FormEntryModel(formDef, FormEntryModel.REPEAT_STRUCTURE_LINEAR)
        val controller = FormEntryController(model)

        // Step through: BEGINNING -> has_children -> child_count -> repeat prompt
        controller.stepToNextEvent() // past BEGINNING
        assertEquals(FormEntryController.EVENT_QUESTION, model.getEvent()) // has_children
        controller.answerQuestion(StringData("yes"))

        controller.stepToNextEvent()
        assertEquals(FormEntryController.EVENT_QUESTION, model.getEvent()) // child_count
        controller.answerQuestion(IntegerData(2))

        // Navigate until we hit repeat prompt or question
        var event = controller.stepToNextEvent()
        while (event != FormEntryController.EVENT_PROMPT_NEW_REPEAT &&
            event != FormEntryController.EVENT_QUESTION &&
            event != FormEntryController.EVENT_END_OF_FORM
        ) {
            event = controller.stepToNextEvent()
        }
        assertEquals(FormEntryController.EVENT_PROMPT_NEW_REPEAT, event)

        // Create a repeat instance
        controller.newRepeat()
        event = controller.stepToNextEvent()
        // Should now be at first question in repeat (child/name)
        assertEquals(FormEntryController.EVENT_QUESTION, event)
    }

    @Test
    fun testRepeatSerializesWithChildren() {
        val result = runner.fillAndSerialize("/test_repeat_and_relevancy.xml") { model, controller ->
            // Answer has_children = yes
            controller.answerQuestion(StringData("yes"))
            controller.stepToNextEvent()

            // Answer child_count = 1
            controller.answerQuestion(IntegerData(1))

            // Navigate to repeat prompt
            var event = controller.stepToNextEvent()
            while (event != FormEntryController.EVENT_PROMPT_NEW_REPEAT &&
                event != FormEntryController.EVENT_END_OF_FORM
            ) {
                event = controller.stepToNextEvent()
            }

            // Add a repeat
            controller.newRepeat()
            controller.stepToNextEvent()
            // name
            controller.answerQuestion(StringData("Alice"))
            controller.stepToNextEvent()
            // age
            controller.answerQuestion(IntegerData(5))

            // Step to end
            event = controller.stepToNextEvent()
            while (event != FormEntryController.EVENT_END_OF_FORM) {
                if (event == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                    // skip adding more
                }
                event = controller.stepToNextEvent()
            }
        }

        assertTrue(result is FormResult.Success, "Serialize should succeed: $result")
        val xml = (result as FormResult.Success).xml
        assertTrue(xml.contains("Alice"), "XML should contain child name")
        assertTrue(xml.contains("<age>5</age>") || xml.contains(">5<"), "XML should contain child age")
    }

    // --- Skip logic (relevancy) tests ---

    @Test
    fun testRelevancyHidesQuestion() {
        val formDef = loadForm("/test_repeat_and_relevancy.xml")!!
        formDef.initialize(true, null)
        val model = FormEntryModel(formDef, FormEntryModel.REPEAT_STRUCTURE_LINEAR)
        val controller = FormEntryController(model)

        controller.stepToNextEvent() // has_children
        // Answer "no" — child_count should be skipped
        controller.answerQuestion(StringData("no"))

        val event = controller.stepToNextEvent()
        // Should skip child_count (not relevant) and go to repeat or notes
        // The engine automatically skips non-relevant questions during stepToNextEvent
        assertTrue(
            event != FormEntryController.EVENT_QUESTION ||
                model.getQuestionPrompt()?.getQuestion()?.getTextID() != "child_count",
            "child_count should be skipped when has_children != 'yes'"
        )
    }

    @Test
    fun testViewModelSkipLogic() {
        val formDef = loadForm("/test_repeat_and_relevancy.xml")!!
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)

        vm.loadForm()
        // First question should be has_children
        assertTrue(vm.questions.isNotEmpty(), "Should have questions loaded")
        assertEquals(1, vm.questions.size, "Should show one question at a time")
    }

    // --- Constraint tests ---

    @Test
    fun testConstraintViolation() {
        val formDef = loadForm("/test_constraints.xml")!!
        formDef.initialize(true, null)
        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        controller.stepToNextEvent() // into field-list group
        // In field-list, getQuestionPrompts() returns all prompts
        val prompts = controller.getQuestionPrompts()
        assertNotNull(prompts)
        assertTrue(prompts.size >= 2, "Field-list should have multiple prompts, got ${prompts.size}")

        // Try to set age to invalid value (200)
        val result = controller.answerQuestion(prompts[0].getIndex()!!, IntegerData(200))
        assertEquals(FormEntryController.ANSWER_CONSTRAINT_VIOLATED, result)

        // Constraint text should be available
        val constraintText = prompts[0].getConstraintText()
        assertNotNull(constraintText, "Constraint message should be set")
        assertTrue(constraintText.contains("0") && constraintText.contains("120"),
            "Constraint message should mention valid range: $constraintText")
    }

    @Test
    fun testConstraintAcceptsValidValue() {
        val formDef = loadForm("/test_constraints.xml")!!
        formDef.initialize(true, null)
        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        controller.stepToNextEvent()
        val prompts = controller.getQuestionPrompts()
        assertNotNull(prompts)

        // Valid age
        val result = controller.answerQuestion(prompts[0].getIndex()!!, IntegerData(25))
        assertEquals(FormEntryController.ANSWER_OK, result)
    }

    @Test
    fun testFieldListGroupShowsMultipleQuestions() {
        val formDef = loadForm("/test_constraints.xml")!!
        formDef.initialize(true, null)
        val model = FormEntryModel(formDef)
        val controller = FormEntryController(model)

        controller.stepToNextEvent()
        val prompts = controller.getQuestionPrompts()
        // Field-list should show all 3 questions (age, name, email) at once
        assertEquals(3, prompts.size, "Field-list should show all 3 questions")
    }

    @Test
    fun testViewModelConstraintMessage() {
        val formDef = loadForm("/test_constraints.xml")!!
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)

        vm.loadForm()
        // Field-list: all 3 questions visible
        assertEquals(3, vm.questions.size, "Should show 3 questions in field-list")

        // Answer age with invalid value
        vm.answerQuestion(0, IntegerData(200))
        // Should have constraint message on first question
        assertNotNull(vm.questions[0].constraintMessage,
            "First question should have constraint message")

        // Answer age with valid value
        vm.answerQuestion(0, IntegerData(25))
        // Constraint should be cleared
        val q0 = vm.questions[0]
        assertTrue(q0.constraintMessage == null,
            "Constraint should be cleared after valid answer, got: ${q0.constraintMessage}")
    }

    @Test
    fun testViewModelRequiredValidation() {
        val formDef = loadForm("/test_constraints.xml")!!
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)

        vm.loadForm()
        assertEquals(3, vm.questions.size)

        // Try to go next without filling required field (name)
        vm.nextQuestion()
        // Should not advance — name is required
        assertFalse(vm.isComplete, "Should not complete with missing required field")
    }

    // --- Helpers ---

    private fun loadForm(resource: String) =
        XFormUtils.getFormFromInputStream(this::class.java.getResourceAsStream(resource)!!)
}
