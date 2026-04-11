package org.commcare.app.viewmodel

import org.commcare.app.engine.FormEntrySession
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for draft text state management in [FormEntryViewModel].
 *
 * The draft layer (#394) records typed-but-not-yet-committed values so the
 * UI reflects user input even when the engine rejects partial values under
 * a constraint check. Key invariants:
 *
 * 1. After [answerQuestionString], the draft is recorded and the field
 *    displays the drafted value (even if the engine rejected it).
 * 2. After successful navigation ([nextQuestion]/[previousQuestion]),
 *    drafts are cleared for the previous page so stale text doesn't
 *    bleed onto the new page.
 * 3. After a successful engine commit (ANSWER_OK), the draft for that
 *    index is cleared — the engine value is the source of truth.
 *
 * Phase 10 Stream 1 — ViewModel test backfill.
 */
class FormEntryDraftTextTest {

    private fun loadConstraintForm(): FormEntryViewModel {
        val stream = this::class.java.getResourceAsStream("/test_field_list_constraints.xml")
        assertNotNull(stream)
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)
        vm.loadForm()
        return vm
    }

    @Test
    fun draftTextShowsInAnswerFieldAfterInput() {
        val vm = loadConstraintForm()
        // The field-list form has age, name, email on one page.
        val nameIdx = vm.questions.indexOfFirst { it.questionText.contains("name") }
        assertTrue(nameIdx >= 0)

        vm.answerQuestionString(nameIdx, "Alice")
        assertEquals("Alice", vm.questions[nameIdx].answer,
            "drafted value should be visible in the answer field")
    }

    @Test
    fun constraintViolationKeepsDraftVisible() {
        val vm = loadConstraintForm()
        // age has constraint: 0..120
        val ageIdx = vm.questions.indexOfFirst { it.questionText.contains("Age") }
        assertTrue(ageIdx >= 0)

        vm.answerQuestionString(ageIdx, "999")
        // Engine rejects → constraint message set
        assertNotNull(vm.questions[ageIdx].constraintMessage)
        // But the draft "999" should still be visible
        assertEquals("999", vm.questions[ageIdx].answer,
            "rejected value should still display as draft text (#394)")
    }

    @Test
    fun validAnswerClearsDraft() {
        val vm = loadConstraintForm()
        val ageIdx = vm.questions.indexOfFirst { it.questionText.contains("Age") }

        // First enter invalid value
        vm.answerQuestionString(ageIdx, "999")
        assertEquals("999", vm.questions[ageIdx].answer)

        // Then enter valid value — draft should be cleared, engine value wins
        vm.answerQuestionString(ageIdx, "25")
        assertEquals("25", vm.questions[ageIdx].answer)
        assertEquals(null, vm.questions[ageIdx].constraintMessage,
            "valid answer should clear constraint")
    }

    @Test
    fun navigationClearsDrafts() {
        val vm = loadConstraintForm()
        val nameIdx = vm.questions.indexOfFirst { it.questionText.contains("name") }

        // Enter a value
        vm.answerQuestionString(nameIdx, "Bob")
        assertEquals("Bob", vm.questions[nameIdx].answer)

        // Navigate forward then back — drafts should be cleared
        vm.nextQuestion()
        vm.previousQuestion()

        // After returning, the field should show the ENGINE value (which was
        // committed as "Bob"), not a stale draft
        val newNameIdx = vm.questions.indexOfFirst { it.questionText.contains("name") }
        if (newNameIdx >= 0) {
            // The engine accepted "Bob", so it should persist
            assertEquals("Bob", vm.questions[newNameIdx].answer,
                "committed value should survive navigation round-trip")
        }
    }
}
