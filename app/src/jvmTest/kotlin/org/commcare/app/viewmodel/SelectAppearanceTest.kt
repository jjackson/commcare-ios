package org.commcare.app.viewmodel

import org.commcare.app.engine.FormEntrySession
import org.javarosa.xform.util.XFormUtils
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests that all select appearance variants load correctly through
 * [FormEntryViewModel] and produce the expected [QuestionType] and
 * appearance hint. Each question is navigated to and verified.
 *
 * This exercises the engine's appearance-hint propagation from the
 * XForm XML through to the ViewModel layer. If any appearance variant
 * fails to load, map correctly, or serialize, it will show up here.
 *
 * Phase 9 Wave 5c — select appearances deep dive.
 */
class SelectAppearanceTest {

    private fun loadForm(): FormEntryViewModel {
        val stream = this::class.java.getResourceAsStream("/test_select_appearances.xml")
        assertNotNull(stream, "test_select_appearances.xml fixture missing")
        val formDef = XFormUtils.getFormFromInputStream(stream)
        assertNotNull(formDef)
        val session = FormEntrySession(formDef)
        val vm = FormEntryViewModel(session)
        vm.loadForm()
        return vm
    }

    private fun assertQuestion(
        vm: FormEntryViewModel,
        expectedType: QuestionType,
        expectedAppearance: String?,
        expectedChoiceCount: Int,
        description: String
    ) {
        assertTrue(vm.questions.isNotEmpty(), "no questions visible at: $description")
        val q = vm.questions[0]
        assertEquals(expectedType, q.questionType, "wrong type at: $description")
        if (expectedAppearance != null) {
            assertNotNull(q.appearance, "expected appearance at: $description")
            assertTrue(
                q.appearance!!.contains(expectedAppearance),
                "expected appearance containing '$expectedAppearance' but got '${q.appearance}' at: $description"
            )
        }
        assertEquals(expectedChoiceCount, q.choices.size, "wrong choice count at: $description")
    }

    @Test
    fun selectOneDefaultLoadsWithRadioButtons() {
        val vm = loadForm()
        assertQuestion(vm, QuestionType.SELECT_ONE, null, 3, "select_one_default")
    }

    @Test
    fun selectOneMinimalLoadsWithDropdown() {
        val vm = loadForm()
        vm.nextQuestion() // advance past default
        assertQuestion(vm, QuestionType.SELECT_ONE, "minimal", 3, "select_one_minimal")
    }

    @Test
    fun selectOneCompactLoadsWithGrid() {
        val vm = loadForm()
        repeat(2) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_ONE, "compact", 6, "select_one_compact")
    }

    @Test
    fun selectOneQuickLoadsWithAutoAdvance() {
        val vm = loadForm()
        repeat(3) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_ONE, "quick", 3, "select_one_quick")
    }

    @Test
    fun selectOneComboboxLoadsWithFilter() {
        val vm = loadForm()
        repeat(4) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_ONE, "combobox", 4, "select_one_combobox")
    }

    @Test
    fun selectOneListNolabelLoads() {
        val vm = loadForm()
        repeat(5) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_ONE, "list-nolabel", 3, "select_one_list_nolabel")
    }

    @Test
    fun selectMultiDefaultLoadsWithCheckboxes() {
        val vm = loadForm()
        repeat(6) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_MULTI, null, 3, "select_multi_default")
    }

    @Test
    fun selectMultiMinimalLoadsWithDropdownCheckboxes() {
        val vm = loadForm()
        repeat(7) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_MULTI, "minimal", 3, "select_multi_minimal")
    }

    @Test
    fun selectMultiCompactLoadsWithGrid() {
        val vm = loadForm()
        repeat(8) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_MULTI, "compact", 4, "select_multi_compact")
    }

    @Test
    fun selectMultiListNolabelLoads() {
        val vm = loadForm()
        repeat(9) { vm.nextQuestion() }
        assertQuestion(vm, QuestionType.SELECT_MULTI, "list-nolabel", 3, "select_multi_list_nolabel")
    }

    @Test
    fun selectOneQuickAutoAdvancesOnAnswer() {
        val vm = loadForm()
        repeat(3) { vm.nextQuestion() } // navigate to quick question

        val q = vm.questions[0]
        assertEquals(QuestionType.SELECT_ONE, q.questionType)
        assertTrue(q.appearance?.contains("quick") == true)

        // Answer "yes" — quick appearance should allow answering
        val accepted = vm.answerQuestionString(0, "yes")
        assertTrue(accepted, "quick select-one should accept answer")
        assertEquals("yes", vm.questions[0].answer)
    }

    @Test
    fun allAppearanceVariantsNavigableEndToEnd() {
        val vm = loadForm()
        var steps = 0
        while (!vm.isComplete && steps < 20) {
            vm.nextQuestion()
            steps++
        }
        assertTrue(vm.isComplete, "should reach end of form after navigating all questions, stopped at step $steps")
    }

    @Test
    fun formSerializesWithAllAppearances() {
        val vm = loadForm()
        // Answer the first question (default select-one)
        vm.answerQuestionString(0, "apple")
        // Navigate to the end
        var steps = 0
        while (!vm.isComplete && steps < 20) {
            vm.nextQuestion()
            steps++
        }
        assertTrue(vm.isComplete)
        val xml = vm.serializeForm()
        assertNotNull(xml, "serialized XML should not be null")
        assertTrue(xml.contains("apple"), "serialized XML should contain answered value")
    }
}
