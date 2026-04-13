package org.commcare.app.viewmodel

import org.commcare.app.network.ConnectIdApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.platform.currentEpochSeconds
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.app.network.formUrlEncode
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.createHttpClient

/**
 * Manages the OAuth2 token lifecycle for ConnectID and CommCare HQ SSO integration.
 *
 * Responsibilities:
 * - Cache and refresh ConnectID access tokens (ROPC grant via [ConnectIdApi])
 * - Exchange ConnectID tokens for HQ SSO tokens (ROPC grant to HQ OAuth endpoint)
 * - Link a CommCare HQ account to a ConnectID identity (Basic auth + ConnectID token)
 */
class ConnectIdTokenManager(
    private val api: ConnectIdApi,
    private val repository: ConnectIdRepository,
    private val keychainStore: PlatformKeychainStore,
    private val db: org.commcare.app.storage.CommCareDatabase? = null
) {
    companion object {
        /** OAuth2 client_id registered on CommCare HQ for ConnectID SSO. */
        const val HQ_OAUTH_CLIENT_ID = "4eHlQad1oasGZF0lPiycZIjyL0SY1zx7ZblA6SCV"

        private const val KEY_CONNECT_TOKEN = "connect_access_token"
        private const val KEY_CONNECT_TOKEN_EXPIRY = "connect_token_expiry"
        private const val KEY_CONNECT_USERNAME = "connect_username"
        private const val KEY_CONNECT_PASSWORD = "connect_password"

        /** Seconds before expiry at which we proactively refresh the token. */
        private const val EXPIRY_BUFFER_SECONDS = 60L
    }

    // -------------------------------------------------------------------------
    // ConnectID token
    // -------------------------------------------------------------------------

    /**
     * Return a valid ConnectID access token.
     *
     * Uses the cached token if it has not expired yet (with a 60-second safety margin).
     * Falls back to [refreshConnectIdToken] using stored ROPC credentials.
     *
     * @return The access token string, or null if the user is not registered or refresh fails.
     */
    fun getConnectIdToken(): String? {
        val cached = retrieveCredential(KEY_CONNECT_TOKEN)
        val expiry = retrieveCredential(KEY_CONNECT_TOKEN_EXPIRY)?.toLongOrNull() ?: 0L
        if (cached != null && currentEpochSeconds() < expiry) {
            return cached
        }
        // The ROPC password from recovery is single-use — the server invalidates
        // it after the first token exchange. Trying to refresh with stored
        // credentials will always fail with "invalid_grant". If the cached token
        // has expired, return null (user needs to re-authenticate).
        // Still try the refresh in case the password IS valid (e.g., from
        // registration which may issue a persistent password).
        return refreshConnectIdToken()
    }

    /**
     * Refresh the ConnectID token using stored ROPC credentials.
     *
     * Stores the new token and its computed expiry time in the keychain on success.
     *
     * @return The new access token string, or null if credentials are missing or the
     *         token exchange fails.
     */
    /** Last error from token refresh — surfaced in UI for debugging. */
    var lastTokenError: String? = null
        private set

    /** Read a value from keychain, falling back to the DB preferences table. */
    private fun retrieveCredential(key: String): String? {
        return keychainStore.retrieve(key)
            ?: try { db?.commCareQueries?.getPreference(key)?.executeAsOneOrNull() } catch (_: Exception) { null }
    }

    /** Store a value in BOTH keychain and DB (belt-and-suspenders). */
    private fun storeCredential(key: String, value: String) {
        keychainStore.store(key, value)
        try { db?.commCareQueries?.setPreference(key, value) } catch (_: Exception) { }
    }

    fun refreshConnectIdToken(): String? {
        val username = retrieveCredential(KEY_CONNECT_USERNAME)
        if (username == null) {
            lastTokenError = "No stored connect_username in keychain or DB"
            return null
        }
        val password = retrieveCredential(KEY_CONNECT_PASSWORD)
        if (password == null) {
            lastTokenError = "No stored connect_password in keychain or DB"
            return null
        }
        val result = api.getOAuthToken(username, password)
        return result.fold(
            onSuccess = { tokens ->
                lastTokenError = null
                val expiryAt = currentEpochSeconds() + tokens.expiresIn - EXPIRY_BUFFER_SECONDS
                storeCredential(KEY_CONNECT_TOKEN, tokens.accessToken)
                storeCredential(KEY_CONNECT_TOKEN_EXPIRY, expiryAt.toString())
                tokens.accessToken
            },
            onFailure = { e ->
                lastTokenError = "Token refresh failed: ${e.message}"
                null
            }
        )
    }

    // -------------------------------------------------------------------------
    // HQ SSO
    // -------------------------------------------------------------------------

    /**
     * Obtain an HQ SSO access token for [domain] on the given [hqUrl].
     *
     * Performs an ROPC grant to `{hqUrl}/oauth/token/` where the password field carries
     * the ConnectID access token, allowing HQ to verify the user's identity via ConnectID.
     *
     * @param hqUrl  Base URL of the CommCare HQ instance (e.g. "https://www.commcarehq.org").
     * @param domain CommCare domain / project space.
     * @param hqUsername The user's CommCare HQ username (without @domain suffix).
     * @return HQ access token string, or null on any failure.
     */
    fun getHqSsoToken(hqUrl: String, domain: String, hqUsername: String): String? {
        val connectToken = getConnectIdToken() ?: return null
        val httpClient = createHttpClient()

        val tokenUrl = "${hqUrl.trimEnd('/')}/oauth/token/"
        val body = "client_id=${formUrlEncode(HQ_OAUTH_CLIENT_ID)}" +
            "&grant_type=password" +
            "&scope=mobile_access+sync" +
            "&username=${formUrlEncode("${hqUsername}@${domain}")}" +
            "&password=${formUrlEncode(connectToken)}"

        val request = HttpRequest(
            url = tokenUrl,
            method = "POST",
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body = body.encodeToByteArray(),
            contentType = "application/x-www-form-urlencoded"
        )

        val response = httpClient.execute(request)
        if (response.code !in 200..299) return null

        val responseBody = response.body?.decodeToString() ?: return null
        return extractJsonValue(responseBody, "access_token")
    }

    // -------------------------------------------------------------------------
    // HQ account linking
    // -------------------------------------------------------------------------

    /**
     * Link a CommCare HQ account to this ConnectID user.
     *
     * Sends the ConnectID access token to the HQ linking endpoint using Basic auth
     * so HQ can associate the two identities. On success, the link is persisted via
     * [ConnectIdRepository].
     *
     * @param hqUrl      Base URL of the CommCare HQ instance.
     * @param hqUsername The HQ username to link.
     * @param hqPassword The HQ password (used for one-time Basic auth during linking).
     * @param domain     CommCare domain / project space.
     * @return true if the link was created successfully, false otherwise.
     */
    fun linkHqAccount(
        hqUrl: String,
        hqUsername: String,
        hqPassword: String,
        domain: String
    ): Boolean {
        val connectToken = getConnectIdToken() ?: return false
        val httpClient = createHttpClient()

        val linkUrl = "${hqUrl.trimEnd('/')}/settings/users/commcare/link_connectid_user/"
        val credentials = base64Encode("$hqUsername:$hqPassword".encodeToByteArray())
        val jsonBody = """{"token":"$connectToken"}"""

        val request = HttpRequest(
            url = linkUrl,
            method = "POST",
            headers = mapOf(
                "Authorization" to "Basic $credentials",
                "Content-Type" to "application/json"
            ),
            body = jsonBody.encodeToByteArray(),
            contentType = "application/json"
        )

        val response = httpClient.execute(request)
        if (response.code in 200..299) {
            val connectUsername = keychainStore.retrieve(KEY_CONNECT_USERNAME) ?: ""
            repository.saveHqLink(hqUsername, domain, connectUsername)
            return true
        }
        return false
    }

    // -------------------------------------------------------------------------
    // Convenience queries
    // -------------------------------------------------------------------------

    /** Returns true if the user has completed ConnectID registration. */
    fun isRegistered(): Boolean = repository.isRegistered()

    /** Returns the stored ConnectID username, or null if not registered. */
    fun getStoredUsername(): String? = keychainStore.retrieve(KEY_CONNECT_USERNAME)

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extract a string value from a minimal JSON response.
     * Matches patterns like: `"key": "value"` or `"key":"value"`.
     */
    private fun extractJsonValue(json: String, key: String): String? {
        val pattern = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(json)?.groupValues?.getOrNull(1)
    }

    /**
     * RFC 4648 Base64 encoder (no external dependencies).
     */
    private fun base64Encode(data: ByteArray): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(alphabet[(b0 shr 2) and 0x3F])
            sb.append(alphabet[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            sb.append(if (i + 1 < data.size) alphabet[((b1 shl 2) or (b2 shr 6)) and 0x3F] else '=')
            sb.append(if (i + 2 < data.size) alphabet[b2 and 0x3F] else '=')
            i += 3
        }
        return sb.toString()
    }
}
