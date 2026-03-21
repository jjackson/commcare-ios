package org.commcare.app.network

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
 * Tests for ConnectIdApi JSON parsing through its public API methods.
 *
 * Uses a mock PlatformHttpClient that returns controlled JSON responses to
 * exercise the private extractJsonString, extractJsonNumber, extractJsonBoolean,
 * and escapeJson helpers inside ConnectIdApi.
 */
class ConnectIdApiJsonTest {

    /** A mock HTTP client that returns a pre-configured response. */
    private class MockHttpClient(
        private val responseCode: Int = 200,
        private val responseBody: String? = null,
        private val errorBody: String? = null
    ) : PlatformHttpClient {
        var lastRequest: HttpRequest? = null
            private set

        override fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            return HttpResponse(
                code = responseCode,
                headers = emptyMap(),
                body = responseBody?.encodeToByteArray(),
                errorBody = errorBody?.encodeToByteArray()
            )
        }
    }

    // =====================================================================
    // startConfiguration — exercises extractJsonString
    // =====================================================================

    @Test
    fun testStartConfiguration_basicJsonParsing() {
        val json = """{"token":"abc123","sms_method":"firebase","required_lock":"pin"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("abc123", session.sessionToken)
        assertEquals("firebase", session.smsMethod)
        assertEquals("pin", session.requiredLock)
    }

    @Test
    fun testStartConfiguration_jsonWithWhitespace() {
        val json = """{ "token" : "tok-456" , "sms_method" : "twilio" , "required_lock" : "password" }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("tok-456", session.sessionToken)
        assertEquals("twilio", session.smsMethod)
        assertEquals("password", session.requiredLock)
    }

    @Test
    fun testStartConfiguration_missingFields_defaultsToEmpty() {
        // Only "token" present, other keys missing
        val json = """{"token":"tok-only"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("tok-only", session.sessionToken)
        // Missing keys should default to their fallback values
        assertEquals("firebase", session.smsMethod)
        assertEquals("pin", session.requiredLock)
    }

    @Test
    fun testStartConfiguration_emptyResponse_fails() {
        val client = MockHttpClient(responseBody = null)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isFailure)
    }

    @Test
    fun testStartConfiguration_httpError_fails() {
        val client = MockHttpClient(responseCode = 400, responseBody = """{"error":"bad request"}""")
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertTrue(ex.message!!.contains("400"))
    }

    @Test
    fun testStartConfiguration_jsonWithEscapedQuotesInValue() {
        // The value contains escaped quotes: token is: abc\"def
        val json = """{"token":"abc\"def","sms_method":"firebase","required_lock":"pin"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
        // The escaped quote in the JSON value should be included literally as: abc\"def
        // (ConnectIdApi's extractJsonString does not unescape JSON escape sequences)
        val session = result.getOrThrow()
        assertEquals("abc\\\"def", session.sessionToken)
    }

    @Test
    fun testStartConfiguration_emptyStringValues() {
        val json = """{"token":"","sms_method":"","required_lock":""}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
        val session = result.getOrThrow()
        assertEquals("", session.sessionToken)
        assertEquals("", session.smsMethod)
        assertEquals("", session.requiredLock)
    }

    // =====================================================================
    // getOAuthToken — exercises extractJsonString + extractJsonNumber
    // =====================================================================

    @Test
    fun testGetOAuthToken_basicParsing() {
        val json = """{"access_token":"at-xyz-789","expires_in":7200,"token_type":"Bearer"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.getOAuthToken("user@test", "pass123")
        assertTrue(result.isSuccess)
        val tokens = result.getOrThrow()
        assertEquals("at-xyz-789", tokens.accessToken)
        assertEquals(7200L, tokens.expiresIn)
    }

    @Test
    fun testGetOAuthToken_missingExpiresIn_defaultsTo3600() {
        val json = """{"access_token":"at-abc","token_type":"Bearer"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.getOAuthToken("user@test", "pass123")
        assertTrue(result.isSuccess)
        val tokens = result.getOrThrow()
        assertEquals("at-abc", tokens.accessToken)
        assertEquals(3600L, tokens.expiresIn)
    }

    @Test
    fun testGetOAuthToken_missingAccessToken_fails() {
        val json = """{"token_type":"Bearer","expires_in":3600}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.getOAuthToken("user@test", "pass123")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("access_token"))
    }

    @Test
    fun testGetOAuthToken_largeExpiresIn() {
        val json = """{"access_token":"at-123","expires_in":86400}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.getOAuthToken("user", "pass")
        assertTrue(result.isSuccess)
        assertEquals(86400L, result.getOrThrow().expiresIn)
    }

    @Test
    fun testGetOAuthToken_sendsCorrectFormBody() {
        val json = """{"access_token":"at-123","expires_in":3600}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        api.getOAuthToken("user@test.com", "p@ss w0rd!")
        val body = client.lastRequest!!.body!!.decodeToString()
        assertTrue(body.contains("grant_type=password"))
        assertTrue(body.contains("client_id="))
        // username and password should be form-url-encoded
        assertTrue(body.contains("username=user%40test.com"))
        assertTrue(body.contains("password=p%40ss+w0rd%21"))
    }

    @Test
    fun testGetOAuthToken_expiresInWithWhitespace() {
        val json = """{"access_token": "at-ws", "expires_in": 1800}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.getOAuthToken("user", "pass")
        assertTrue(result.isSuccess)
        assertEquals("at-ws", result.getOrThrow().accessToken)
        assertEquals(1800L, result.getOrThrow().expiresIn)
    }

    // =====================================================================
    // checkName — exercises extractJsonBoolean + extractJsonString
    // =====================================================================

    @Test
    fun testCheckName_accountExists_true() {
        val json = """{"account_exists":true,"photo":"base64data=="}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.checkName("session-tok", "John")
        assertTrue(result.isSuccess)
        val resp = result.getOrThrow()
        assertTrue(resp.accountExists)
        assertEquals("base64data==", resp.existingPhoto)
    }

    @Test
    fun testCheckName_accountExists_false() {
        val json = """{"account_exists":false}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.checkName("session-tok", "NewUser")
        assertTrue(result.isSuccess)
        val resp = result.getOrThrow()
        assertFalse(resp.accountExists)
        assertNull(resp.existingPhoto)
    }

    @Test
    fun testCheckName_booleanWithWhitespace() {
        val json = """{"account_exists" : true , "photo" : null}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.checkName("session-tok", "User")
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().accountExists)
        // photo is JSON null, so should be returned as null
        assertNull(result.getOrThrow().existingPhoto)
    }

    @Test
    fun testCheckName_missingAccountExists_defaultsFalse() {
        val json = """{"photo":"somedata"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.checkName("session-tok", "User")
        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().accountExists)
    }

    // =====================================================================
    // completeProfile — exercises multiple extractJsonString calls
    // =====================================================================

    @Test
    fun testCompleteProfile_parsesAllFields() {
        val json = """{"username":"user123","password":"p@ss","db_key":"key-abc"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.completeProfile("session-tok", "John", "1234", "photo-base64")
        assertTrue(result.isSuccess)
        val resp = result.getOrThrow()
        assertEquals("user123", resp.username)
        assertEquals("p@ss", resp.password)
        assertEquals("key-abc", resp.dbKey)
    }

    @Test
    fun testCompleteProfile_escapesNameInBody() {
        val json = """{"username":"u","password":"p","db_key":"k"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        api.completeProfile("session-tok", "John \"Doe\"", "1234", "photo")
        val body = client.lastRequest!!.body!!.decodeToString()
        // The name with quotes should be escaped in the request body
        assertTrue(body.contains("John \\\"Doe\\\""), "Name should be JSON-escaped in request body")
    }

    // =====================================================================
    // fetchDbKey — exercises extractJsonString for single field extraction
    // =====================================================================

    @Test
    fun testFetchDbKey_success() {
        val json = """{"db_key":"encryption-key-12345"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.fetchDbKey("access-token")
        assertTrue(result.isSuccess)
        assertEquals("encryption-key-12345", result.getOrThrow())
    }

    @Test
    fun testFetchDbKey_missingKey_fails() {
        val json = """{"other_field":"value"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.fetchDbKey("access-token")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("db_key"))
    }

    @Test
    fun testFetchDbKey_nullValue_fails() {
        // ConnectIdApi's extractJsonString sees null as no match since it looks for '"' after ':'
        val json = """{"db_key":null}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.fetchDbKey("access-token")
        assertTrue(result.isFailure)
    }

    // =====================================================================
    // confirmOtp / sendOtp — exercises authenticated POST path
    // =====================================================================

    @Test
    fun testSendOtp_successOnHttp200() {
        val client = MockHttpClient(responseCode = 200, responseBody = """{"status":"sent"}""")
        val api = ConnectIdApi(client)

        val result = api.sendOtp("session-token")
        assertTrue(result.isSuccess)
        // Verify authorization header is set
        assertEquals("Bearer session-token", client.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun testConfirmOtp_successOnHttp200() {
        val client = MockHttpClient(responseCode = 200, responseBody = "{}")
        val api = ConnectIdApi(client)

        val result = api.confirmOtp("session-token", "123456")
        assertTrue(result.isSuccess)
        val body = client.lastRequest!!.body!!.decodeToString()
        assertTrue(body.contains("123456"))
    }

    @Test
    fun testConfirmOtp_failsOnHttp401() {
        val client = MockHttpClient(responseCode = 401, errorBody = "Unauthorized")
        val api = ConnectIdApi(client)

        val result = api.confirmOtp("bad-token", "123456")
        assertTrue(result.isFailure)
    }

    // =====================================================================
    // Edge cases for JSON value extraction
    // =====================================================================

    @Test
    fun testJsonParsingWithUnicodeValues() {
        val json = """{"token":"tok-\u00e9\u00e0\u00fc","sms_method":"firebase","required_lock":"pin"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
    }

    @Test
    fun testJsonParsingWithNestedBraces() {
        // The token value doesn't contain braces, but the JSON has extra fields with objects
        val json = """{"token":"tok-nested","extra":{"nested":true},"sms_method":"firebase","required_lock":"pin"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.startConfiguration("+15551234567")
        assertTrue(result.isSuccess)
        assertEquals("tok-nested", result.getOrThrow().sessionToken)
    }

    @Test
    fun testConfirmBackupCodeRecovery_parsesCredentials() {
        val json = """{"username":"recovered_user","password":"new_pass_123","db_key":"recov-key"}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectIdApi(client)

        val result = api.confirmBackupCodeRecovery("session-tok", "backup-code")
        assertTrue(result.isSuccess)
        val resp = result.getOrThrow()
        assertEquals("recovered_user", resp.username)
        assertEquals("new_pass_123", resp.password)
        assertEquals("recov-key", resp.dbKey)
    }
}
