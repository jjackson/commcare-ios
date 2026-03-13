package org.commcare.app.state

import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.util.CommCarePlatform

/**
 * Top-level app state machine.
 * LoggedOut → LoggingIn → Installing → Ready → InSession
 */
sealed class AppState {
    data object LoggedOut : AppState()
    data class LoggingIn(val serverUrl: String, val username: String) : AppState()
    data class LoginError(val message: String) : AppState()
    data class Installing(val progress: Float, val statusMessage: String) : AppState()
    data class InstallError(val message: String) : AppState()
    data class Ready(
        val platform: CommCarePlatform,
        val sandbox: SqlDelightUserSandbox,
        val serverUrl: String,
        val domain: String,
        val authHeader: String
    ) : AppState()
}
