package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for form navigation data models.
 *
 * NOTE: the original swipe-threshold and enum-completeness tests in this
 * file were placeholders that asserted hardcoded constants (e.g.
 * `assertTrue(100f > 0)`) without constructing any real ViewModel or
 * screen. They were removed during the Phase 9 placeholder audit. Real
 * navigation behavior is covered by ViewModelIntegrationTest and the
 * Phase 9 Maestro E2E flows.
 */
class FormNavigationTest {

    @Test
    fun testQuestionStateDefaults() {
        val state = QuestionState(
            questionId = "q1",
            questionText = "What is your name?",
            questionType = QuestionType.TEXT
        )
        assertEquals("", state.answer)
        assertEquals(false, state.isRequired)
        assertEquals(null, state.constraintMessage)
        assertEquals(emptyList(), state.choices)
        assertEquals(null, state.appearance)
        assertEquals(emptySet(), state.selectedChoices)
        assertEquals(null, state.audioUri)
        assertEquals(null, state.imageUri)
    }

    @Test
    fun testQuestionStateWithMedia() {
        val state = QuestionState(
            questionId = "q2",
            questionText = "Listen and answer",
            questionType = QuestionType.TEXT,
            audioUri = "jr://audio/prompt.mp3",
            imageUri = "jr://images/diagram.png"
        )
        assertEquals("jr://audio/prompt.mp3", state.audioUri)
        assertEquals("jr://images/diagram.png", state.imageUri)
    }
}
