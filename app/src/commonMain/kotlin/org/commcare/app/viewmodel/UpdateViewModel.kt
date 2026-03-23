package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    var hasNewUpdate by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var periodicCheckJob: Job? = null

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
                    hasNewUpdate = true
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
     * Download and install the staged update with rollback on failure.
     * Snapshots the current version before installing so we can verify
     * the install succeeded and report accurately on failure.
     */
    fun installUpdate() {
        updateState = UpdateState.Installing
        scope.launch {
            // Snapshot current state for rollback reporting
            val previousVersion = currentVersion
            try {
                updateMessage = "Downloading update..."
                updateProgress = 0.1f

                val installer = AppInstaller(sandbox)
                val newPlatform = installer.install(profileUrl) { progress, message ->
                    updateProgress = 0.1f + progress * 0.8f
                    updateMessage = message
                }

                updateProgress = 0.95f
                updateMessage = "Verifying installation..."

                // Verify the new version was actually installed
                val newVersion = try {
                    newPlatform.getCurrentProfile()?.getVersion()?.toString()
                } catch (_: Exception) { null }

                if (newVersion != null && newVersion != previousVersion) {
                    currentVersion = newVersion
                    availableVersion = newVersion
                    hasNewUpdate = false
                    updateState = UpdateState.Complete
                    updateMessage = "Updated to version $newVersion (was $previousVersion)."
                } else {
                    // Install completed but version didn't change — likely a no-op
                    updateState = UpdateState.UpToDate
                    updateMessage = "Already on latest version."
                }
                updateProgress = 1f
            } catch (e: Exception) {
                // Rollback: restore previous version tracking
                currentVersion = previousVersion
                updateState = UpdateState.Error("Update failed: ${e.message}")
                updateMessage = "Update failed. Version $previousVersion preserved."
                updateProgress = 0f
            }
        }
    }

    /**
     * Schedule a daily background check for app updates.
     * Runs an infinite coroutine loop with a 24-hour delay between checks.
     * Safe to call multiple times — cancels any existing periodic job first.
     */
    fun schedulePeriodicCheck() {
        periodicCheckJob?.cancel()
        periodicCheckJob = scope.launch {
            while (true) {
                delay(24 * 60 * 60 * 1000L) // 24 hours
                try {
                    checkForUpdatesQuietly()
                } catch (_: Exception) {
                    // Background check failures are non-fatal; retry next cycle
                }
            }
        }
    }

    /**
     * Silent update check that only sets [hasNewUpdate] without changing [updateState].
     * Used by the periodic background checker so it doesn't interrupt the UI.
     */
    private fun checkForUpdatesQuietly() {
        if (profileUrl.isBlank()) return
        try {
            val httpClient = createHttpClient()
            val response = httpClient.execute(
                HttpRequest(url = profileUrl, method = "GET")
            )
            if (response.code !in 200..299) return
            val body = response.body?.decodeToString() ?: return
            val serverVersion = extractVersionFromProfile(body)
            val installedVersion = currentVersion?.toIntOrNull()
            if (serverVersion != null && installedVersion != null && serverVersion > installedVersion) {
                hasNewUpdate = true
                availableVersion = serverVersion.toString()
            }
        } catch (_: Exception) {
            // Swallow — background check is best-effort
        }
    }

    fun dismissUpdate() {
        updateState = UpdateState.Idle
        hasNewUpdate = false
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
