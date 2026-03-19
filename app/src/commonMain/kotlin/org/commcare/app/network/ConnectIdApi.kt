package org.commcare.app.network

import org.commcare.app.model.ConnectIdTokens
import org.commcare.app.model.RegistrationSession
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import org.commcare.core.interfaces.createHttpClient

/**
 * HTTP client for the ConnectID service at connectid.dimagi.com.
 * Handles registration, OTP verification, profile completion, and OAuth token exchange.
 *
 * Uses the existing KMP PlatformHttpClient. JSON parsing uses simple string extraction
 * (no kotlinx.serialization dependency), matching the pattern in LoginViewModel.
 */
class ConnectIdApi(
    private val httpClient: PlatformHttpClient = createHttpClient()
) {
    companion object {
        const val BASE_URL = "https://connectid.dimagi.com"
        const val OAUTH_CLIENT_ID = "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa"
    }

    /**
     * Start or resume a registration/configuration flow.
     * POST /users/configure
     */
    fun startConfiguration(phone: String, sessionToken: String? = null): Result<RegistrationSession> {
        return try {
            val bodyParts = mutableListOf("phone_number=$phone")
            if (sessionToken != null) {
                bodyParts.add("session_token=$sessionToken")
            }
            val body = bodyParts.joinToString("&")

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/configure",
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = body.encodeToByteArray(),
                    contentType = "application/x-www-form-urlencoded"
                )
            )

            if (response.code !in 200..299) {
                return Result.failure(ConnectIdException("Configuration failed (${response.code})"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectIdException("Empty response"))

            Result.success(
                RegistrationSession(
                    sessionToken = extractJsonString(json, "session_token") ?: "",
                    smsMethod = extractJsonString(json, "sms_method") ?: "firebase",
                    requiredLock = extractJsonString(json, "required_lock") ?: "pin"
                )
            )
        } catch (e: Exception) {
            Result.failure(ConnectIdException("Configuration request failed: ${e.message}", e))
        }
    }

    /**
     * Request an OTP be sent via SMS.
     * POST /users/send_otp
     */
    fun sendOtp(sessionToken: String): Result<Unit> {
        return executeSimplePost(
            "$BASE_URL/users/send_otp",
            "session_token=$sessionToken"
        )
    }

    /**
     * Confirm an OTP code.
     * POST /users/confirm_otp
     */
    fun confirmOtp(sessionToken: String, otp: String): Result<Unit> {
        return executeSimplePost(
            "$BASE_URL/users/confirm_otp",
            "session_token=$sessionToken&otp=$otp"
        )
    }

    /**
     * Check if a display name is available.
     * POST /users/check_name
     */
    fun checkName(sessionToken: String, name: String): Result<Unit> {
        return executeSimplePost(
            "$BASE_URL/users/check_name",
            "session_token=$sessionToken&name=$name"
        )
    }

    /**
     * Confirm a backup/recovery code.
     * POST /users/confirm_backup_code
     */
    fun confirmBackupCode(sessionToken: String, code: String): Result<Unit> {
        return executeSimplePost(
            "$BASE_URL/users/confirm_backup_code",
            "session_token=$sessionToken&backup_code=$code"
        )
    }

    /**
     * Complete the user profile (final registration step).
     * POST /users/complete_profile
     */
    fun completeProfile(
        sessionToken: String,
        name: String,
        recoveryPin: String,
        photoBase64: String
    ): Result<CompleteProfileResponse> {
        return try {
            val body = "session_token=$sessionToken&name=$name&recovery_pin=$recoveryPin&photo=$photoBase64"

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/complete_profile",
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = body.encodeToByteArray(),
                    contentType = "application/x-www-form-urlencoded"
                )
            )

            if (response.code !in 200..299) {
                return Result.failure(ConnectIdException("Profile completion failed (${response.code})"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectIdException("Empty response"))

            Result.success(
                CompleteProfileResponse(
                    username = extractJsonString(json, "username") ?: "",
                    password = extractJsonString(json, "password") ?: "",
                    dbKey = extractJsonString(json, "db_key") ?: ""
                )
            )
        } catch (e: Exception) {
            Result.failure(ConnectIdException("Profile completion request failed: ${e.message}", e))
        }
    }

    /**
     * Exchange credentials for an OAuth2 access token.
     * POST /o/token/
     */
    fun getOAuthToken(username: String, password: String): Result<ConnectIdTokens> {
        return try {
            val body = "grant_type=password&client_id=$OAUTH_CLIENT_ID" +
                "&username=$username&password=$password"

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/o/token/",
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = body.encodeToByteArray(),
                    contentType = "application/x-www-form-urlencoded"
                )
            )

            if (response.code !in 200..299) {
                return Result.failure(ConnectIdException("OAuth token exchange failed (${response.code})"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectIdException("Empty response"))

            val accessToken = extractJsonString(json, "access_token")
                ?: return Result.failure(ConnectIdException("No access_token in response"))

            val expiresIn = extractJsonNumber(json, "expires_in") ?: 3600L

            Result.success(ConnectIdTokens(accessToken = accessToken, expiresIn = expiresIn))
        } catch (e: Exception) {
            Result.failure(ConnectIdException("OAuth token request failed: ${e.message}", e))
        }
    }

    /**
     * Fetch the database encryption key for this user.
     * GET /users/fetch_db_key
     */
    fun fetchDbKey(accessToken: String): Result<String> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/fetch_db_key",
                    method = "GET",
                    headers = mapOf("Authorization" to "Bearer $accessToken")
                )
            )

            if (response.code !in 200..299) {
                return Result.failure(ConnectIdException("Fetch DB key failed (${response.code})"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectIdException("Empty response"))

            val dbKey = extractJsonString(json, "db_key")
                ?: return Result.failure(ConnectIdException("No db_key in response"))

            Result.success(dbKey)
        } catch (e: Exception) {
            Result.failure(ConnectIdException("Fetch DB key request failed: ${e.message}", e))
        }
    }

    // --- Internal helpers ---

    /**
     * Execute a simple POST that expects a 2xx with no meaningful body.
     */
    private fun executeSimplePost(url: String, body: String): Result<Unit> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = url,
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/x-www-form-urlencoded"),
                    body = body.encodeToByteArray(),
                    contentType = "application/x-www-form-urlencoded"
                )
            )

            if (response.code !in 200..299) {
                val errorMsg = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectIdException(errorMsg))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ConnectIdException("Request failed: ${e.message}", e))
        }
    }

    /**
     * Extract a string value from a JSON response using simple string operations.
     * Matches pattern: "key": "value" or "key":"value"
     */
    private fun extractJsonString(json: String, key: String): String? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        // Find the colon after the key
        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        // Find the opening quote of the value
        val openQuote = json.indexOf('"', colonIdx + 1)
        if (openQuote == -1) return null

        // Find the closing quote (handle escaped quotes)
        var closeQuote = openQuote + 1
        while (closeQuote < json.length) {
            if (json[closeQuote] == '"' && json[closeQuote - 1] != '\\') break
            closeQuote++
        }
        if (closeQuote >= json.length) return null

        return json.substring(openQuote + 1, closeQuote)
    }

    /**
     * Extract a numeric value from a JSON response.
     * Matches pattern: "key": 1234 or "key":1234
     */
    private fun extractJsonNumber(json: String, key: String): Long? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        // Skip whitespace after colon
        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return null

        // Read digits (and optional leading minus)
        val sb = StringBuilder()
        if (json[start] == '-') {
            sb.append('-')
            start++
        }
        while (start < json.length && json[start].isDigit()) {
            sb.append(json[start])
            start++
        }

        return sb.toString().toLongOrNull()
    }
}

data class CompleteProfileResponse(
    val username: String,
    val password: String,
    val dbKey: String
)

class ConnectIdException(message: String, cause: Throwable? = null) : Exception(message, cause)
