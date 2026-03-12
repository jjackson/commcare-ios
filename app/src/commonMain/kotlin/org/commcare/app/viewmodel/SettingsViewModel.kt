package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages app settings — server URL, sync frequency, developer options.
 */
class SettingsViewModel {
    var serverUrl by mutableStateOf("https://www.commcarehq.org")
    var syncFrequencyMinutes by mutableStateOf(15)
    var developerMode by mutableStateOf(false)
    var showDebugInfo by mutableStateOf(false)
    var autoSync by mutableStateOf(true)
    var savedMessage by mutableStateOf<String?>(null)
        private set

    fun save() {
        // In full implementation: persist to platform key-value storage
        savedMessage = "Settings saved"
    }

    fun clearSavedMessage() {
        savedMessage = null
    }

    fun resetToDefaults() {
        serverUrl = "https://www.commcarehq.org"
        syncFrequencyMinutes = 15
        developerMode = false
        showDebugInfo = false
        autoSync = true
        savedMessage = "Settings reset to defaults"
    }
}
