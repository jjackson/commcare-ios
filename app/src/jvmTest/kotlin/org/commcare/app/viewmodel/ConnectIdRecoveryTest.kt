package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.network.ConnectIdApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ConnectIdViewModel covering both new-user registration
 * and existing-user recovery flow branching.
 *
 * The flow diverges at checkName(): if account_exists=true the wizard
 * skips photo/account-creation and goes backup-code → success (recovery).
 */
class ConnectIdRecoveryTest {

    // --- Test infrastructure ---

    /**
     * Mock HTTP client that returns canned responses based on the URL path.
     * Tracks all requests for verification.
     */
    private class MockConnectIdHttpClient(
        private val accountExists: Boolean = false
    ) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()

        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            val url = request.url

            return when {
                url.contains("start_configuration") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = """{"token":"test-session-token","sms_method":"firebase","required_lock":"pin"}""".encodeToByteArray()
                )

                url.contains("send_session_otp") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = "{}".encodeToByteArray()
                )

                url.contains("confirm_session_otp") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = "{}".encodeToByteArray()
                )

                url.contains("check_name") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = if (accountExists) {
                        """{"account_exists":true,"photo":"base64photodata"}"""
                    } else {
                        """{"account_exists":false}"""
                    }.encodeToByteArray()
                )

                url.contains("recover/confirm_backup_code") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = """{"username":"recovered-user","password":"recovered-pass","db_key":"recovered-dbkey"}""".encodeToByteArray()
                )

                url.contains("complete_profile") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = """{"username":"new-user","password":"new-pass","db_key":"new-dbkey"}""".encodeToByteArray()
                )

                else -> HttpResponse(
                    code = 404,
                    headers = emptyMap(),
                    body = """{"error":"unknown endpoint"}""".encodeToByteArray()
                )
            }
        }
    }

    /**
     * Mock HTTP client that returns errors for specific endpoints.
     */
    private class ErrorHttpClient(
        private val errorEndpoint: String,
        private val errorCode: Int = 400,
        private val errorBody: String = """{"error":"test error"}"""
    ) : PlatformHttpClient {
        override fun execute(request: HttpRequest): HttpResponse {
            val url = request.url
            return when {
                url.contains(errorEndpoint) -> HttpResponse(
                    code = errorCode,
                    headers = emptyMap(),
                    body = errorBody.encodeToByteArray()
                )

                url.contains("start_configuration") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = """{"token":"test-token","sms_method":"firebase","required_lock":"pin"}""".encodeToByteArray()
                )

                url.contains("send_session_otp") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = "{}".encodeToByteArray()
                )

                url.contains("confirm_session_otp") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = "{}".encodeToByteArray()
                )

                url.contains("check_name") -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = """{"account_exists":true,"photo":"photo"}""".encodeToByteArray()
                )

                else -> HttpResponse(
                    code = 200,
                    headers = emptyMap(),
                    body = "{}".encodeToByteArray()
                )
            }
        }
    }

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun createViewModel(
        httpClient: PlatformHttpClient,
        db: CommCareDatabase = createTestDatabase()
    ): ConnectIdViewModel {
        val api = ConnectIdApi(httpClient)
        val repository = ConnectIdRepository(db)
        val keychainStore = PlatformKeychainStore()
        return ConnectIdViewModel(api, repository, keychainStore)
    }

    /** Wait for coroutine-launched work to settle. */
    private fun waitForAsync(ms: Long = 200) {
        Thread.sleep(ms)
    }

    // -------------------------------------------------------------------
    // Initial state
    // -------------------------------------------------------------------

    @Test
    fun testInitialState() {
        val vm = createViewModel(MockConnectIdHttpClient())
        assertEquals(RegistrationStep.PHONE_ENTRY, vm.currentStep)
        assertFalse(vm.isLoading)
        assertNull(vm.errorMessage)
        assertFalse(vm.isRecoveryFlow)
        assertNull(vm.existingUserPhoto)
    }

    // -------------------------------------------------------------------
    // New-user registration path (account_exists = false)
    // -------------------------------------------------------------------

    @Test
    fun testNewUserPhoneSubmitAdvancesToBiometric() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = false))
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()

        assertEquals(RegistrationStep.BIOMETRIC_SETUP, vm.currentStep)
        assertFalse(vm.isLoading)
        assertNull(vm.errorMessage)
    }

    @Test
    fun testNewUserBiometricSetupAdvancesToOtp() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = false))
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()

        vm.completeBiometricSetup("pin", "1234")
        assertEquals(RegistrationStep.OTP_VERIFICATION, vm.currentStep)
    }

    @Test
    fun testNewUserOtpVerificationAdvancesToName() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = false))

        // Phone → Biometric → OTP
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()

        assertEquals(RegistrationStep.NAME_ENTRY, vm.currentStep)
    }

    @Test
    fun testNewUserNameCheckSetsNoRecovery() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = false))

        // Navigate to NAME_ENTRY
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()

        // Submit name — account_exists=false
        vm.fullName = "New User"
        vm.submitName()
        waitForAsync()

        assertEquals(RegistrationStep.BACKUP_CODE, vm.currentStep)
        assertFalse(vm.isRecoveryFlow, "New user should NOT be in recovery flow")
        assertNull(vm.existingUserPhoto)
    }

    @Test
    fun testNewUserBackupCodeAdvancesToPhoto() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = false))

        // Navigate to BACKUP_CODE via new-user path
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "New User"
        vm.submitName()
        waitForAsync()

        // Submit backup code
        vm.backupCode = "654321"
        vm.submitBackupCode()
        waitForAsync()

        assertEquals(
            RegistrationStep.PHOTO_CAPTURE, vm.currentStep,
            "New user should advance to PHOTO_CAPTURE after backup code"
        )
        assertFalse(vm.isRecoveryFlow)
    }

    @Test
    fun testNewUserFullRegistrationFlow() {
        val httpClient = MockConnectIdHttpClient(accountExists = false)
        val db = createTestDatabase()
        val vm = createViewModel(httpClient, db)

        // Step 1: Phone
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        assertEquals(RegistrationStep.BIOMETRIC_SETUP, vm.currentStep)

        // Step 2: Biometric
        vm.completeBiometricSetup("pin", "1234")
        assertEquals(RegistrationStep.OTP_VERIFICATION, vm.currentStep)

        // Step 3: OTP
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        assertEquals(RegistrationStep.NAME_ENTRY, vm.currentStep)

        // Step 4: Name
        vm.fullName = "New User"
        vm.submitName()
        waitForAsync()
        assertEquals(RegistrationStep.BACKUP_CODE, vm.currentStep)
        assertFalse(vm.isRecoveryFlow)

        // Step 5: Backup code
        vm.backupCode = "654321"
        vm.submitBackupCode()
        waitForAsync()
        assertEquals(RegistrationStep.PHOTO_CAPTURE, vm.currentStep)

        // Step 6: Photo → Account creation → Success
        vm.onPhotoCaptured("base64photo")
        waitForAsync()
        assertEquals(RegistrationStep.SUCCESS, vm.currentStep)

        // Verify user saved in repository
        val repo = ConnectIdRepository(db)
        val savedUser = repo.getUser()
        assertNotNull(savedUser, "User should be saved after registration")
        assertEquals("new-user", savedUser.userId)
        assertEquals("New User", savedUser.name)
    }

    // -------------------------------------------------------------------
    // Existing-user recovery path (account_exists = true)
    // -------------------------------------------------------------------

    @Test
    fun testRecoveryFlowNameCheckSetsRecoveryState() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = true))

        // Navigate to NAME_ENTRY
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()

        // Submit name — account_exists=true
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()

        assertEquals(RegistrationStep.BACKUP_CODE, vm.currentStep)
        assertTrue(vm.isRecoveryFlow, "Existing user should be in recovery flow")
        assertNotNull(vm.existingUserPhoto, "Existing user photo should be set")
    }

    @Test
    fun testRecoveryFlowBackupCodeGoesDirectlyToSuccess() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = true))

        // Navigate to BACKUP_CODE via recovery path
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()

        assertTrue(vm.isRecoveryFlow)

        // Submit backup code — recovery path
        vm.backupCode = "123456"
        vm.submitBackupCode()
        waitForAsync()

        assertEquals(
            RegistrationStep.SUCCESS, vm.currentStep,
            "Recovery flow should skip PHOTO_CAPTURE and go directly to SUCCESS"
        )
    }

    @Test
    fun testRecoveryFlowCredentialsStoredInKeychain() {
        val keychainStore = PlatformKeychainStore()
        val httpClient = MockConnectIdHttpClient(accountExists = true)
        val db = createTestDatabase()
        val api = ConnectIdApi(httpClient)
        val repository = ConnectIdRepository(db)
        val vm = ConnectIdViewModel(api, repository, keychainStore)

        // Navigate through recovery
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()
        vm.backupCode = "123456"
        vm.submitBackupCode()
        waitForAsync()

        assertEquals(RegistrationStep.SUCCESS, vm.currentStep)

        // Verify credentials stored in Keychain
        assertEquals("recovered-user", keychainStore.retrieve("connect_username"))
        assertEquals("recovered-pass", keychainStore.retrieve("connect_password"))
        assertEquals("recovered-dbkey", keychainStore.retrieve("connect_db_key"))
    }

    @Test
    fun testRecoveryFlowUserSavedToRepository() {
        val db = createTestDatabase()
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = true), db)

        // Navigate through recovery
        vm.phoneNumber = "5551234567"
        vm.countryCode = "+1"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()
        vm.backupCode = "123456"
        vm.submitBackupCode()
        waitForAsync()

        assertEquals(RegistrationStep.SUCCESS, vm.currentStep)

        // Verify user record saved
        val repo = ConnectIdRepository(db)
        val savedUser = repo.getUser()
        assertNotNull(savedUser, "Recovered user should be saved")
        assertEquals("recovered-user", savedUser.userId)
        assertEquals("Existing User", savedUser.name)
        assertEquals("+15551234567", savedUser.phone)
    }

    @Test
    fun testRecoveryFlowSkipsPhotoCapture() {
        val httpClient = MockConnectIdHttpClient(accountExists = true)
        val vm = createViewModel(httpClient)

        // Full recovery path
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()
        vm.backupCode = "123456"
        vm.submitBackupCode()
        waitForAsync()

        assertEquals(RegistrationStep.SUCCESS, vm.currentStep)

        // Verify that complete_profile was never called (recovery doesn't create a new account)
        val completeProfileCalls = httpClient.requests.filter {
            it.url.contains("complete_profile")
        }
        assertTrue(
            completeProfileCalls.isEmpty(),
            "Recovery flow should not call complete_profile endpoint"
        )
    }

    // -------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------

    @Test
    fun testPhoneRequiredValidation() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = ""
        vm.submitPhone()
        assertEquals(RegistrationStep.PHONE_ENTRY, vm.currentStep)
        assertEquals("Phone number required", vm.errorMessage)
    }

    @Test
    fun testNameRequiredValidation() {
        val vm = createViewModel(MockConnectIdHttpClient())

        // Navigate to NAME_ENTRY
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()

        // Submit empty name
        vm.fullName = ""
        vm.submitName()
        assertEquals(RegistrationStep.NAME_ENTRY, vm.currentStep)
        assertEquals("Name required", vm.errorMessage)
    }

    @Test
    fun testBackupCodeTooShortValidation() {
        val vm = createViewModel(MockConnectIdHttpClient(accountExists = true))

        // Navigate to BACKUP_CODE
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Test"
        vm.submitName()
        waitForAsync()

        // Try to submit short code
        vm.backupCode = "123"
        vm.submitBackupCode()
        assertEquals(RegistrationStep.BACKUP_CODE, vm.currentStep)
        assertEquals("Enter a 6-digit backup code", vm.errorMessage)
    }

    @Test
    fun testRecoveryBackupCodeFailureShowsError() {
        val httpClient = ErrorHttpClient(
            errorEndpoint = "confirm_backup_code",
            errorCode = 400,
            errorBody = """{"error":"invalid backup code"}"""
        )
        val vm = createViewModel(httpClient)

        // Navigate to BACKUP_CODE (recovery path)
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()
        assertTrue(vm.isRecoveryFlow)

        // Submit backup code — server rejects it
        vm.backupCode = "999999"
        vm.submitBackupCode()
        waitForAsync()

        assertEquals(
            RegistrationStep.BACKUP_CODE, vm.currentStep,
            "Should stay on BACKUP_CODE after failure"
        )
        assertNotNull(vm.errorMessage, "Error message should be set")
        assertTrue(vm.errorMessage!!.contains("backup code", ignoreCase = true))
    }

    @Test
    fun testClearErrorResetsMessage() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = ""
        vm.submitPhone()
        assertNotNull(vm.errorMessage)

        vm.clearError()
        assertNull(vm.errorMessage)
    }

    // -------------------------------------------------------------------
    // Navigation (goBack)
    // -------------------------------------------------------------------

    @Test
    fun testGoBackFromBiometricToPhone() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        assertEquals(RegistrationStep.BIOMETRIC_SETUP, vm.currentStep)

        vm.goBack()
        assertEquals(RegistrationStep.PHONE_ENTRY, vm.currentStep)
    }

    @Test
    fun testGoBackFromOtpToBiometric() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        assertEquals(RegistrationStep.OTP_VERIFICATION, vm.currentStep)

        vm.goBack()
        assertEquals(RegistrationStep.BIOMETRIC_SETUP, vm.currentStep)
    }

    @Test
    fun testGoBackFromNameToOtp() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        assertEquals(RegistrationStep.NAME_ENTRY, vm.currentStep)

        vm.goBack()
        assertEquals(RegistrationStep.OTP_VERIFICATION, vm.currentStep)
    }

    @Test
    fun testGoBackFromBackupCodeToName() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Test"
        vm.submitName()
        waitForAsync()
        assertEquals(RegistrationStep.BACKUP_CODE, vm.currentStep)

        vm.goBack()
        assertEquals(RegistrationStep.NAME_ENTRY, vm.currentStep)
    }

    @Test
    fun testGoBackFromPhoneStaysAtPhone() {
        val vm = createViewModel(MockConnectIdHttpClient())
        assertEquals(RegistrationStep.PHONE_ENTRY, vm.currentStep)
        vm.goBack()
        assertEquals(RegistrationStep.PHONE_ENTRY, vm.currentStep)
    }

    @Test
    fun testGoBackClearsError() {
        val vm = createViewModel(MockConnectIdHttpClient())
        vm.phoneNumber = ""
        vm.submitPhone()
        assertNotNull(vm.errorMessage)

        vm.goBack()
        assertNull(vm.errorMessage)
    }

    // -------------------------------------------------------------------
    // API call verification
    // -------------------------------------------------------------------

    @Test
    fun testRecoveryFlowCallsCorrectEndpoints() {
        val httpClient = MockConnectIdHttpClient(accountExists = true)
        val vm = createViewModel(httpClient)

        // Full recovery path
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "Existing User"
        vm.submitName()
        waitForAsync()
        vm.backupCode = "123456"
        vm.submitBackupCode()
        waitForAsync()

        // Verify the endpoints called
        val urls = httpClient.requests.map { it.url }
        assertTrue(urls.any { it.contains("start_configuration") }, "Should call start_configuration")
        assertTrue(urls.any { it.contains("confirm_session_otp") }, "Should call confirm_session_otp")
        assertTrue(urls.any { it.contains("check_name") }, "Should call check_name")
        assertTrue(urls.any { it.contains("recover/confirm_backup_code") }, "Should call recovery backup code")
        assertFalse(urls.any { it.contains("complete_profile") }, "Should NOT call complete_profile")
    }

    @Test
    fun testNewUserFlowCallsCompleteProfile() {
        val httpClient = MockConnectIdHttpClient(accountExists = false)
        val vm = createViewModel(httpClient)

        // Full new-user path through photo capture
        vm.phoneNumber = "5551234567"
        vm.submitPhone()
        waitForAsync()
        vm.completeBiometricSetup("pin", "1234")
        vm.otpCode = "123456"
        vm.verifyOtp()
        waitForAsync()
        vm.fullName = "New User"
        vm.submitName()
        waitForAsync()
        vm.backupCode = "654321"
        vm.submitBackupCode()
        waitForAsync()
        vm.onPhotoCaptured("photo-data")
        waitForAsync()

        // Verify complete_profile was called
        val urls = httpClient.requests.map { it.url }
        assertTrue(urls.any { it.contains("complete_profile") }, "New user should call complete_profile")
    }
}
