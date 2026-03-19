package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.AppInstaller
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox

/**
 * Manages demo/practice mode — isolated sandbox for training without server submissions.
 * Demo data is kept separate from real user data.
 */
class DemoModeManager(
    private val db: CommCareDatabase
) {
    var isDemoMode by mutableStateOf(false)
        private set
    var demoState by mutableStateOf<DemoState>(DemoState.Idle)
        private set

    private var demoSandbox: SqlDelightUserSandbox? = null

    /**
     * Enter demo mode with an isolated sandbox.
     * @param profileUrl Optional profile URL for app installation
     * @return AppState.Ready if successful, null on failure
     */
    fun enterDemoMode(profileUrl: String = ""): AppState? {
        return try {
            demoState = DemoState.Loading
            val sandbox = SqlDelightUserSandbox(db)
            demoSandbox = sandbox

            val installer = AppInstaller(sandbox)
            val platform = if (profileUrl.isNotBlank()) {
                installer.install(profileUrl) { _, _ -> }
            } else {
                installer.createMinimalPlatform()
            }

            isDemoMode = true
            demoState = DemoState.Active

            val demoApp = ApplicationRecord(
                id = "demo",
                profileUrl = "",
                displayName = "Demo Mode",
                domain = "demo",
                majorVersion = 2,
                minorVersion = 53,
                installDate = 0L
            )
            AppState.Ready(
                platform = platform,
                sandbox = sandbox,
                app = demoApp,
                serverUrl = "demo://localhost",
                domain = "demo",
                authHeader = "Demo demo_user"
            )
        } catch (e: Exception) {
            demoState = DemoState.Error("Failed to start demo mode: ${e.message}")
            null
        }
    }

    /**
     * Reset demo data — clears the demo sandbox and re-initializes.
     */
    fun resetDemoData(): AppState? {
        if (!isDemoMode) return null
        return try {
            demoState = DemoState.Loading
            // Clear existing demo sandbox
            demoSandbox = null

            // Re-enter demo mode
            val sandbox = SqlDelightUserSandbox(db)
            demoSandbox = sandbox

            val installer = AppInstaller(sandbox)
            val platform = installer.createMinimalPlatform()

            demoState = DemoState.Active
            val demoApp = ApplicationRecord(
                id = "demo",
                profileUrl = "",
                displayName = "Demo Mode",
                domain = "demo",
                majorVersion = 2,
                minorVersion = 53,
                installDate = 0L
            )
            AppState.Ready(
                platform = platform,
                sandbox = sandbox,
                app = demoApp,
                serverUrl = "demo://localhost",
                domain = "demo",
                authHeader = "Demo demo_user"
            )
        } catch (e: Exception) {
            demoState = DemoState.Error("Failed to reset demo data: ${e.message}")
            null
        }
    }

    /**
     * Exit demo mode and return to login.
     */
    fun exitDemoMode() {
        isDemoMode = false
        demoSandbox = null
        demoState = DemoState.Idle
    }

    /**
     * Check if form submissions should be blocked (in demo mode, they should not reach the server).
     */
    fun shouldBlockSubmission(): Boolean = isDemoMode
}

sealed class DemoState {
    data object Idle : DemoState()
    data object Loading : DemoState()
    data object Active : DemoState()
    data class Error(val message: String) : DemoState()
}
