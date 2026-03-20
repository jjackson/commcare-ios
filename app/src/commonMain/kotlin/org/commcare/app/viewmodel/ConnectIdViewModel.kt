package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val scope = CoroutineScope(Dispatchers.Default)

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
            if (isRecoveryFlow) {
                // Recovery: verify existing backup code and receive credentials
                val result = api.confirmBackupCodeRecovery(token, backupCode)
                isLoading = false
                result.fold(
                    onSuccess = { response ->
                        createdUsername = response.username
                        createdPassword = response.password
                        dbKey = response.dbKey
                        // Store recovered credentials securely
                        keychainStore.store("connect_username", response.username)
                        keychainStore.store("connect_password", response.password)
                        keychainStore.store("connect_db_key", response.dbKey)
                        // Save recovered user record
                        val user = ConnectIdUser(
                            userId = response.username,
                            name = fullName,
                            phone = "${countryCode}${phoneNumber}",
                            photoPath = null,
                            hasConnectAccess = false,
                            securityMethod = securityMethod
                        )
                        repository.saveUser(user)
                        // Recovery skips photo AND biometric — go straight to success
                        currentStep = RegistrationStep.SUCCESS
                    },
                    onFailure = { errorMessage = "Invalid backup code: ${it.message}" }
                )
            } else {
                // New registration: set backup code, then proceed to photo capture
                val result = api.confirmBackupCode(token, backupCode)
                isLoading = false
                result.fold(
                    onSuccess = { currentStep = RegistrationStep.PHOTO_CAPTURE },
                    onFailure = { errorMessage = "Failed: ${it.message}" }
                )
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
        val photo = photoBase64 ?: ""
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.completeProfile(token, fullName, backupCode, photo)
            isLoading = false
            result.fold(
                onSuccess = { response ->
                    createdUsername = response.username
                    createdPassword = response.password
                    dbKey = response.dbKey
                    // Store credentials securely
                    keychainStore.store("connect_username", response.username)
                    keychainStore.store("connect_password", response.password)
                    keychainStore.store("connect_db_key", response.dbKey)
                    // Save user record
                    val user = ConnectIdUser(
                        userId = response.username,
                        name = fullName,
                        phone = "${countryCode}${phoneNumber}",
                        photoPath = null,
                        hasConnectAccess = false,
                        securityMethod = securityMethod
                    )
                    repository.saveUser(user)
                    currentStep = RegistrationStep.SUCCESS
                },
                onFailure = { errorMessage = "Account creation failed: ${it.message}" }
            )
        }
    }

    // Step 2: Biometric/PIN setup — secures device, then proceeds to OTP
    fun completeBiometricSetup(method: String, pin: String? = null) {
        securityMethod = method
        if (pin != null) devicePin = pin
        if (pin != null) {
            keychainStore.store("connect_pin", pin)
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
