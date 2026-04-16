package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.engine.AppInstaller
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.state.AppState
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.app.viewmodel.UserKeyRecordManager.LoginMode
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
    private var currentApp: ApplicationRecord? = null

    var appState by mutableStateOf<AppState>(AppState.LoggedOut)
        private set

    /** Current login mode -- PASSWORD, PIN, or BIOMETRIC */
    var loginMode by mutableStateOf(LoginMode.PASSWORD)
        private set

    /** Error message for PIN entry */
    var pinError by mutableStateOf<String?>(null)

    /** Set to true after first successful password login if biometric is available
     *  but not yet enrolled — triggers the enrollment offer dialog. */
    var showBiometricEnrollment by mutableStateOf(false)

    /** Key record manager -- set via [setKeyRecordManager] from App.kt */
    private var keyRecordManager: UserKeyRecordManager? = null

    fun setKeyRecordManager(manager: UserKeyRecordManager) {
        keyRecordManager = manager
    }

    /** The sandbox populated after successful login */
    var sandbox: SqlDelightUserSandbox? = null
        private set

    /** Auth header for subsequent requests */
    var authHeader: String? = null
        private set

    private val httpClient = createHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

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
                // HQ Basic auth requires the fully qualified mobile worker
                // username (`user@domain.commcarehq.org`). Users can type just
                // the short form in the login field and we expand it here
                // using the resolved domain (see #391).
                val authUsername = if (username.contains("@")) {
                    username
                } else {
                    "$username@$resolvedDomain.commcarehq.org"
                }
                val credentials = encodeBasicAuth(authUsername, password)
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

    /**
     * Post-login app installation. Called after successful server auth + restore parsing.
     * Builds the profile URL from [appId] and installs the app, or falls back to a
     * minimal platform when no appId is configured (development mode).
     *
     * This is intentionally separate from [AppInstallViewModel.install], which handles
     * the pre-login fresh-install flow (setup screen, Connect downloads). This method
     * runs after the user is already authenticated and their sandbox is populated.
     */
    private fun installApp(sandbox: SqlDelightUserSandbox, domain: String) {
        try {
            val installer = AppInstaller(sandbox)

            val resolvedProfileUrl = if (appId.isNotBlank()) {
                "${serverUrl.trimEnd('/')}/a/$domain/apps/download/$appId/profile.ccpr"
            } else {
                ""
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
                primeQuickLogin(domain)
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
                primeQuickLogin(domain)
                appState = AppState.Ready(platform, sandbox, app, serverUrl, domain, authHeader!!)
            }
        } catch (e: Exception) {
            val cause = e.cause?.let { " Cause: ${it::class.simpleName}: ${it.message}" } ?: ""
            appState = AppState.InstallError("App installation failed: ${e::class.simpleName}: ${e.message}$cause")
        }
    }

    /**
     * After a successful login, store the encrypted password for quick-login
     * and update the last-login timestamp.
     */
    private fun primeQuickLogin(domain: String) {
        try {
            keyRecordManager?.primeForQuickLogin(username, domain, password)
            keyRecordManager?.updateLastLogin(username, domain)
            // After first successful password login, check if biometric is
            // available and offer enrollment. Only offer if:
            // 1. Currently in PASSWORD mode (not already using PIN/biometric)
            // 2. No PIN is set yet (first-time login on this device)
            // 3. Biometric hardware is available
            if (loginMode == LoginMode.PASSWORD) {
                val manager = keyRecordManager
                if (manager != null && !manager.hasPinSet(username, domain)) {
                    val biometric = org.commcare.app.platform.PlatformBiometricAuth()
                    if (biometric.canAuthenticate()) {
                        showBiometricEnrollment = true
                    }
                }
            }
        } catch (_: Exception) {
            // Non-fatal — quick login features just won't be available
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

    /**
     * Detect the appropriate login mode for the given user.
     * Falls back to PASSWORD if no quick-login record exists.
     */
    fun detectLoginMode() {
        val resolvedDomain = resolveDomain()
        loginMode = keyRecordManager?.getLoginMode(username, resolvedDomain) ?: LoginMode.PASSWORD
    }

    /**
     * Verify the entered PIN and, if valid, proceed with login
     * using the stored encrypted password.
     */
    fun loginWithPin(pin: String) {
        val manager = keyRecordManager ?: return
        val resolvedDomain = resolveDomain()
        val decryptedPassword = manager.verifyPinAndGetPassword(username, resolvedDomain, pin)
        if (decryptedPassword == null) {
            pinError = "Incorrect PIN"
            return
        }
        pinError = null
        password = decryptedPassword
        login()
    }

    /**
     * Authenticate via biometric and, if the stored password is available,
     * proceed with login.
     */
    fun loginWithBiometric() {
        val manager = keyRecordManager ?: return
        val resolvedDomain = resolveDomain()
        val decryptedPassword = manager.getPasswordForBiometric(username, resolvedDomain)
        if (decryptedPassword == null) {
            pinError = "Biometric login not available"
            return
        }
        pinError = null
        password = decryptedPassword
        login()
    }

    /**
     * Switch from PIN/biometric mode back to password mode
     * (e.g. user tapped "Forgot PIN?").
     */
    fun forgotPin() {
        loginMode = LoginMode.PASSWORD
        pinError = null
    }

    fun resetError() {
        appState = AppState.LoggedOut
    }

    /**
     * Log in using an HQ OAuth Bearer token (from Connect SSO exchange).
     * Calls the same restore endpoint as [login] but with Bearer auth
     * instead of Basic, then parses restore data and installs the app.
     *
     * @param token  HQ access token from /oauth/token/ exchange.
     * @param domain HQ project domain (e.g. "andreaconnect").
     */
    fun loginWithSsoToken(token: String, domain: String) {
        appState = AppState.LoggingIn(serverUrl, "ConnectID")

        scope.launch {
            try {
                val loginUrl = "${serverUrl.trimEnd('/')}/a/$domain/phone/restore/"
                authHeader = "Bearer $token"

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
                        parseRestoreResponse(response.body, domain)
                    }
                    response.code == 401 -> {
                        appState = AppState.LoginError("SSO token rejected (401)")
                    }
                    else -> {
                        appState = AppState.LoginError("Server error (${response.code})")
                    }
                }
            } catch (e: Exception) {
                appState = AppState.LoginError("SSO login failed: ${e.message}")
            }
        }
    }

    /**
     * Set app state directly — used by demo mode to bypass login.
     */
    fun setReadyState(state: AppState) {
        appState = state
    }

    /**
     * Resolve the HQ domain to use for login / restore / submit URLs.
     *
     * Priority order:
     *   1. If the username is fully qualified (`user@domain.commcarehq.org`),
     *      extract the domain from the suffix. Real mobile workers typing
     *      the full form should always work regardless of configuration.
     *   2. If an app is installed, use its domain. This lets users type
     *      just their short username (e.g. "haltest") and have the app
     *      route to the correct HQ project.
     *   3. Last-resort fallback is the hardcoded "demo" domain — useful
     *      only for demo-mode bootstrap when no app is installed yet.
     *
     * See #391 — prior to this fix, short-form usernames always went to
     * "demo" regardless of what app was installed, leading to silent 401
     * failures for any user who didn't type the `@domain.commcarehq.org`
     * suffix.
     */
    internal fun resolveDomain(): String {
        if (username.contains("@")) {
            return username.substringAfter("@").removeSuffix(".commcarehq.org")
        }
        currentApp?.domain?.let { if (it.isNotBlank()) return it }
        return "demo"
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
