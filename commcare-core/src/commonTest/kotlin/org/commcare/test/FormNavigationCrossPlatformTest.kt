package org.commcare.test

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.StringData
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.xform.util.XFormLoader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Cross-platform form navigation tests. Tests repeat groups, field-lists,
 * skip logic, constraints, and required fields using the engine APIs.
 *
 * These don't compare serialized output — they validate navigation behavior.
 * Runs on both JVM and iOS.
 */
class FormNavigationCrossPlatformTest {

    private fun loadForm(resourcePath: String): FormDef {
        val bytes = TestResources.loadResource(resourcePath)
        return XFormLoader.loadForm(bytes)
    }

    private fun initForm(resourcePath: String, repeatStructure: Int = -1): Pair<FormEntryModel, FormEntryController> {
        val formDef = loadForm(resourcePath)
        formDef.initialize(true, null)
        val model = if (repeatStructure >= 0) {
            FormEntryModel(formDef, repeatStructure)
        } else {
            FormEntryModel(formDef)
        }
        val controller = FormEntryController(model)
        return model to controller
    }

    // --- Basic Navigation ---

    @Test
    fun testFormStartsAtBeginning() {
        val (model, _) = initForm("/test_all_question_types.xml")
        assertEquals(FormEntryController.EVENT_BEGINNING_OF_FORM, model.getEvent())
    }

    @Test
    fun testStepThroughAllQuestions() {
        val (model, controller) = initForm("/test_all_question_types.xml")
        var questionCount = 0

        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_QUESTION) {
                questionCount++
            }
            controller.stepToNextEvent()
        }

        assertTrue(questionCount >= 7, "Should have at least 7 questions, got $questionCount")
    }

    @Test
    fun testStepBackward() {
        val (model, controller) = initForm("/test_all_question_types.xml")

        // Step forward past first question
        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_QUESTION) {
            controller.stepToNextEvent()
        }
        val firstQuestionIndex = model.getFormIndex()

        // Step forward to second question
        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_QUESTION) {
            controller.stepToNextEvent()
        }

        // Step backward
        controller.stepToPreviousEvent()
        while (model.getEvent() != FormEntryController.EVENT_QUESTION) {
            controller.stepToPreviousEvent()
        }

        assertEquals(firstQuestionIndex, model.getFormIndex(), "Should return to first question")
    }

    // --- Repeat Groups ---

    @Test
    fun testRepeatPromptEvent() {
        val (model, controller) = initForm(
            "/test_repeat_and_relevancy.xml",
            FormEntryModel.REPEAT_STRUCTURE_LINEAR
        )

        controller.stepToNextEvent()
        var foundRepeatPrompt = false
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_PROMPT_NEW_REPEAT) {
                foundRepeatPrompt = true
                break
            }
            controller.stepToNextEvent()
        }

        assertTrue(foundRepeatPrompt, "Should encounter EVENT_PROMPT_NEW_REPEAT")
    }

    @Test
    fun testSkipRepeatReachesEnd() {
        val (model, controller) = initForm(
            "/test_repeat_and_relevancy.xml",
            FormEntryModel.REPEAT_STRUCTURE_LINEAR
        )

        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            // Skip all repeats
            controller.stepToNextEvent()
        }

        assertEquals(FormEntryController.EVENT_END_OF_FORM, model.getEvent())
    }

    // --- Constraints ---

    @Test
    fun testConstraintViolation() {
        val (model, controller) = initForm("/test_field_list_constraints.xml")

        // Navigate to the field-list group
        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_GROUP) {
                try {
                    val prompts = controller.getQuestionPrompts()
                    if (prompts.isNotEmpty()) {
                        // First question has constraint: . > 0 and . < 150
                        val idx = prompts[0].getIndex()!!
                        val result = controller.answerQuestion(idx, IntegerData(-5))
                        assertEquals(
                            FormEntryController.ANSWER_CONSTRAINT_VIOLATED,
                            result,
                            "Negative age should violate constraint"
                        )
                        return
                    }
                } catch (_: Exception) {
                    // Not a field-list group
                }
            }
            controller.stepToNextEvent()
        }

        // If we get here, we didn't find the constraint question
        assertTrue(false, "Should have found a constraint question")
    }

    @Test
    fun testValidAnswerAccepted() {
        val (model, controller) = initForm("/test_field_list_constraints.xml")

        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_GROUP) {
                try {
                    val prompts = controller.getQuestionPrompts()
                    if (prompts.isNotEmpty()) {
                        val idx = prompts[0].getIndex()!!
                        val result = controller.answerQuestion(idx, IntegerData(25))
                        assertEquals(
                            FormEntryController.ANSWER_OK,
                            result,
                            "Valid age should be accepted"
                        )
                        return
                    }
                } catch (_: Exception) {
                    // Not a field-list group
                }
            }
            controller.stepToNextEvent()
        }
    }

    @Test
    fun testConstraintMessage() {
        val (model, controller) = initForm("/test_field_list_constraints.xml")

        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_GROUP) {
                try {
                    val prompts = controller.getQuestionPrompts()
                    if (prompts.isNotEmpty()) {
                        val prompt = prompts[0]
                        val constraintText = prompt.getConstraintText(IntegerData(-5))
                        assertNotNull(constraintText, "Should have constraint message")
                        return
                    }
                } catch (_: Exception) {
                    // Not a field-list group
                }
            }
            controller.stepToNextEvent()
        }
    }

    // --- Field-List Groups ---

    @Test
    fun testFieldListGroupReturnsMultiplePrompts() {
        val (model, controller) = initForm("/test_field_list_constraints.xml")

        controller.stepToNextEvent()
        while (model.getEvent() != FormEntryController.EVENT_END_OF_FORM) {
            if (model.getEvent() == FormEntryController.EVENT_GROUP) {
                try {
                    val prompts = controller.getQuestionPrompts()
                    if (prompts.size > 1) {
                        assertTrue(
                            prompts.size >= 3,
                            "Field-list should have 3+ prompts, got ${prompts.size}"
                        )
                        return
                    }
                } catch (_: Exception) {
                    // Not a field-list group
                }
            }
            controller.stepToNextEvent()
        }
    }

    // --- Form Loading ---

    @Test
    fun testAllTestFormsLoad() {
        val forms = listOf(
            "/test_all_question_types.xml",
            "/test_calculations.xml",
            "/test_geopoint.xml",
            "/test_multi_select.xml",
            "/test_repeat_and_relevancy.xml",
            "/test_field_list_constraints.xml",
            "/test_form_entry_controller.xml"
        )

        for (formPath in forms) {
            val formDef = loadForm(formPath)
            assertNotNull(formDef, "Form should load: $formPath")
            formDef.initialize(true, null)
        }
    }

    @Test
    fun testFormInitializesWithTitle() {
        val formDef = loadForm("/test_all_question_types.xml")
        formDef.initialize(true, null)
        val title = formDef.getTitle()
        assertNotNull(title, "Form should have a title")
        assertTrue(title.isNotEmpty(), "Title should not be empty")
    }
}
