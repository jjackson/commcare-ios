package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.app.engine.AppInstaller
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.createHttpClient
import org.commcare.core.parse.ParseUtils
import org.javarosa.core.io.createByteArrayInputStream

/**
 * Manages login state and HQ authentication.
 * On successful auth, parses the restore response to populate the sandbox.
 */
class LoginViewModel(private val db: CommCareDatabase) {
    var username by mutableStateOf("")
    var password by mutableStateOf("")

    // Internal — not shown in UI. Will come from ApplicationRecord in Wave 2.
    private var serverUrl = "https://www.commcarehq.org"
    private var appId = ""
    private var profileUrl = ""
    private var currentApp: ApplicationRecord? = null

    var appState by mutableStateOf<AppState>(AppState.LoggedOut)
        private set

    /** The sandbox populated after successful login */
    var sandbox: SqlDelightUserSandbox? = null
        private set

    /** Auth header for subsequent requests */
    var authHeader: String? = null
        private set

    private val httpClient = createHttpClient()
    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Configure server and app details from an installed application.
     * Called by the setup/dispatch flow after an app is installed.
     */
    fun configureApp(serverUrl: String, appId: String, app: ApplicationRecord? = null) {
        this.serverUrl = serverUrl
        this.appId = appId
        this.currentApp = app
    }

    fun login() {
        if (username.isBlank() || password.isBlank()) {
            appState = AppState.LoginError("Username and password are required")
            return
        }

        appState = AppState.LoggingIn(serverUrl, username)

        scope.launch {
            try {
                val resolvedDomain = resolveDomain()
                val loginUrl = "${serverUrl.trimEnd('/')}/a/$resolvedDomain/phone/restore/"
                val credentials = encodeBasicAuth(username, password)
                authHeader = "Basic $credentials"

                val response = httpClient.execute(
                    HttpRequest(
                        url = loginUrl,
                        method = "GET",
                        headers = mapOf(
                            "Authorization" to authHeader!!,
                            "X-CommCareHQ-LastSyncToken" to ""
                        )
                    )
                )

                when {
                    response.code in 200..299 -> {
                        appState = AppState.Installing(0.1f, "Parsing restore data...")
                        parseRestoreResponse(response.body, resolvedDomain)
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
    }

    private fun parseRestoreResponse(body: ByteArray?, domain: String) {
        if (body == null || body.isEmpty()) {
            appState = AppState.InstallError("Empty restore response")
            return
        }

        try {
            val newSandbox = SqlDelightUserSandbox(db)

            // Extract sync token from response before parsing
            val bodyString = body.decodeToString()
            val syncToken = extractSyncToken(bodyString)
            if (syncToken != null) {
                newSandbox.syncToken = syncToken
            }

            // Parse restore XML into sandbox (cases, users, fixtures, ledgers)
            val stream = createByteArrayInputStream(body)
            ParseUtils.parseIntoSandbox(stream, newSandbox, failfast = false)

            // Set the logged-in user from the parsed user storage.
            // Without this, CommCareInstanceInitializer throws "No user logged in"
            // when initializing forms that reference the #user instance.
            val userStorage = newSandbox.getUserStorage()
            if (userStorage.getNumRecords() > 0) {
                val user = userStorage.iterate().nextRecord()
                newSandbox.setLoggedInUser(user)
            }

            sandbox = newSandbox

            // Persist sync token for incremental syncs
            val userId = extractUserId(bodyString)
            if (syncToken != null && userId != null) {
                newSandbox.persistSyncToken(userId)
            }

            appState = AppState.Installing(0.5f, "Restore complete. Installing app...")

            // Now try to install the app
            installApp(newSandbox, domain)
        } catch (e: Exception) {
            appState = AppState.InstallError("Failed to parse restore: ${e.message}")
        }
    }

    private fun installApp(sandbox: SqlDelightUserSandbox, domain: String) {
        try {
            val installer = AppInstaller(sandbox)

            // Build profile URL from appId if no explicit profileUrl set
            val resolvedProfileUrl = when {
                profileUrl.isNotBlank() -> profileUrl
                appId.isNotBlank() -> "${serverUrl.trimEnd('/')}/a/$domain/apps/download/$appId/profile.ccpr"
                else -> ""
            }

            if (resolvedProfileUrl.isNotBlank()) {
                // Full installation from profile URL
                val platform = installer.install(resolvedProfileUrl) { progress, message ->
                    appState = AppState.Installing(0.5f + progress * 0.5f, message)
                }
                val profileDisplayName = try {
                    platform.getCurrentProfile().getDisplayName() ?: "CommCare"
                } catch (_: Exception) {
                    "CommCare"
                }
                val app = currentApp ?: ApplicationRecord(
                    id = appId.ifBlank { "unknown" },
                    profileUrl = resolvedProfileUrl,
                    displayName = profileDisplayName,
                    domain = domain,
                    majorVersion = platform.majorVersion,
                    minorVersion = platform.minorVersion,
                    installDate = 0L
                )
                appState = AppState.Ready(platform, sandbox, app, serverUrl, domain, authHeader!!)
            } else {
                // Minimal platform for development (no app profile configured)
                val platform = installer.createMinimalPlatform()
                val app = currentApp ?: ApplicationRecord(
                    id = appId.ifBlank { "unknown" },
                    profileUrl = "",
                    displayName = "CommCare",
                    domain = domain,
                    majorVersion = platform.majorVersion,
                    minorVersion = platform.minorVersion,
                    installDate = 0L
                )
                appState = AppState.Ready(platform, sandbox, app, serverUrl, domain, authHeader!!)
            }
        } catch (e: Exception) {
            val cause = e.cause?.let { " Cause: ${it::class.simpleName}: ${it.message}" } ?: ""
            appState = AppState.InstallError("App installation failed: ${e::class.simpleName}: ${e.message}$cause")
        }
    }

    private fun extractUserId(body: String): String? {
        // Try <user_id> tag first, then <registration> block
        val startTag = "<user_id>"
        val endTag = "</user_id>"
        val start = body.indexOf(startTag)
        if (start == -1) return null
        val end = body.indexOf(endTag, start)
        if (end == -1) return null
        return body.substring(start + startTag.length, end).trim()
    }

    private fun extractSyncToken(body: String): String? {
        val startTag = "<restore_id>"
        val endTag = "</restore_id>"
        val start = body.indexOf(startTag)
        if (start == -1) return null
        val end = body.indexOf(endTag, start)
        if (end == -1) return null
        return body.substring(start + startTag.length, end).trim()
    }

    fun resetError() {
        appState = AppState.LoggedOut
    }

    /**
     * Set app state directly — used by demo mode to bypass login.
     */
    fun setReadyState(state: AppState) {
        appState = state
    }

    private fun resolveDomain(): String {
        return if (username.contains("@")) {
            username.substringAfter("@").removeSuffix(".commcarehq.org")
        } else {
            "demo"
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
