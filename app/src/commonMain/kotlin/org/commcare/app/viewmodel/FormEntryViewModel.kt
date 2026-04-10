package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.FormEntrySession
import org.commcare.app.engine.FormSerializer
import org.commcare.app.ui.PersistentTileData
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

    /** Persistent case tile data shown during form entry, set by HomeScreen. */
    var persistentTileData by mutableStateOf<PersistentTileData?>(null)

    private var questionIndex = 0
    private val totalQuestions: Int get() = formSession.getQuestionCount().coerceAtLeast(1)

    /**
     * Per-question-index draft text for the currently-visible page. Typed-but-
     * not-yet-committed values live here so the UI can reflect what the user
     * has entered even when the XForm engine rejects partial values under a
     * `constraint` check (see issue #394).
     *
     * The draft for index N is the source of truth for what the text field
     * displays. `updateQuestions()` copies drafts into `QuestionState.answer`.
     * On successful commit, the draft is cleared for that index. On navigation
     * (next/prev), all drafts are cleared so stale text doesn't bleed onto
     * the new page.
     */
    private var draftTexts by mutableStateOf<Map<Int, String>>(emptyMap())

    fun loadForm() {
        try {
            formSession.initialize()
            formTitle = formSession.getFormTitle()
            formSession.stepNext() // Move past BEGINNING_OF_FORM
            advanceToQuestion()
            updateQuestions()
            // Debug: if no questions loaded, report form state
            if (questions.isEmpty() && !isComplete && !isRepeatPrompt) {
                val event = formSession.currentEvent()
                val eventName = when (event) {
                    FormEntryController.EVENT_BEGINNING_OF_FORM -> "BEGINNING"
                    FormEntryController.EVENT_END_OF_FORM -> "END"
                    FormEntryController.EVENT_QUESTION -> "QUESTION"
                    FormEntryController.EVENT_GROUP -> "GROUP"
                    FormEntryController.EVENT_REPEAT -> "REPEAT"
                    FormEntryController.EVENT_PROMPT_NEW_REPEAT -> "NEW_REPEAT"
                    else -> "UNKNOWN($event)"
                }
                errorMessage = "No questions at event=$eventName, prompts=${formSession.getPrompts().size}"
            }
        } catch (e: Exception) {
            val causeChain = buildString {
                var current: Throwable? = e
                while (current != null) {
                    append("${current::class.simpleName}: ${current.message}")
                    current = current.cause
                    if (current != null) append(" → ")
                }
            }
            errorMessage = "Failed to load form: $causeChain"
        }
    }

    /**
     * Load available languages into the LanguageViewModel from this form's localizer.
     */
    fun loadLanguages(languageViewModel: LanguageViewModel) {
        val localizer = formSession.formDef.getLocalizer() ?: return
        languageViewModel.loadLanguages(localizer)
    }

    /**
     * Refresh form display after a language change.
     */
    fun refreshAfterLanguageChange() {
        formTitle = formSession.getFormTitle()
        updateQuestions()
    }

    /**
     * Answer a question. Returns true if the answer was accepted (ANSWER_OK),
     * false if it violated a constraint or was otherwise rejected.
     */
    fun answerQuestion(index: Int, answer: IAnswerData?): Boolean {
        try {
            val result = formSession.answerAtIndex(index, answer)
            when (result) {
                FormEntryController.ANSWER_OK -> {
                    // Clear constraint message for this question
                    clearConstraint(index)
                    // Commit succeeded — drop any draft for this index since
                    // the engine now owns the value.
                    if (draftTexts.containsKey(index)) {
                        draftTexts = draftTexts - index
                    }
                    // Refresh questions — relevancy may have changed (skip logic)
                    updateQuestions()
                    return true
                }
                FormEntryController.ANSWER_CONSTRAINT_VIOLATED -> {
                    val prompts = formSession.getPrompts()
                    val msg = if (index < prompts.size) {
                        prompts[index].getConstraintText() ?: "Constraint violated"
                    } else {
                        "Constraint violated"
                    }
                    setConstraint(index, msg)
                    // Keep whatever draft text is on this index so the user
                    // sees their input. Refresh so the field re-renders.
                    updateQuestions()
                }
                FormEntryController.ANSWER_REQUIRED_BUT_EMPTY -> {
                    setConstraint(index, "This field is required")
                }
            }
        } catch (e: Exception) {
            errorMessage = "Error answering question: ${e.message}"
        }
        return false
    }

    /**
     * Answer a question with a string value. Returns true if accepted.
     *
     * Always records the typed value as a draft for the given index (see
     * `draftTexts`) so the UI reflects the user's input even when the engine
     * rejects the partial value under a constraint check. See #394.
     */
    fun answerQuestionString(index: Int, value: String): Boolean {
        // Record the draft first — this is what the field displays next recomp.
        draftTexts = draftTexts + (index to value)

        val prompts = formSession.getPrompts()
        if (index < prompts.size) {
            val prompt = prompts[index]
            val data = FormEntrySession.createAnswerData(
                value, prompt.getControlType(), prompt.getDataType()
            )
            return answerQuestion(index, data)
        }
        // Even if the engine doesn't have this index, refresh so the draft
        // shows in the field.
        updateQuestions()
        return false
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
     * Capture an image for the question at the given index.
     */
    fun captureImage(index: Int, imageCapture: org.commcare.app.platform.PlatformImageCapture) {
        imageCapture.captureFromCamera { path ->
            if (path != null) answerQuestionString(index, path)
        }
    }

    /**
     * Capture audio for the question at the given index.
     */
    fun captureAudio(index: Int, audioCapture: org.commcare.app.platform.PlatformAudioCapture) {
        audioCapture.startRecording { path ->
            if (path != null) answerQuestionString(index, path)
        }
    }

    /**
     * Capture a signature for the question at the given index.
     */
    fun captureSignature(index: Int, signatureCapture: org.commcare.app.platform.PlatformSignatureCapture) {
        signatureCapture.captureSignature { path ->
            if (path != null) answerQuestionString(index, path)
        }
    }

    /**
     * Capture GPS location for the question at the given index.
     */
    fun captureLocation(index: Int, locationProvider: org.commcare.app.platform.PlatformLocationProvider) {
        locationProvider.requestLocation { result ->
            if (result != null) answerQuestionString(index, result.toGeoPointString())
        }
    }

    /**
     * Scan a barcode for the question at the given index.
     */
    fun scanBarcode(index: Int, barcodeScanner: org.commcare.app.platform.PlatformBarcodeScanner) {
        barcodeScanner.scanBarcode { value ->
            if (value != null) answerQuestionString(index, value)
        }
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
            // Validate all visible required questions before stepping.
            // Drafts that haven't been committed are NOT treated as "has value"
            // here — the user must have typed a value that the engine accepted
            // before they can advance. For single-answer fields this works
            // because every successful keystroke commits the value.
            val prompts = formSession.getPrompts()
            for ((i, prompt) in prompts.withIndex()) {
                if (prompt.isRequired() && prompt.getAnswerValue() == null) {
                    setConstraint(i, "This field is required")
                    return
                }
            }

            // Clear draft map for a fresh slate on the next page.
            draftTexts = emptyMap()

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
            // Discard any drafts on this page — user is navigating away.
            draftTexts = emptyMap()
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

    /**
     * Determine the next navigation destination after form completion.
     * Returns a hint for the UI layer to decide what screen to show.
     */
    fun getPostCompletionDestination(): PostFormDestination {
        return PostFormDestination.RETURN_TO_MENU
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
        // Preserve existing constraint messages across refreshes. The engine
        // doesn't track constraint messages — they live only in our UI state
        // (set via setConstraint / cleared via clearConstraint). If we rebuilt
        // the list with `constraintMessage = null` on every refresh, calling
        // updateQuestions() from the CONSTRAINT_VIOLATED path would wipe the
        // message we just set. Keying on questionId so the preservation still
        // works when the form advances to a different set of questions.
        val priorConstraints = questions.associate { it.questionId to it.constraintMessage }
        questions = try {
            formSession.getPrompts().mapIndexed { index, prompt ->
                val questionId = prompt.getIndex().toString()
                val answerValue = prompt.getAnswerValue()
                val engineValue = answerValue?.getDisplayText() ?: ""
                // Draft always wins over engine value — that's the whole point
                // of the draft layer (#394). Drafts are cleared on successful
                // commit and on navigation.
                val displayValue = draftTexts[index] ?: engineValue
                // For select-multi, extract the selected values from the
                // engine's SelectMultiData so the checkbox UI can reflect the
                // current selection. Without this, every refresh (triggered
                // by toggleMultiSelectChoice → answerQuestion → updateQuestions)
                // would reset selectedChoices to empty and the UI would never
                // show a checked box even though the engine has accepted the
                // answer.
                val selectedChoices: Set<String> = if (answerValue is SelectMultiData) {
                    answerValue.getValue().map { it.getValue() }.toSet()
                } else {
                    emptySet()
                }
                QuestionState(
                    questionId = questionId,
                    questionText = prompt.getQuestionText() ?: prompt.getLongText() ?: "",
                    questionType = mapControlType(prompt.getControlType(), prompt.getDataType(), prompt.getAppearanceHint()),
                    dataType = prompt.getDataType(),
                    answer = displayValue,
                    isRequired = prompt.isRequired(),
                    constraintMessage = priorConstraints[questionId],
                    choices = prompt.getSelectChoices()?.map {
                        it.labelInnerText ?: it.value ?: ""
                    } ?: emptyList(),
                    appearance = prompt.getAppearanceHint(),
                    selectedChoices = selectedChoices,
                    audioUri = try { prompt.getAudioText() } catch (_: Exception) { null },
                    imageUri = try { prompt.getImageText() } catch (_: Exception) { null }
                )
            }
        } catch (e: Exception) {
            errorMessage = "Question load error: ${e::class.simpleName}: ${e.message}"
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

    private fun mapControlType(
        controlType: Int,
        dataType: Int = 0,
        appearance: String? = null
    ): QuestionType {
        return when (controlType) {
            Constants.CONTROL_INPUT -> {
                when (dataType) {
                    Constants.DATATYPE_INTEGER -> QuestionType.INTEGER
                    Constants.DATATYPE_DECIMAL -> QuestionType.DECIMAL
                    Constants.DATATYPE_DATE -> QuestionType.DATE
                    Constants.DATATYPE_TIME -> QuestionType.TIME
                    Constants.DATATYPE_DATE_TIME -> QuestionType.DATETIME
                    Constants.DATATYPE_GEOPOINT -> QuestionType.GEOPOINT
                    else -> QuestionType.TEXT
                }
            }
            Constants.CONTROL_SELECT_ONE -> QuestionType.SELECT_ONE
            Constants.CONTROL_SELECT_MULTI -> QuestionType.SELECT_MULTI
            Constants.CONTROL_TRIGGER -> QuestionType.TRIGGER
            Constants.CONTROL_LABEL -> QuestionType.LABEL
            Constants.CONTROL_UPLOAD -> {
                // The engine maps all <upload> elements to CONTROL_UPLOAD
                // regardless of mediatype. Differentiate by appearance
                // (signature) or by the prompt's data type hint.
                when {
                    appearance?.contains("signature") == true -> QuestionType.SIGNATURE
                    else -> QuestionType.IMAGE
                }
            }
            Constants.CONTROL_IMAGE_CHOOSE -> {
                if (appearance?.contains("signature") == true) QuestionType.SIGNATURE
                else QuestionType.IMAGE
            }
            Constants.CONTROL_AUDIO_CAPTURE -> QuestionType.AUDIO
            Constants.CONTROL_VIDEO_CAPTURE -> QuestionType.VIDEO
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
    val selectedChoices: Set<String> = emptySet(),
    val audioUri: String? = null,
    val imageUri: String? = null
)

enum class QuestionType {
    TEXT, INTEGER, DECIMAL, DATE, TIME, DATETIME,
    SELECT_ONE, SELECT_MULTI,
    LABEL, TRIGGER, GROUP, REPEAT,
    GEOPOINT, IMAGE, AUDIO, VIDEO, SIGNATURE, BARCODE, UPLOAD
}

enum class PostFormDestination {
    RETURN_TO_MENU,
    RETURN_TO_CASE_LIST,
    CHAINED_FORM
}
