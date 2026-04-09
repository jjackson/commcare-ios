package org.commcare.app.viewmodel

import org.commcare.app.engine.FormEntrySession
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Regression tests for multi-select question state management in
 * [FormEntryViewModel].
 *
 * Historical context: `updateQuestions()` used to rebuild the questions list
 * from the engine's prompts without carrying the currently-selected values
 * into `QuestionState.selectedChoices`. Every call to
 * `toggleMultiSelectChoice` triggers an `answerQuestion` → `updateQuestions`
 * cycle, so each new tap would immediately reset `selectedChoices` back to
 * `emptySet()` even though the engine had happily accepted the answer. The
 * visible symptom (caught during Phase 9 Wave 5 scouting of the Visit form
 * on iOS): tapping a checkbox briefly highlighted the row but the checkbox
 * never rendered as checked.
 *
 * The fix populates `selectedChoices` from the engine's `SelectMultiData`
 * answer inside `updateQuestions()`, so the UI always reflects whatever the
 * engine has accepted. These tests guard that behavior.
 */
class MultiSelectStateTest {

    /**
     * Load the test_multi_select.xml form, navigate to the symptoms
     * (select-multi) question, and verify that the test fixture is what
     * we expect before exercising toggle behavior.
     */
    private fun setupViewModelAtSymptomsQuestion(): FormEntryViewModel {
        val stream = this::class.java.getResourceAsStream("/test_multi_select.xml")
        assertNotNull(stream, "test_multi_select.xml fixture missing")
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)

        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)
        vm.loadForm()

        // Question 0 = favorite_color (select-one), Question 1 = symptoms
        // (select-multi). Navigate past the first to reach the multi-select.
        assertEquals(QuestionType.SELECT_ONE, vm.questions[0].questionType)
        vm.nextQuestion()

        // We should now be on the select-multi question.
        assertTrue(vm.questions.isNotEmpty(), "no questions after nextQuestion")
        assertEquals(
            QuestionType.SELECT_MULTI,
            vm.questions[0].questionType,
            "expected select-multi as second question"
        )
        return vm
    }

    @Test
    fun selectedChoicesPersistAfterSingleToggle() {
        val vm = setupViewModelAtSymptomsQuestion()

        vm.toggleMultiSelectChoice(0, "fever")

        // After a single toggle, the ViewModel should reflect the selection.
        // Before the fix, `updateQuestions()` ran inside `answerQuestion`
        // (triggered by the toggle) and wiped `selectedChoices` back to empty.
        assertEquals(
            setOf("fever"),
            vm.questions[0].selectedChoices,
            "selectedChoices should contain 'fever' after first toggle; " +
                "if empty, updateQuestions is dropping engine state"
        )
    }

    @Test
    fun selectedChoicesAccumulateAcrossMultipleToggles() {
        val vm = setupViewModelAtSymptomsQuestion()

        vm.toggleMultiSelectChoice(0, "fever")
        vm.toggleMultiSelectChoice(0, "cough")
        vm.toggleMultiSelectChoice(0, "headache")

        // All three selections should be present. Before the fix, each
        // subsequent toggle would start from an empty set because the
        // previous answer had been wiped by updateQuestions, so the final
        // state would only contain "headache".
        assertEquals(
            setOf("fever", "cough", "headache"),
            vm.questions[0].selectedChoices,
            "all three toggled choices should persist across successive calls"
        )
    }

    @Test
    fun togglingSelectedChoiceRemovesItFromState() {
        val vm = setupViewModelAtSymptomsQuestion()

        vm.toggleMultiSelectChoice(0, "fever")
        vm.toggleMultiSelectChoice(0, "cough")
        // Un-toggle fever — should leave only cough.
        vm.toggleMultiSelectChoice(0, "fever")

        assertEquals(
            setOf("cough"),
            vm.questions[0].selectedChoices,
            "un-toggling should remove a choice without affecting siblings"
        )
    }

    @Test
    fun selectedChoicesSurviveNavigationAwayAndBack() {
        val vm = setupViewModelAtSymptomsQuestion()

        vm.toggleMultiSelectChoice(0, "fever")
        vm.toggleMultiSelectChoice(0, "cough")

        // Navigate forward to the third question, then back to the
        // select-multi. The engine retains the answer across navigation, so
        // the UI state should reflect it when we return.
        vm.nextQuestion()
        vm.previousQuestion()

        assertEquals(
            QuestionType.SELECT_MULTI,
            vm.questions[0].questionType,
            "should have navigated back to the select-multi question"
        )
        assertEquals(
            setOf("fever", "cough"),
            vm.questions[0].selectedChoices,
            "selectedChoices should survive forward+back navigation"
        )
    }
}
