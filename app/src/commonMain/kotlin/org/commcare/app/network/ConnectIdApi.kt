package org.commcare.app.network

import org.commcare.app.model.ConnectIdTokens
import org.commcare.app.network.formUrlEncode
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
        const val APPLICATION_ID = "org.commcare.ios"
    }

    /**
     * Start or resume a registration/configuration flow.
     * POST /users/start_configuration
     *
     * The response includes a `token` field used as a Bearer token for subsequent calls.
     */
    fun startConfiguration(phone: String): Result<RegistrationSession> {
        return try {
            val body = """{"phone_number":"$phone","application_id":"$APPLICATION_ID"}"""

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/start_configuration",
                    method = "POST",
                    headers = mapOf("Content-Type" to "application/json"),
                    body = body.encodeToByteArray(),
                    contentType = "application/json"
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.body?.decodeToString() ?: ""
                return Result.failure(ConnectIdException("Configuration failed (${response.code}): $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectIdException("Empty response"))

            Result.success(
                RegistrationSession(
                    sessionToken = extractJsonString(json, "token") ?: "",
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
     * POST /users/send_session_otp
     * Authorization: Bearer <session_token>
     */
    fun sendOtp(sessionToken: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$BASE_URL/users/send_session_otp",
            sessionToken,
            body = "{}"
        )
    }

    /**
     * Confirm an OTP code.
     * POST /users/confirm_session_otp
     * Authorization: Bearer <session_token>
     * Body: {"otp":"<code>"}
     */
    fun confirmOtp(sessionToken: String, otp: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$BASE_URL/users/confirm_session_otp",
            sessionToken,
            body = """{"otp":"$otp"}"""
        )
    }

    /**
     * Check if a display name is available, and whether the account already exists.
     * POST /users/check_name
     * Authorization: Bearer <session_token>
     *
     * Returns [CheckNameResponse] with `accountExists` (true for recovery flow)
     * and optionally the existing user's photo (base64).
     */
    fun checkName(sessionToken: String, name: String): Result<CheckNameResponse> {
        return try {
            val body = """{"name":"${escapeJson(name)}"}"""

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/check_name",
                    method = "POST",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer $sessionToken"
                    ),
                    body = body.encodeToByteArray(),
                    contentType = "application/json"
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectIdException(errorBody))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectIdException("Empty response"))

            Result.success(
                CheckNameResponse(
                    accountExists = extractJsonBoolean(json, "account_exists") ?: false,
                    existingPhoto = extractJsonString(json, "photo")
                )
            )
        } catch (e: Exception) {
            Result.failure(ConnectIdException("Name check request failed: ${e.message}", e))
        }
    }

    /**
     * Confirm a backup/recovery code (used for both new registration and recovery flows).
     * POST /users/recover/confirm_backup_code
     * Authorization: Bearer <session_token>
     * Body: recovery_pin=<code>
     */
    fun confirmBackupCode(sessionToken: String, code: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$BASE_URL/users/recover/confirm_backup_code",
            sessionToken,
            body = """{"recovery_pin":"$code"}"""
        )
    }

    /**
     * Confirm a backup code for account recovery (existing user, new device).
     * POST /users/recover/confirm_backup_code
     * Authorization: Bearer <session_token>
     * Body: recovery_pin=<code>
     *
     * In recovery mode the server returns the user's credentials (username, password, db_key)
     * so the device can re-establish access without creating a new account.
     */
    fun confirmBackupCodeRecovery(sessionToken: String, code: String): Result<CompleteProfileResponse> {
        return try {
            val body = """{"recovery_pin":"$code"}"""

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/recover/confirm_backup_code",
                    method = "POST",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer $sessionToken"
                    ),
                    body = body.encodeToByteArray(),
                    contentType = "application/json"
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectIdException("Recovery backup code failed: $errorBody"))
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
            Result.failure(ConnectIdException("Recovery backup code request failed: ${e.message}", e))
        }
    }

    /**
     * Complete the user profile (final registration step).
     * POST /users/complete_profile
     * Authorization: Bearer <session_token>
     */
    fun completeProfile(
        sessionToken: String,
        name: String,
        recoveryPin: String,
        photoBase64: String
    ): Result<CompleteProfileResponse> {
        return try {
            val body = """{"name":"${escapeJson(name)}","recovery_pin":"${escapeJson(recoveryPin)}","photo":"${escapeJson(photoBase64)}"}"""

            val response = httpClient.execute(
                HttpRequest(
                    url = "$BASE_URL/users/complete_profile",
                    method = "POST",
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Authorization" to "Bearer $sessionToken"
                    ),
                    body = body.encodeToByteArray(),
                    contentType = "application/json"
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.body?.decodeToString() ?: ""
                return Result.failure(ConnectIdException("Profile completion failed (${response.code}): $errorBody"))
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
            // scope=openid is required per the PersonalID OAuth spec.
            // Without it, the server may reject ROPC token refresh.
            // See dimagi/commcare-android ApiPersonalId.retrievePersonalIdToken().
            val body = "grant_type=password&client_id=${formUrlEncode(OAUTH_CLIENT_ID)}" +
                "&scope=openid" +
                "&username=${formUrlEncode(username)}&password=${formUrlEncode(password)}"

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
                val errorBody = response.body?.decodeToString() ?: ""
                return Result.failure(ConnectIdException("OAuth token exchange failed (${response.code}): $errorBody"))
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
                val errorBody = response.body?.decodeToString() ?: ""
                return Result.failure(ConnectIdException("Fetch DB key failed (${response.code}): $errorBody"))
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
     * Execute an authenticated POST using a Bearer token in the Authorization header.
     * Expects a 2xx response with no meaningful body.
     */
    private fun executeAuthenticatedPost(url: String, sessionToken: String, body: String?): Result<Unit> {
        return try {
            val headers = mutableMapOf(
                "Authorization" to "Bearer $sessionToken",
                "Content-Type" to "application/json"
            )

            val response = httpClient.execute(
                HttpRequest(
                    url = url,
                    method = "POST",
                    headers = headers,
                    body = body?.encodeToByteArray(),
                    contentType = "application/json"
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectIdException(errorBody))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ConnectIdException("Request failed: ${e.message}", e))
        }
    }

    /**
     * Escape a string for safe inclusion in a JSON value.
     * Handles control characters, quotes, backslashes, and newlines.
     */
    private fun escapeJson(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
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

    /**
     * Extract a boolean value from a JSON response.
     * Matches pattern: "key": true or "key": false
     */
    private fun extractJsonBoolean(json: String, key: String): Boolean? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        // Skip whitespace after colon
        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return null

        return when {
            json.startsWith("true", start) -> true
            json.startsWith("false", start) -> false
            else -> null
        }
    }
}

data class CheckNameResponse(
    val accountExists: Boolean,
    val existingPhoto: String?  // base64 photo of existing user (for recovery confirmation)
)

data class CompleteProfileResponse(
    val username: String,
    val password: String,
    val dbKey: String
)

class ConnectIdException(message: String, cause: Throwable? = null) : Exception(message, cause)
