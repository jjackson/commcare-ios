package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.storage.CommCareDatabase

/**
 * Manages form records — drafts (incomplete), completed forms for review,
 * and transitions to submitted status.
 */
class FormRecordViewModel(
    private val db: CommCareDatabase? = null
) {
    var incompleteRecords by mutableStateOf<List<FormRecord>>(emptyList())
        private set
    var completeRecords by mutableStateOf<List<FormRecord>>(emptyList())
        private set

    private var nextId = 1

    fun loadRecords() {
        if (db == null) return
        try {
            incompleteRecords = db.commCareQueries.selectIncompleteFormRecords().executeAsList().map {
                FormRecord(
                    formId = it.form_id,
                    xmlns = it.xmlns,
                    formName = it.form_name,
                    status = FormRecordStatus.valueOf(it.status.uppercase()),
                    serializedInstance = it.serialized_instance,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }
            completeRecords = db.commCareQueries.selectCompleteFormRecords().executeAsList().map {
                FormRecord(
                    formId = it.form_id,
                    xmlns = it.xmlns,
                    formName = it.form_name,
                    status = FormRecordStatus.COMPLETE,
                    serializedInstance = it.serialized_instance,
                    createdAt = it.created_at,
                    updatedAt = it.updated_at
                )
            }
        } catch (_: Exception) {
            // DB not yet initialized
        }
    }

    /**
     * Save current form state as an incomplete draft.
     * Returns the form ID for later resumption.
     */
    fun saveDraft(formXml: String, xmlns: String, formName: String, existingId: String? = null): String {
        val formId = existingId ?: "draft-${nextId++}"
        val now = currentTimestamp()
        val record = FormRecord(
            formId = formId,
            xmlns = xmlns,
            formName = formName,
            status = FormRecordStatus.INCOMPLETE,
            serializedInstance = formXml,
            createdAt = now,
            updatedAt = now
        )

        db?.commCareQueries?.insertFormRecord(
            form_id = formId,
            xmlns = xmlns,
            form_name = formName,
            status = "incomplete",
            serialized_instance = formXml,
            created_at = now,
            updated_at = now
        )

        incompleteRecords = incompleteRecords.filter { it.formId != formId } + record
        return formId
    }

    /**
     * Mark a form as complete (for review) with its final XML.
     */
    fun markComplete(formId: String, formXml: String) {
        val now = currentTimestamp()
        db?.commCareQueries?.insertFormRecord(
            form_id = formId,
            xmlns = incompleteRecords.find { it.formId == formId }?.xmlns ?: "",
            form_name = incompleteRecords.find { it.formId == formId }?.formName ?: "",
            status = "complete",
            serialized_instance = formXml,
            created_at = incompleteRecords.find { it.formId == formId }?.createdAt ?: now,
            updated_at = now
        )
        incompleteRecords = incompleteRecords.filter { it.formId != formId }
        loadRecords()
    }

    /**
     * Mark a completed form as submitted (after queue submission).
     */
    fun markSubmitted(formId: String) {
        val now = currentTimestamp()
        db?.commCareQueries?.updateFormRecordStatus("submitted", now, formId)
        completeRecords = completeRecords.filter { it.formId != formId }
    }

    /**
     * Get a saved form record by ID for resumption or review.
     */
    fun getRecord(formId: String): FormRecord? {
        return incompleteRecords.find { it.formId == formId }
            ?: completeRecords.find { it.formId == formId }
    }

    fun deleteRecord(formId: String) {
        db?.commCareQueries?.deleteFormRecord(formId)
        incompleteRecords = incompleteRecords.filter { it.formId != formId }
        completeRecords = completeRecords.filter { it.formId != formId }
    }

    private fun currentTimestamp(): String = "now"
}

data class FormRecord(
    val formId: String,
    val xmlns: String,
    val formName: String,
    val status: FormRecordStatus,
    val serializedInstance: String,
    val createdAt: String,
    val updatedAt: String
)

enum class FormRecordStatus {
    INCOMPLETE, COMPLETE, SUBMITTED
}
