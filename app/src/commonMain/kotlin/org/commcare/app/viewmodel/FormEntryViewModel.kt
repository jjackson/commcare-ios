package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages form entry state — question navigation, answers, validation.
 *
 * Wraps the CommCare FormEntryController/FormEntryModel for form navigation.
 * Question widgets read from and write to this ViewModel.
 */
class FormEntryViewModel {
    var currentQuestion by mutableStateOf<QuestionState?>(null)
        private set
    var formTitle by mutableStateOf("Form")
        private set
    var progress by mutableStateOf(0f)
        private set
    var isComplete by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var validationMessage by mutableStateOf<String?>(null)
        private set
    var isSubmitting by mutableStateOf(false)
        private set

    /**
     * Load a form by XForm namespace.
     * In a full implementation, this would use FormEntryController to parse
     * the XForm and initialize the model.
     */
    fun loadForm(formXmlns: String) {
        formTitle = "Form Entry"
        currentQuestion = QuestionState(
            questionId = "placeholder",
            questionText = "Form entry is not yet implemented.\nForm: $formXmlns",
            questionType = QuestionType.LABEL,
            answer = null,
            isRequired = false,
            isRelevant = true,
            constraintMessage = null
        )
        progress = 0f
        isComplete = false
    }

    fun answerQuestion(answer: String?) {
        currentQuestion = currentQuestion?.copy(answer = answer)
        validationMessage = null
    }

    fun nextQuestion(): Boolean {
        // Validate current answer
        val q = currentQuestion ?: return false
        if (q.isRequired && q.answer.isNullOrBlank()) {
            validationMessage = "This question is required"
            return false
        }

        // In full implementation: call FormEntryController.stepToNextEvent()
        // For now, mark as complete
        isComplete = true
        progress = 1f
        return true
    }

    fun previousQuestion(): Boolean {
        // In full implementation: call FormEntryController.stepToPreviousEvent()
        return false
    }

    fun submitForm() {
        isSubmitting = true
        // In full implementation: serialize form XML, POST to HQ
        // For now, just mark as done
        isSubmitting = false
        isComplete = true
    }
}

data class QuestionState(
    val questionId: String,
    val questionText: String,
    val questionType: QuestionType,
    val answer: String?,
    val isRequired: Boolean,
    val isRelevant: Boolean,
    val constraintMessage: String?,
    val choices: List<String> = emptyList()
)

enum class QuestionType {
    TEXT,
    INTEGER,
    DECIMAL,
    DATE,
    SELECT_ONE,
    SELECT_MULTI,
    LABEL,
    GROUP,
    REPEAT
}
