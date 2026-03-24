package org.commcare.app.integration

/**
 * Configuration for Connect integration tests (marketplace + identity).
 * Reads credentials from environment variables.
 *
 * To run integration tests locally:
 *   export CONNECT_ACCESS_TOKEN="<token>"
 *   ./gradlew :app:jvmTest --tests "*ConnectApiRequestTest*"
 *
 * Or use username/password (slower — requires OAuth token exchange):
 *   export CONNECT_USERNAME="user"
 *   export CONNECT_PASSWORD="pass"
 */
object ConnectTestConfig {
    val connectIdUrl: String get() = System.getenv("CONNECT_ID_URL") ?: "https://connectid.dimagi.com"
    val connectMarketplaceUrl: String get() = System.getenv("CONNECT_MARKETPLACE_URL") ?: "https://connect.dimagi.com"
    val connectUsername: String get() = System.getenv("CONNECT_USERNAME") ?: ""
    val connectPassword: String get() = System.getenv("CONNECT_PASSWORD") ?: ""
    val connectAccessToken: String get() = System.getenv("CONNECT_ACCESS_TOKEN") ?: ""

    val isConfigured: Boolean get() = connectAccessToken.isNotBlank() ||
        (connectUsername.isNotBlank() && connectPassword.isNotBlank())

    fun bearerHeader(token: String): String = "Bearer $token"
}
