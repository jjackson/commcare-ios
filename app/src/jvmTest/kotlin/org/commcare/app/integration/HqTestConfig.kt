package org.commcare.app.integration

/**
 * Configuration for CommCare HQ integration tests.
 * Reads credentials from environment variables.
 *
 * To run integration tests locally:
 *   export COMMCARE_HQ_URL="https://www.commcarehq.org"
 *   export COMMCARE_USERNAME="user@domain"
 *   export COMMCARE_PASSWORD="password"
 *   export COMMCARE_APP_ID="abc123"
 *   export COMMCARE_DOMAIN="demo"
 *   ./gradlew :app:jvmTest --tests "*HqIntegrationTest*"
 */
object HqTestConfig {
    val hqUrl: String get() = System.getenv("COMMCARE_HQ_URL") ?: "https://www.commcarehq.org"
    val username: String get() = System.getenv("COMMCARE_USERNAME") ?: ""
    val password: String get() = System.getenv("COMMCARE_PASSWORD") ?: ""
    val appId: String get() = System.getenv("COMMCARE_APP_ID") ?: ""
    val domain: String get() = System.getenv("COMMCARE_DOMAIN") ?: ""

    val isConfigured: Boolean get() = username.isNotBlank() && password.isNotBlank() && domain.isNotBlank()

    fun restoreUrl(): String = "${hqUrl.trimEnd('/')}/a/$domain/phone/restore/"
    fun submitUrl(): String = "${hqUrl.trimEnd('/')}/a/$domain/receiver/"

    fun basicAuthHeader(): String {
        val credentials = "$username:$password"
        val encoded = java.util.Base64.getEncoder().encodeToString(credentials.toByteArray())
        return "Basic $encoded"
    }
}
