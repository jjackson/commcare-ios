package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.FormEntrySession
import org.commcare.app.engine.FormSerializer
import org.javarosa.core.model.Constants
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.helper.Selection
import org.javarosa.form.api.FormEntryController

/**
 * ViewModel for form entry. Wraps FormEntrySession to drive question navigation,
 * answer validation, and form completion.
 */
class FormEntryViewModel(
    private val formSession: FormEntrySession
) {
    var questions by mutableStateOf<List<QuestionState>>(emptyList())
        private set
    var formTitle by mutableStateOf("")
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

    private var questionIndex = 0
    private val totalQuestions: Int get() = formSession.getQuestionCount().coerceAtLeast(1)

    fun loadForm() {
        try {
            formSession.initialize()
            formTitle = formSession.getFormTitle()
            formSession.stepNext() // Move past BEGINNING_OF_FORM
            advanceToQuestion()
            updateQuestions()
        } catch (e: Exception) {
            errorMessage = "Failed to load form: ${e.message}"
        }
    }

    fun answerQuestion(index: Int, answer: IAnswerData?) {
        try {
            val result = formSession.answerAtIndex(index, answer)
            when (result) {
                FormEntryController.ANSWER_OK -> {
                    validationMessage = null
                }
                FormEntryController.ANSWER_CONSTRAINT_VIOLATED -> {
                    val prompts = formSession.getPrompts()
                    validationMessage = if (index < prompts.size) {
                        prompts[index].getConstraintText() ?: "Constraint violated"
                    } else {
                        "Constraint violated"
                    }
                }
                FormEntryController.ANSWER_REQUIRED_BUT_EMPTY -> {
                    validationMessage = "This field is required"
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error answering question: ${e.message}"
        }
    }

    fun answerQuestionString(index: Int, value: String) {
        val prompts = formSession.getPrompts()
        if (index < prompts.size) {
            val prompt = prompts[index]
            val data = FormEntrySession.createAnswerData(
                value, prompt.getControlType(), prompt.getDataType()
            )
            answerQuestion(index, data)
        }
    }

    /**
     * Toggle a choice in a select-multi question and submit the answer.
     */
    fun toggleMultiSelectChoice(index: Int, choice: String) {
        val current = questions.getOrNull(index) ?: return
        val updated = current.selectedChoices.toMutableSet()
        if (updated.contains(choice)) {
            updated.remove(choice)
        } else {
            updated.add(choice)
        }
        // Update local state
        questions = questions.toMutableList().also {
            it[index] = current.copy(selectedChoices = updated)
        }
        // Submit to engine as SelectMultiData
        val selections = ArrayList(updated.map { Selection(it) })
        val data: IAnswerData? = if (selections.isEmpty()) null else SelectMultiData(selections)
        answerQuestion(index, data)
    }

    fun nextQuestion() {
        try {
            val event = formSession.stepNext()
            if (event == FormEntryController.EVENT_END_OF_FORM) {
                isComplete = true
            } else {
                advanceToQuestion()
                questionIndex++
                progress = (questionIndex.toFloat() / totalQuestions).coerceIn(0f, 1f)
                updateQuestions()
            }
        } catch (e: Exception) {
            errorMessage = "Navigation error: ${e.message}"
        }
    }

    fun previousQuestion() {
        try {
            formSession.stepPrev()
            if (questionIndex > 0) questionIndex--
            progress = (questionIndex.toFloat() / totalQuestions).coerceIn(0f, 1f)
            updateQuestions()
        } catch (e: Exception) {
            errorMessage = "Navigation error: ${e.message}"
        }
    }

    /**
     * Serialize the completed form to XML string for submission.
     * Call this after isComplete becomes true.
     */
    fun serializeForm(): String? {
        return try {
            FormSerializer.serializeForm(formSession.formDef)
        } catch (e: Exception) {
            errorMessage = "Failed to serialize form: ${e.message}"
            null
        }
    }

    /**
     * Get the form's xmlns for submission tracking.
     */
    fun getFormXmlns(): String {
        return formSession.formDef.getInstance()?.getRoot()?.getNamespace() ?: ""
    }

    private fun advanceToQuestion() {
        var event = formSession.currentEvent()
        while (event != FormEntryController.EVENT_QUESTION &&
            event != FormEntryController.EVENT_END_OF_FORM &&
            event != FormEntryController.EVENT_PROMPT_NEW_REPEAT
        ) {
            event = formSession.stepNext()
        }
        if (event == FormEntryController.EVENT_END_OF_FORM) {
            isComplete = true
        }
    }

    private fun updateQuestions() {
        validationMessage = null
        questions = try {
            formSession.getPrompts().map { prompt ->
                QuestionState(
                    questionId = prompt.getIndex().toString(),
                    questionText = prompt.getQuestionText() ?: prompt.getLongText() ?: "",
                    questionType = mapControlType(prompt.getControlType(), prompt.getDataType()),
                    dataType = prompt.getDataType(),
                    answer = prompt.getAnswerValue()?.getDisplayText() ?: "",
                    isRequired = prompt.isRequired(),
                    isRelevant = true,
                    constraintMessage = null,
                    choices = prompt.getSelectChoices()?.map {
                        it.labelInnerText ?: it.value ?: ""
                    } ?: emptyList(),
                    appearance = prompt.getAppearanceHint()
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapControlType(controlType: Int, dataType: Int = 0): QuestionType {
        return when (controlType) {
            Constants.CONTROL_INPUT -> {
                when (dataType) {
                    Constants.DATATYPE_INTEGER -> QuestionType.INTEGER
                    Constants.DATATYPE_DECIMAL -> QuestionType.DECIMAL
                    Constants.DATATYPE_DATE -> QuestionType.DATE
                    Constants.DATATYPE_TIME -> QuestionType.TIME
                    else -> QuestionType.TEXT
                }
            }
            Constants.CONTROL_SELECT_ONE -> QuestionType.SELECT_ONE
            Constants.CONTROL_SELECT_MULTI -> QuestionType.SELECT_MULTI
            Constants.CONTROL_TRIGGER -> QuestionType.TRIGGER
            Constants.CONTROL_LABEL -> QuestionType.LABEL
            else -> QuestionType.TEXT
        }
    }
}

data class QuestionState(
    val questionId: String,
    val questionText: String,
    val questionType: QuestionType,
    val dataType: Int = 0,
    val answer: String = "",
    val isRequired: Boolean = false,
    val isRelevant: Boolean = true,
    val constraintMessage: String? = null,
    val choices: List<String> = emptyList(),
    val appearance: String? = null,
    val selectedChoices: Set<String> = emptySet()
)

enum class QuestionType {
    TEXT, INTEGER, DECIMAL, DATE, TIME,
    SELECT_ONE, SELECT_MULTI,
    LABEL, TRIGGER, GROUP, REPEAT
}
