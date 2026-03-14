package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.FormEntrySession
import org.commcare.app.engine.FormSerializer
import org.commcare.core.interfaces.UserSandbox
import org.commcare.core.process.XmlFormRecordProcessor
import org.javarosa.core.io.createByteArrayInputStream
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
    var isSubmitting by mutableStateOf(false)
        private set

    /** True when we're at a repeat prompt asking "Add another?" */
    var isRepeatPrompt by mutableStateOf(false)
        private set
    var repeatPromptText by mutableStateOf("")
        private set

    /** True when form is in read-only review mode (completed form review). */
    var isReadOnly by mutableStateOf(false)
        private set

    /** Draft form ID, set when resuming or after first save. */
    var draftFormId by mutableStateOf<String?>(null)

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
                    // Clear constraint message for this question
                    clearConstraint(index)
                    // Refresh questions — relevancy may have changed (skip logic)
                    updateQuestions()
                }
                FormEntryController.ANSWER_CONSTRAINT_VIOLATED -> {
                    val prompts = formSession.getPrompts()
                    val msg = if (index < prompts.size) {
                        prompts[index].getConstraintText() ?: "Constraint violated"
                    } else {
                        "Constraint violated"
                    }
                    setConstraint(index, msg)
                }
                FormEntryController.ANSWER_REQUIRED_BUT_EMPTY -> {
                    setConstraint(index, "This field is required")
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

    /**
     * Add a new repeat instance and continue to the first question in it.
     */
    fun addRepeat() {
        try {
            formSession.controller.newRepeat()
            isRepeatPrompt = false
            formSession.stepNext() // step into the new repeat
            advanceToQuestion()
            questionIndex++
            progress = (questionIndex.toFloat() / totalQuestions).coerceIn(0f, 1f)
            updateQuestions()
        } catch (e: Exception) {
            errorMessage = "Error adding repeat: ${e.message}"
        }
    }

    /**
     * Skip adding a new repeat and continue past the repeat group.
     */
    fun skipRepeat() {
        try {
            isRepeatPrompt = false
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

    fun nextQuestion() {
        try {
            // Validate all visible required questions before stepping
            val prompts = formSession.getPrompts()
            for ((i, prompt) in prompts.withIndex()) {
                if (prompt.isRequired() && prompt.getAnswerValue() == null) {
                    setConstraint(i, "This field is required")
                    return
                }
            }

            val event = formSession.stepNext()
            if (event == FormEntryController.EVENT_END_OF_FORM) {
                isComplete = true
            } else {
                advanceToQuestion()
                if (!isRepeatPrompt) {
                    questionIndex++
                    progress = (questionIndex.toFloat() / totalQuestions).coerceIn(0f, 1f)
                    updateQuestions()
                }
            }
        } catch (e: Exception) {
            errorMessage = "Navigation error: ${e.message}"
        }
    }

    fun previousQuestion() {
        try {
            isRepeatPrompt = false
            formSession.stepPrev()
            // Skip back past non-question events
            var event = formSession.currentEvent()
            while (event != FormEntryController.EVENT_QUESTION &&
                event != FormEntryController.EVENT_BEGINNING_OF_FORM
            ) {
                event = formSession.stepPrev()
            }
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
     * Serialize the form and process case blocks locally.
     * Applies case create/update/close to the sandbox immediately,
     * so case list reflects changes without waiting for server sync.
     * Returns the serialized XML for queueing, or null on error.
     */
    fun submitForm(sandbox: UserSandbox): String? {
        val xml = serializeForm() ?: return null
        try {
            val stream = createByteArrayInputStream(xml.encodeToByteArray())
            XmlFormRecordProcessor.process(sandbox, stream)
        } catch (e: Exception) {
            errorMessage = "Failed to process case updates: ${e.message}"
        }
        return xml
    }

    /**
     * Get the form's xmlns for submission tracking.
     */
    fun getFormXmlns(): String {
        return formSession.formDef.getInstance()?.getRoot()?.getNamespace() ?: ""
    }

    /**
     * Save current form state as a draft to the FormRecordViewModel.
     * Returns the draft form ID.
     */
    fun saveDraft(formRecordViewModel: FormRecordViewModel): String? {
        return try {
            val xml = FormSerializer.serializeForm(formSession.formDef)
            val xmlns = getFormXmlns()
            val id = formRecordViewModel.saveDraft(xml, xmlns, formTitle, draftFormId)
            draftFormId = id
            id
        } catch (e: Exception) {
            errorMessage = "Failed to save draft: ${e.message}"
            null
        }
    }

    /**
     * Enable read-only review mode. All questions are displayed but not editable.
     */
    fun enableReviewMode() {
        isReadOnly = true
    }

    private fun advanceToQuestion() {
        var event = formSession.currentEvent()
        while (event != FormEntryController.EVENT_QUESTION &&
            event != FormEntryController.EVENT_END_OF_FORM &&
            event != FormEntryController.EVENT_PROMPT_NEW_REPEAT
        ) {
            // Stop at field-list groups — they display all questions at once
            if (event == FormEntryController.EVENT_GROUP && isFieldList()) {
                break
            }
            event = formSession.stepNext()
        }
        when (event) {
            FormEntryController.EVENT_END_OF_FORM -> {
                isComplete = true
            }
            FormEntryController.EVENT_PROMPT_NEW_REPEAT -> {
                isRepeatPrompt = true
                repeatPromptText = formSession.model.getCaptionPrompt()?.getLongText()
                    ?: "Add another group?"
            }
        }
    }

    private fun isFieldList(): Boolean {
        return try {
            formSession.controller.isHostWithAppearance(
                formSession.model.getFormIndex(),
                FormEntryController.FIELD_LIST
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun updateQuestions() {
        questions = try {
            formSession.getPrompts().map { prompt ->
                QuestionState(
                    questionId = prompt.getIndex().toString(),
                    questionText = prompt.getQuestionText() ?: prompt.getLongText() ?: "",
                    questionType = mapControlType(prompt.getControlType(), prompt.getDataType()),
                    dataType = prompt.getDataType(),
                    answer = prompt.getAnswerValue()?.getDisplayText() ?: "",
                    isRequired = prompt.isRequired(),
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

    private fun setConstraint(index: Int, message: String) {
        if (index < questions.size) {
            questions = questions.toMutableList().also {
                it[index] = it[index].copy(constraintMessage = message)
            }
        }
    }

    private fun clearConstraint(index: Int) {
        if (index < questions.size && questions[index].constraintMessage != null) {
            questions = questions.toMutableList().also {
                it[index] = it[index].copy(constraintMessage = null)
            }
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
