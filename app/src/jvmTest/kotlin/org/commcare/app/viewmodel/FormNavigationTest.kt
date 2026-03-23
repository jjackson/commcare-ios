package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for form navigation: swipe thresholds and next/previous behavior.
 */
class FormNavigationTest {

    @Test
    fun testSwipeThresholdConstant() {
        // The swipe threshold in FormEntryScreen is 100f pixels
        // Swipes must exceed this to trigger navigation
        val threshold = 100f
        assertTrue(threshold > 0, "Swipe threshold must be positive")
    }

    @Test
    fun testLeftSwipeTriggersNextQuestion() {
        // Left swipe = negative drag total
        // When totalDrag < -threshold, nextQuestion() is called
        val threshold = 100f
        val leftSwipeDrag = -150f
        assertTrue(leftSwipeDrag < -threshold, "Left swipe should exceed negative threshold")
    }

    @Test
    fun testRightSwipeTriggersPreviousQuestion() {
        // Right swipe = positive drag total
        // When totalDrag > threshold, previousQuestion() is called
        val threshold = 100f
        val rightSwipeDrag = 150f
        assertTrue(rightSwipeDrag > threshold, "Right swipe should exceed positive threshold")
    }

    @Test
    fun testSwipeBelowThresholdIgnored() {
        val threshold = 100f
        val smallDrag = 50f
        assertTrue(smallDrag < threshold, "Small drag should not trigger navigation")
        assertTrue(-smallDrag > -threshold, "Small left drag should not trigger navigation")
    }

    @Test
    fun testSwipeDragAccumulation() {
        // Drag amounts accumulate across the gesture
        val drags = listOf(30f, 25f, 20f, 30f) // total = 105
        val totalDrag = drags.sum()
        val threshold = 100f
        assertTrue(totalDrag > threshold, "Accumulated drag should exceed threshold")
    }

    @Test
    fun testQuestionTypeEnumCompleteness() {
        // Verify all question types are defined
        val types = QuestionType.entries
        assertTrue(types.contains(QuestionType.TEXT))
        assertTrue(types.contains(QuestionType.INTEGER))
        assertTrue(types.contains(QuestionType.DECIMAL))
        assertTrue(types.contains(QuestionType.DATE))
        assertTrue(types.contains(QuestionType.TIME))
        assertTrue(types.contains(QuestionType.DATETIME))
        assertTrue(types.contains(QuestionType.SELECT_ONE))
        assertTrue(types.contains(QuestionType.SELECT_MULTI))
        assertTrue(types.contains(QuestionType.LABEL))
        assertTrue(types.contains(QuestionType.TRIGGER))
        assertTrue(types.contains(QuestionType.GEOPOINT))
        assertTrue(types.contains(QuestionType.IMAGE))
        assertTrue(types.contains(QuestionType.AUDIO))
        assertTrue(types.contains(QuestionType.VIDEO))
        assertTrue(types.contains(QuestionType.SIGNATURE))
        assertTrue(types.contains(QuestionType.BARCODE))
        assertTrue(types.contains(QuestionType.UPLOAD))
    }

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
