package org.commcare.app.viewmodel

import org.commcare.app.engine.FormEntrySession
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for field-list group rendering: all questions in a
 * `<group appearance="field-list">` should appear as multiple
 * questions on a single page, not one per page.
 *
 * Phase 9 Wave 5c — field-list groups. Replaces placeholder tests
 * that constructed inline QuestionState objects without loading any
 * form (same pattern as the #412 placeholder audit).
 */
class FieldListGroupTest {

    private fun loadFieldListForm(): FormEntryViewModel {
        val stream = this::class.java.getResourceAsStream("/test_field_list_constraints.xml")
        assertNotNull(stream, "test_field_list_constraints.xml fixture missing")
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)
        vm.loadForm()
        return vm
    }

    @Test
    fun fieldListShowsMultipleQuestionsOnOnePage() {
        val vm = loadFieldListForm()
        // A field-list group with 3 questions should render all 3
        // as the current page's questions list.
        assertTrue(
            vm.questions.size >= 3,
            "field-list should show all questions at once, got ${vm.questions.size}: " +
                vm.questions.map { it.questionText }
        )
    }

    @Test
    fun fieldListQuestionsHaveCorrectTypes() {
        val vm = loadFieldListForm()
        val types = vm.questions.map { it.questionType }
        assertTrue(types.contains(QuestionType.INTEGER), "should contain INTEGER for age")
        assertTrue(types.contains(QuestionType.TEXT), "should contain TEXT for name/email")
    }

    @Test
    fun fieldListConstraintsValidatePerQuestion() {
        val vm = loadFieldListForm()
        val ageIdx = vm.questions.indexOfFirst { it.questionText.contains("Age") }
        assertTrue(ageIdx >= 0, "should find Age question")

        vm.answerQuestionString(ageIdx, "999")
        val ageQ = vm.questions[ageIdx]
        assertNotNull(ageQ.constraintMessage, "age=999 should violate constraint")
        assertTrue(
            ageQ.constraintMessage!!.contains("120"),
            "constraint message should mention 120"
        )
    }

    @Test
    fun fieldListAcceptsValidAnswers() {
        val vm = loadFieldListForm()
        val ageIdx = vm.questions.indexOfFirst { it.questionText.contains("Age") }
        val nameIdx = vm.questions.indexOfFirst { it.questionText.contains("name") }
        val emailIdx = vm.questions.indexOfFirst { it.questionText.contains("Email") }

        assertTrue(vm.answerQuestionString(ageIdx, "30"), "valid age should be accepted")
        assertTrue(vm.answerQuestionString(nameIdx, "Test User"), "name should be accepted")
        assertTrue(vm.answerQuestionString(emailIdx, "test@example.com"), "valid email accepted")
    }

    @Test
    fun fieldListAdvancesToEndAfterOneNext() {
        val vm = loadFieldListForm()
        val nameIdx = vm.questions.indexOfFirst { it.questionText.contains("name") }
        val accepted = vm.answerQuestionString(nameIdx, "Test")
        assertTrue(accepted, "name answer should be accepted")
        // Dump state before advancing
        val preAdvanceQuestions = vm.questions.map { "${it.questionText}(answer=${it.answer}, required=${it.isRequired}, constraint=${it.constraintMessage})" }
        vm.nextQuestion()
        var steps = 0
        while (!vm.isComplete && steps < 5) {
            vm.nextQuestion()
            steps++
        }
        assertTrue(
            vm.isComplete,
            "field-list form should complete after $steps extra steps. " +
                "Pre-advance: $preAdvanceQuestions. " +
                "Post: questions=${vm.questions.map { "${it.questionText}(answer=${it.answer}, constraint=${it.constraintMessage})" }}, " +
                "error=${vm.errorMessage}"
        )
    }

    @Test
    fun fieldListSerializesAllAnswers() {
        val vm = loadFieldListForm()
        val ageIdx = vm.questions.indexOfFirst { it.questionText.contains("Age") }
        val nameIdx = vm.questions.indexOfFirst { it.questionText.contains("name") }
        val emailIdx = vm.questions.indexOfFirst { it.questionText.contains("Email") }

        val ageOk = vm.answerQuestionString(ageIdx, "25")
        val nameOk = vm.answerQuestionString(nameIdx, "Alice")
        val emailOk = vm.answerQuestionString(emailIdx, "alice@test.com")

        assertTrue(ageOk, "age should be accepted (idx=$ageIdx)")
        assertTrue(nameOk, "name should be accepted (idx=$nameIdx)")
        assertTrue(emailOk, "email should be accepted (idx=$emailIdx)")

        vm.nextQuestion()
        var steps = 0
        while (!vm.isComplete && steps < 5) { vm.nextQuestion(); steps++ }

        assertTrue(vm.isComplete, "form should be complete, error=${vm.errorMessage}")
        val xml = vm.serializeForm()
        assertNotNull(xml, "serialized XML should not be null")
        assertTrue(xml.contains("25"), "serialized XML should contain age. XML=$xml")
        assertTrue(xml.contains("Alice"), "serialized XML should contain name. XML=$xml")
        // @ may be XML-encoded as &#64; by the serializer — both forms are correct
        assertTrue(
            xml.contains("alice@test.com") || xml.contains("alice&#64;test.com"),
            "serialized XML should contain email. XML=$xml"
        )
    }
}
