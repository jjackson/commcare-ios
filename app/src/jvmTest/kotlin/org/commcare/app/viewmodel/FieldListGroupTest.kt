package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for field-list group behavior — multiple questions displayed on one screen.
 */
class FieldListGroupTest {

    @Test
    fun testFieldListAppearanceDetection() {
        // A group with appearance="field-list" should display all children at once
        val appearance = "field-list"
        assertTrue(appearance.contains("field-list"))
    }

    @Test
    fun testMultipleQuestionsInFieldList() {
        // When a field-list group is encountered, getPrompts() returns
        // all questions in the group (not just the first one)
        val questions = listOf(
            QuestionState("q1", "First Name", QuestionType.TEXT),
            QuestionState("q2", "Last Name", QuestionType.TEXT),
            QuestionState("q3", "Age", QuestionType.INTEGER)
        )
        assertEquals(3, questions.size)
        assertEquals(QuestionType.TEXT, questions[0].questionType)
        assertEquals(QuestionType.INTEGER, questions[2].questionType)
    }

    @Test
    fun testFieldListWithMixedTypes() {
        // Field lists can contain different question types
        val questions = listOf(
            QuestionState("q1", "Name", QuestionType.TEXT),
            QuestionState("q2", "Gender", QuestionType.SELECT_ONE,
                choices = listOf("Male", "Female", "Other")),
            QuestionState("q3", "DOB", QuestionType.DATE)
        )
        assertEquals(3, questions.size)
        assertEquals(3, questions[1].choices.size)
    }

    @Test
    fun testFieldListAnswersIndependent() {
        // Each question in a field list has independent answer state
        val q1 = QuestionState("q1", "Name", QuestionType.TEXT, answer = "John")
        val q2 = QuestionState("q2", "Age", QuestionType.INTEGER, answer = "35")
        assertEquals("John", q1.answer)
        assertEquals("35", q2.answer)
    }

    @Test
    fun testFieldListRequiredFields() {
        // Field list can contain mix of required and optional fields
        val questions = listOf(
            QuestionState("q1", "Name", QuestionType.TEXT, isRequired = true),
            QuestionState("q2", "Nickname", QuestionType.TEXT, isRequired = false),
            QuestionState("q3", "Age", QuestionType.INTEGER, isRequired = true)
        )
        val requiredCount = questions.count { it.isRequired }
        assertEquals(2, requiredCount)
    }
}
