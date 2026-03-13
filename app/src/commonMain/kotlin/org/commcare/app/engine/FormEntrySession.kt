package org.commcare.app.engine

import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.process.CommCareInstanceInitializer
import org.commcare.util.CommCarePlatform
import org.javarosa.core.model.Constants
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.data.DateData
import org.javarosa.core.model.data.DecimalData
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.data.IntegerData
import org.javarosa.core.model.data.SelectMultiData
import org.javarosa.core.model.data.SelectOneData
import org.javarosa.core.model.data.StringData
import org.javarosa.core.model.data.TimeData
import org.javarosa.form.api.FormEntryController
import org.javarosa.form.api.FormEntryModel
import org.javarosa.form.api.FormEntryPrompt

/**
 * Wraps the engine's FormEntryController/Model into a simpler interface for the ViewModel.
 * Handles form initialization, question navigation, and answer validation.
 */
class FormEntrySession(
    val formDef: FormDef,
    private val sandbox: SqlDelightUserSandbox? = null,
    private val platform: CommCarePlatform? = null
) {
    val model: FormEntryModel = FormEntryModel(formDef, FormEntryModel.REPEAT_STRUCTURE_LINEAR)
    val controller: FormEntryController = FormEntryController(model)

    /**
     * Initialize the form with instance data (case references, fixtures, etc.)
     */
    fun initialize() {
        if (sandbox != null && platform != null) {
            val initializer = CommCareInstanceInitializer(sandbox)
            formDef.initialize(true, initializer)
        } else {
            formDef.initialize(true, null)
        }
    }

    fun currentEvent(): Int = model.getEvent()

    fun stepNext(): Int = controller.stepToNextEvent()

    fun stepPrev(): Int = controller.stepToPreviousEvent()

    fun getPrompts(): Array<FormEntryPrompt> {
        return try {
            controller.getQuestionPrompts()
        } catch (_: Exception) {
            emptyArray()
        }
    }

    fun answer(data: IAnswerData?): Int = controller.answerQuestion(data)

    fun answerAtIndex(index: Int, data: IAnswerData?): Int {
        val prompts = getPrompts()
        if (index < prompts.size) {
            val formIndex = prompts[index].getIndex() ?: return FormEntryController.ANSWER_OK
            return controller.answerQuestion(formIndex, data)
        }
        return FormEntryController.ANSWER_OK
    }

    fun isAtEnd(): Boolean = model.getEvent() == FormEntryController.EVENT_END_OF_FORM

    fun isAtBeginning(): Boolean = model.getEvent() == FormEntryController.EVENT_BEGINNING_OF_FORM

    fun getFormTitle(): String {
        return formDef.getTitle() ?: "Form"
    }

    /**
     * Get the total number of questions (approximate, for progress calculation).
     */
    fun getQuestionCount(): Int {
        return formDef.getDeepChildCount()
    }

    companion object {
        /**
         * Create an IAnswerData from a string value and control type.
         */
        fun createAnswerData(value: String, controlType: Int, dataType: Int): IAnswerData? {
            if (value.isBlank()) return null

            return when (controlType) {
                Constants.CONTROL_INPUT -> {
                    when (dataType) {
                        Constants.DATATYPE_INTEGER -> IntegerData(value.toIntOrNull() ?: 0)
                        Constants.DATATYPE_DECIMAL -> DecimalData(value.toDoubleOrNull() ?: 0.0)
                        Constants.DATATYPE_DATE -> parseDateData(value)
                        Constants.DATATYPE_TIME -> parseTimeData(value)
                        else -> StringData(value)
                    }
                }
                else -> StringData(value)
            }
        }

        /**
         * Parse a date string (YYYY-MM-DD) into DateData.
         * Falls back to StringData if parsing fails.
         */
        private fun parseDateData(value: String): IAnswerData {
            return try {
                // Try engine's date parser first (handles multiple formats)
                val date = org.javarosa.core.model.utils.DateUtils.getDateFromString(value)
                if (date != null) DateData(date) else StringData(value)
            } catch (_: Exception) {
                StringData(value)
            }
        }

        /**
         * Parse a time string (HH:MM or HH:MM:SS) into TimeData.
         * Falls back to StringData if parsing fails.
         */
        private fun parseTimeData(value: String): IAnswerData {
            return try {
                val parts = value.split(":")
                if (parts.size >= 2) {
                    val df = org.javarosa.core.model.utils.DateUtils.DateFields()
                    df.year = 1970
                    df.month = 1
                    df.day = 1
                    df.hour = parts[0].toInt()
                    df.minute = parts[1].toInt()
                    df.second = if (parts.size >= 3) parts[2].toInt() else 0
                    TimeData(org.javarosa.core.model.utils.DateUtils.getDate(df))
                } else {
                    StringData(value)
                }
            } catch (_: Exception) {
                StringData(value)
            }
        }
    }
}
