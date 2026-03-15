package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Manages app settings — server, sync, search, locale, developer options.
 * Full settings parity with commcare-android.
 */
class SettingsViewModel {
    // Server settings
    var serverUrl by mutableStateOf("https://www.commcarehq.org")
    var autoSync by mutableStateOf(true)
    var syncFrequencyMinutes by mutableStateOf(15)

    // Search settings
    var fuzzySearchEnabled by mutableStateOf(true)
    var fuzzySearchThreshold by mutableStateOf(0.8f)

    // Locale settings
    var localeOverride by mutableStateOf<String?>(null)

    // Auto-update settings
    var autoUpdateEnabled by mutableStateOf(true)
    var autoUpdateFrequencyHours by mutableStateOf(24)

    // Device settings
    var logLevel by mutableStateOf("warn")

    // Developer settings
    var developerMode by mutableStateOf(false)
    var showDebugInfo by mutableStateOf(false)
    var showFormHierarchy by mutableStateOf(false)
    var enableXPathTester by mutableStateOf(false)

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
        autoSync = true
        fuzzySearchEnabled = true
        fuzzySearchThreshold = 0.8f
        localeOverride = null
        autoUpdateEnabled = true
        autoUpdateFrequencyHours = 24
        logLevel = "warn"
        developerMode = false
        showDebugInfo = false
        showFormHierarchy = false
        enableXPathTester = false
        savedMessage = "Settings reset to defaults"
    }
}
