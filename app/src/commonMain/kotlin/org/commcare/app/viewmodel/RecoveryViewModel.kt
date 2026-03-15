package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Recovery mode — manage unsent forms, view logs, clear data.
 */
class RecoveryViewModel {
    var unsentForms by mutableStateOf<List<UnsentForm>>(emptyList())
        private set
    var logs by mutableStateOf<List<LogEntry>>(emptyList())
        private set
    var actionResult by mutableStateOf<String?>(null)
        private set

    /**
     * Load unsent forms from the form queue.
     */
    fun loadUnsentForms(formRecords: List<Pair<String, String>>) {
        unsentForms = formRecords.mapIndexed { index, (title, timestamp) ->
            UnsentForm(id = index.toString(), title = title, timestamp = timestamp)
        }
    }

    /**
     * Force-submit a specific unsent form.
     */
    fun forceSubmitForm(formId: String) {
        actionResult = "Attempting to submit form $formId..."
        // In production: call FormQueueViewModel.submitForm(formId)
    }

    /**
     * Delete an unsent form.
     */
    fun deleteForm(formId: String) {
        unsentForms = unsentForms.filter { it.id != formId }
        actionResult = "Form deleted"
    }

    /**
     * Load application logs.
     */
    fun loadLogs(maxEntries: Int = 100) {
        // In production: read from platform log storage
        logs = emptyList()
        actionResult = "Logs loaded ($maxEntries max)"
    }

    /**
     * Clear all user data and force fresh sync on next login.
     */
    fun clearUserData(): Boolean {
        // In production: wipe SQLDelight database
        actionResult = "User data cleared. Login again to restore."
        return true
    }

    /**
     * Export form data as XML for manual recovery.
     */
    fun exportFormXml(formId: String): String? {
        val form = unsentForms.find { it.id == formId }
        if (form == null) {
            actionResult = "Form not found"
            return null
        }
        actionResult = "Form XML exported"
        // In production: retrieve XML from form queue storage
        return "<form id=\"${form.id}\"><title>${form.title}</title></form>"
    }

    fun clearActionResult() {
        actionResult = null
    }
}

data class UnsentForm(
    val id: String,
    val title: String,
    val timestamp: String
)

data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String
)
