package org.commcare.app.state

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
    data object Ready : AppState()
}
