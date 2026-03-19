package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.app.model.ConnectIdUser
import org.commcare.app.model.RegistrationSession
import org.commcare.app.network.ConnectIdApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.ConnectIdRepository

enum class RegistrationStep {
    PHONE_ENTRY, OTP_VERIFICATION, NAME_ENTRY, BACKUP_CODE,
    PHOTO_CAPTURE, ACCOUNT_CREATION, BIOMETRIC_SETUP, SUCCESS
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
                onSuccess = { session = it; currentStep = RegistrationStep.OTP_VERIFICATION },
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

    // Step 3: Submit name
    fun submitName() {
        val token = session?.sessionToken ?: return
        if (fullName.isBlank()) { errorMessage = "Name required"; return }
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.checkName(token, fullName)
            isLoading = false
            result.fold(
                onSuccess = { currentStep = RegistrationStep.BACKUP_CODE },
                onFailure = { errorMessage = "Name check failed: ${it.message}" }
            )
        }
    }

    // Step 4: Submit backup code
    fun submitBackupCode() {
        val token = session?.sessionToken ?: return
        if (backupCode.length < 6) { errorMessage = "Enter a 6-digit backup code"; return }
        isLoading = true; errorMessage = null
        scope.launch {
            val result = api.confirmBackupCode(token, backupCode)
            isLoading = false
            result.fold(
                onSuccess = { currentStep = RegistrationStep.PHOTO_CAPTURE },
                onFailure = { errorMessage = "Failed: ${it.message}" }
            )
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
                    currentStep = RegistrationStep.BIOMETRIC_SETUP
                },
                onFailure = { errorMessage = "Account creation failed: ${it.message}" }
            )
        }
    }

    // Step 7: Biometric/PIN setup complete
    fun completeBiometricSetup(method: String, pin: String? = null) {
        securityMethod = method
        if (pin != null) devicePin = pin
        // Persist the user
        val user = ConnectIdUser(
            userId = createdUsername ?: "",
            name = fullName,
            phone = "${countryCode}${phoneNumber}",
            photoPath = null,  // photo stored separately if needed
            hasConnectAccess = false,  // will be updated after first opportunity check
            securityMethod = method
        )
        repository.saveUser(user)
        if (pin != null) {
            keychainStore.store("connect_pin", pin)
        }
        currentStep = RegistrationStep.SUCCESS
    }

    // Step 8: Done
    fun finish() {
        // Reset wizard state — caller handles navigation
    }

    // Navigation
    fun goBack() {
        currentStep = when (currentStep) {
            RegistrationStep.OTP_VERIFICATION -> RegistrationStep.PHONE_ENTRY
            RegistrationStep.NAME_ENTRY -> RegistrationStep.OTP_VERIFICATION
            RegistrationStep.BACKUP_CODE -> RegistrationStep.NAME_ENTRY
            RegistrationStep.PHOTO_CAPTURE -> RegistrationStep.BACKUP_CODE
            RegistrationStep.BIOMETRIC_SETUP -> RegistrationStep.PHOTO_CAPTURE
            else -> currentStep  // can't go back from PHONE_ENTRY, ACCOUNT_CREATION, SUCCESS
        }
        errorMessage = null
    }
}
