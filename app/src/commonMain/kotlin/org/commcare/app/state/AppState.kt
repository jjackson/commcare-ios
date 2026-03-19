package org.commcare.app.state

import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.util.CommCarePlatform

/**
 * Top-level app state machine.
 * NoAppsInstalled / NeedsLogin → LoggingIn → Installing → Ready
 */
sealed class AppState {
    data object NoAppsInstalled : AppState()
    data class NeedsLogin(
        val seatedApp: ApplicationRecord,
        val allApps: List<ApplicationRecord>
    ) : AppState()
    data object LoggedOut : AppState()
    data class LoggingIn(val serverUrl: String, val username: String) : AppState()
    data class LoginError(val message: String) : AppState()
    data class Installing(val progress: Float, val statusMessage: String) : AppState()
    data class InstallError(val message: String) : AppState()
    data class Ready(
        val platform: CommCarePlatform,
        val sandbox: SqlDelightUserSandbox,
        val app: ApplicationRecord,
        val serverUrl: String,
        val domain: String,
        val authHeader: String
    ) : AppState()
    data class AppCorrupted(val app: ApplicationRecord, val message: String) : AppState()
}
