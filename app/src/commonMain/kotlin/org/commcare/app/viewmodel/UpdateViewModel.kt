package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.engine.AppInstaller
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.createHttpClient
import org.commcare.util.CommCarePlatform

/**
 * Manages app update checking and staged upgrades.
 * Supports version comparison, staged install, and rollback on failure.
 */
class UpdateViewModel(
    private val sandbox: SqlDelightUserSandbox,
    private val platform: CommCarePlatform,
    private val profileUrl: String
) {
    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set
    var currentVersion by mutableStateOf<String?>(null)
        private set
    var availableVersion by mutableStateOf<String?>(null)
        private set
    var updateProgress by mutableStateOf(0f)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

    init {
        loadCurrentVersion()
    }

    private fun loadCurrentVersion() {
        try {
            val profile = platform.getCurrentProfile()
            currentVersion = profile?.getVersion()?.toString()
        } catch (_: Exception) {
            currentVersion = null
        }
    }

    /**
     * Check for available updates by comparing profile versions.
     */
    fun checkForUpdates() {
        if (profileUrl.isBlank()) {
            updateState = UpdateState.Error("No profile URL configured")
            return
        }

        updateState = UpdateState.Checking
        scope.launch {
            try {
                updateMessage = "Checking server for updates..."
                updateProgress = 0.2f

                // Fetch the profile XML from the server
                val httpClient = createHttpClient()
                val response = httpClient.execute(
                    HttpRequest(url = profileUrl, method = "GET")
                )

                if (response.code !in 200..299) {
                    updateState = UpdateState.Error("Server returned ${response.code}")
                    updateMessage = null
                    return@launch
                }

                val body = response.body?.decodeToString() ?: ""
                updateProgress = 0.6f

                // Extract version from profile XML: <profile ... version="N" ...>
                val serverVersion = extractVersionFromProfile(body)
                val installedVersion = currentVersion?.toIntOrNull()

                if (serverVersion != null && installedVersion != null && serverVersion > installedVersion) {
                    availableVersion = serverVersion.toString()
                    updateState = UpdateState.Available
                    updateMessage = "Version $serverVersion available (installed: $installedVersion)"
                } else {
                    updateState = UpdateState.UpToDate
                    updateMessage = "App is up to date"
                }
                updateProgress = 0f
            } catch (e: Exception) {
                updateState = UpdateState.Error("Check failed: ${e.message}")
                updateMessage = null
            }
        }
    }

    private fun extractVersionFromProfile(xml: String): Int? {
        // Match version="N" in the <profile> tag
        val regex = Regex("""<profile[^>]*\bversion\s*=\s*"(\d+)"[^>]*>""")
        return regex.find(xml)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Download and install the staged update.
     */
    fun installUpdate() {
        updateState = UpdateState.Installing
        scope.launch {
            try {
                val installer = AppInstaller(sandbox)
                val newPlatform = installer.install(profileUrl) { progress, message ->
                    updateProgress = progress
                    updateMessage = message
                }

                // Update succeeded
                availableVersion = try {
                    newPlatform.getCurrentProfile()?.getVersion()?.toString()
                } catch (_: Exception) { null }

                updateState = UpdateState.Complete
                updateMessage = "Update installed successfully. Restart to apply."
            } catch (e: Exception) {
                updateState = UpdateState.Error("Update failed: ${e.message}")
                updateMessage = "Update failed. Previous version preserved."
            }
        }
    }

    fun dismissUpdate() {
        updateState = UpdateState.Idle
        updateMessage = null
        updateProgress = 0f
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object Available : UpdateState()
    data object UpToDate : UpdateState()
    data object Installing : UpdateState()
    data object Complete : UpdateState()
    data class Error(val message: String) : UpdateState()
}
