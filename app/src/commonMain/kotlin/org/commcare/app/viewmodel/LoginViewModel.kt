package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.state.AppState
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.createHttpClient

/**
 * Manages login state and HQ authentication.
 */
class LoginViewModel {
    var serverUrl by mutableStateOf("https://www.commcarehq.org")
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var appState by mutableStateOf<AppState>(AppState.LoggedOut)
        private set

    private val httpClient = createHttpClient()

    fun login() {
        if (username.isBlank() || password.isBlank()) {
            appState = AppState.LoginError("Username and password are required")
            return
        }

        appState = AppState.LoggingIn(serverUrl, username)

        try {
            // CommCare HQ login endpoint
            val loginUrl = "${serverUrl.trimEnd('/')}/a/${getDomain()}/phone/restore/"
            val credentials = encodeBasicAuth(username, password)

            val response = httpClient.execute(
                HttpRequest(
                    url = loginUrl,
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Basic $credentials",
                        "X-CommCareHQ-LastSyncToken" to ""
                    )
                )
            )

            when {
                response.code in 200..299 -> {
                    appState = AppState.Installing(0f, "Login successful, preparing install...")
                }
                response.code == 401 -> {
                    appState = AppState.LoginError("Invalid username or password")
                }
                response.code == 404 -> {
                    appState = AppState.LoginError("Domain not found. Check your server URL.")
                }
                else -> {
                    appState = AppState.LoginError("Server error (${response.code})")
                }
            }
        } catch (e: Exception) {
            appState = AppState.LoginError("Connection failed: ${e.message}")
        }
    }

    fun resetError() {
        appState = AppState.LoggedOut
    }

    private fun getDomain(): String {
        // Extract domain from username (user@domain format) or use default
        return if (username.contains("@")) {
            username.substringAfter("@")
        } else {
            "demo" // fallback
        }
    }

    private fun encodeBasicAuth(user: String, pass: String): String {
        val raw = "$user:$pass"
        return base64Encode(raw.encodeToByteArray())
    }

    private fun base64Encode(data: ByteArray): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
        val sb = StringBuilder()
        var i = 0
        while (i < data.size) {
            val b0 = data[i].toInt() and 0xFF
            val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
            sb.append(chars[(b0 shr 2) and 0x3F])
            sb.append(chars[((b0 shl 4) or (b1 shr 4)) and 0x3F])
            if (i + 1 < data.size) {
                sb.append(chars[((b1 shl 2) or (b2 shr 6)) and 0x3F])
            } else {
                sb.append('=')
            }
            if (i + 2 < data.size) {
                sb.append(chars[b2 and 0x3F])
            } else {
                sb.append('=')
            }
            i += 3
        }
        return sb.toString()
    }
}
