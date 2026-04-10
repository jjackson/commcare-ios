package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.model.ConnectIdUser
import org.commcare.app.model.RegistrationSession
import org.commcare.app.network.CheckNameResponse
import org.commcare.app.network.ConnectIdApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.ConnectIdRepository

enum class RegistrationStep {
    PHONE_ENTRY, BIOMETRIC_SETUP, OTP_VERIFICATION, NAME_ENTRY, BACKUP_CODE,
    PHOTO_CAPTURE, ACCOUNT_CREATION, SUCCESS
}

class ConnectIdViewModel(
    private val api: ConnectIdApi,
    private val repository: ConnectIdRepository,
    private val keychainStore: PlatformKeychainStore
) {
    var currentStep by mutableStateOf(RegistrationStep.PHONE_ENTRY)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Collected data across steps
    var phoneNumber by mutableStateOf("")
    var countryCode by mutableStateOf("+1")
    var otpCode by mutableStateOf("")
    var fullName by mutableStateOf("")
    var backupCode by mutableStateOf("")
    var photoBase64 by mutableStateOf<String?>(null)
    var securityMethod by mutableStateOf("pin")  // "pin" or "biometric"
    var devicePin by mutableStateOf("")

    // Recovery flow state — set when check_name returns account_exists=true
    var isRecoveryFlow by mutableStateOf(false)
        private set
    var existingUserPhoto by mutableStateOf<String?>(null)
        private set

    // Server state
    private var session: RegistrationSession? = null
    private var createdUsername: String? = null
    private var createdPassword: String? = null
    private var dbKey: String? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 1x1 transparent PNG, data-URI encoded. Used as a placeholder when
    // the user skips the photo step during Connect ID registration —
    // see createAccount() comment for context. The connect-id server's
    // upload_photo_to_s3 function calls split_base64_string() which
    // expects the "data:image/<type>;base64,..." data-URI format, not
    // raw base64. Without the prefix, upload_photo_to_s3 returns a
    // 500 FAILED_TO_UPLOAD error.
    private val PLACEHOLDER_PHOTO_BASE64 =
        "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lxKUAAAAASUVORK5CYII="

    fun cancel() { scope.cancel() }

    fun clearError() { errorMessage = null }

    // Step 1: Start configuration
    fun submitPhone() {
        if (phoneNumber.isBlank()) { errorMessage = "Phone number required"; return }
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.startConfiguration("${countryCode}${phoneNumber}")
            isLoading = false
            result.fold(
                onSuccess = { session = it; currentStep = RegistrationStep.BIOMETRIC_SETUP },
                onFailure = { errorMessage = "Registration failed: ${it.message}" }
            )
        }
    }

    // Step 2a: Send OTP
    fun sendOtp() {
        val token = session?.sessionToken ?: return
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.sendOtp(token)
            isLoading = false
            result.fold(
                onSuccess = { /* OTP sent, user enters code */ },
                onFailure = { errorMessage = "Failed to send OTP: ${it.message}" }
            )
        }
    }

    // Step 2b: Verify OTP
    fun verifyOtp() {
        val token = session?.sessionToken ?: return
        if (otpCode.isBlank()) { errorMessage = "Enter the verification code"; return }
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.confirmOtp(token, otpCode)
            isLoading = false
            result.fold(
                onSuccess = { currentStep = RegistrationStep.NAME_ENTRY },
                onFailure = { errorMessage = "Invalid code: ${it.message}" }
            )
        }
    }

    // Step 3: Submit name — check_name response determines new vs recovery flow
    fun submitName() {
        val token = session?.sessionToken ?: return
        if (fullName.isBlank()) { errorMessage = "Name required"; return }
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.checkName(token, fullName)
            isLoading = false
            result.fold(
                onSuccess = { response: CheckNameResponse ->
                    if (response.accountExists) {
                        // Recovery flow: existing user on a new device
                        isRecoveryFlow = true
                        existingUserPhoto = response.existingPhoto?.takeIf { it.isNotEmpty() }
                        // Skip PHOTO_CAPTURE — go straight to BACKUP_CODE (input mode)
                    }
                    currentStep = RegistrationStep.BACKUP_CODE
                },
                onFailure = { errorMessage = "Name check failed: ${it.message}" }
            )
        }
    }

    // Step 4: Submit backup code — branches on recovery vs new registration
    fun submitBackupCode() {
        val token = session?.sessionToken ?: return
        if (backupCode.length < 6) { errorMessage = "Enter a 6-digit backup code"; return }
        isLoading = true; errorMessage = null
        scope.launch {
            try {
                if (isRecoveryFlow) {
                    // Recovery: verify existing backup code and receive credentials
                    val result = api.confirmBackupCodeRecovery(token, backupCode)
                    isLoading = false
                    result.fold(
                        onSuccess = { response ->
                            createdUsername = response.username
                            createdPassword = response.password
                            dbKey = response.dbKey
                            // Store recovered credentials — keychain + DB fallback.
                            // The iOS keychain SecItemAdd silently fails in some
                            // Compose contexts, so we also persist to the DB.
                            try {
                                keychainStore.store("connect_username", response.username)
                                keychainStore.store("connect_password", response.password)
                                keychainStore.store("connect_db_key", response.dbKey)
                            } catch (_: Exception) { /* keychain may silently fail */ }
                            try {
                                repository.db.commCareQueries.setPreference("connect_username", response.username)
                                repository.db.commCareQueries.setPreference("connect_password", response.password)
                                repository.db.commCareQueries.setPreference("connect_db_key", response.dbKey)
                            } catch (e: Exception) {
                                errorMessage = "Failed to store credentials: ${e.message}"
                                return@launch
                            }
                            // Immediately fetch and cache an OAuth token using
                            // the credentials we just received. This ensures the
                            // token is available for marketplace API calls without
                            // relying on a later keychain round-trip (which may
                            // fail due to iOS keychain timing/access issues on
                            // simulator). See #389.
                            try {
                                val tokenResult = api.getOAuthToken(response.username, response.password)
                                tokenResult.getOrNull()?.let { tokens ->
                                    val expiryAt = org.commcare.app.platform.currentEpochSeconds() + tokens.expiresIn - 60L
                                    keychainStore.store("connect_access_token", tokens.accessToken)
                                    keychainStore.store("connect_token_expiry", expiryAt.toString())
                                }
                            } catch (_: Exception) {
                                // Non-fatal: token will be refreshed on demand
                            }
                            // Save recovered user record
                            try {
                                val user = ConnectIdUser(
                                    userId = response.username,
                                    name = fullName,
                                    phone = "${countryCode}${phoneNumber}",
                                    photoPath = null,
                                    hasConnectAccess = true,
                                    securityMethod = securityMethod
                                )
                                repository.saveUser(user)
                            } catch (e: Exception) {
                                println("[ConnectId] Repository save failed: ${e.message}")
                            }
                            // Recovery skips photo AND biometric — go straight to success
                            currentStep = RegistrationStep.SUCCESS
                        },
                        onFailure = { errorMessage = "Invalid backup code: ${it.message}" }
                    )
                } else {
                    // New registration: the backup code is stored in the
                    // `backupCode` state here and passed to complete_profile
                    // (see createAccount() below) which is where it actually
                    // gets saved on the server. We do NOT call
                    // confirm_backup_code here — that endpoint is recovery-
                    // only and requires the user to already exist in the DB.
                    // Calling it during new registration hits
                    // ConnectUser.DoesNotExist on the server and returns 500.
                    // Caught by Phase 9 E2E testing — see issue #385.
                    isLoading = false
                    currentStep = RegistrationStep.PHOTO_CAPTURE
                }
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Backup code error: ${e::class.simpleName}: ${e.message}"
            }
        }
    }

    // Step 5: Photo captured (called by UI after camera capture)
    fun onPhotoCaptured(base64: String) {
        photoBase64 = base64
        currentStep = RegistrationStep.ACCOUNT_CREATION
        createAccount()
    }

    // Step 6: Create account (automatic)
    private fun createAccount() {
        val token = session?.sessionToken ?: return
        // The connect-id complete_profile endpoint rejects missing/empty
        // photo with a 400 MISSING_DATA. When the user skips the photo
        // step (PhotoCaptureStep's skip button passes ""), substitute a
        // 1x1 transparent PNG placeholder so the request succeeds. The
        // placeholder is uploaded to S3 and displayed as the user's
        // profile photo — it's a legitimate (if blank) image.
        // Caught by Phase 9 E2E testing — see issue #385.
        val photo = photoBase64?.takeIf { it.isNotBlank() } ?: PLACEHOLDER_PHOTO_BASE64
        isLoading = true; errorMessage = null
        scope.launch {
            try {
                val result = api.completeProfile(token, fullName, backupCode, photo)
                isLoading = false
                result.fold(
                    onSuccess = { response ->
                        createdUsername = response.username
                        createdPassword = response.password
                        dbKey = response.dbKey
                        try {
                            keychainStore.store("connect_username", response.username)
                            keychainStore.store("connect_password", response.password)
                            keychainStore.store("connect_db_key", response.dbKey)
                        } catch (e: Exception) {
                            errorMessage = "Failed to store credentials securely: ${e.message}"
                            return@launch
                        }
                        try {
                            val user = ConnectIdUser(
                                userId = response.username,
                                name = fullName,
                                phone = "${countryCode}${phoneNumber}",
                                photoPath = null,
                                hasConnectAccess = false,
                                securityMethod = securityMethod
                            )
                            repository.saveUser(user)
                        } catch (e: Exception) {
                            println("[ConnectId] Repository save failed: ${e.message}")
                        }
                        currentStep = RegistrationStep.SUCCESS
                    },
                    onFailure = { errorMessage = "Account creation failed: ${it.message}" }
                )
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Account creation error: ${e::class.simpleName}: ${e.message}"
            }
        }
    }

    // Step 2: Biometric/PIN setup — secures device, then proceeds to OTP
    fun completeBiometricSetup(method: String, pin: String? = null) {
        securityMethod = method
        if (pin != null) devicePin = pin
        if (pin != null) {
            // Keychain writes can fail on certain platform edge cases (see
            // PlatformKeychainStore comment + issue #385). Catching here
            // surfaces the error to the user instead of crashing the app.
            try {
                keychainStore.store("connect_pin", pin)
            } catch (e: Throwable) {
                errorMessage = "Failed to store PIN: ${e::class.simpleName}: ${e.message}"
                return
            }
        }
        // After biometric setup, go to OTP verification
        currentStep = RegistrationStep.OTP_VERIFICATION
    }

    // Step 8: Done
    fun finish() {
        // Reset wizard state — caller handles navigation
    }

    // Navigation — matches Android order: PHONE → BIOMETRIC → OTP → NAME → BACKUP_CODE → PHOTO
    fun goBack() {
        currentStep = when (currentStep) {
            RegistrationStep.BIOMETRIC_SETUP -> RegistrationStep.PHONE_ENTRY
            RegistrationStep.OTP_VERIFICATION -> RegistrationStep.BIOMETRIC_SETUP
            RegistrationStep.NAME_ENTRY -> RegistrationStep.OTP_VERIFICATION
            RegistrationStep.BACKUP_CODE -> RegistrationStep.NAME_ENTRY
            RegistrationStep.PHOTO_CAPTURE -> RegistrationStep.BACKUP_CODE
            else -> currentStep  // can't go back from PHONE_ENTRY, ACCOUNT_CREATION, SUCCESS
        }
        errorMessage = null
    }
}
