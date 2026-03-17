package org.commcare.app.integration

import org.junit.Ignore
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Integration tests against a real CommCare HQ instance.
 *
 * @Ignore'd by default — these require real HQ credentials and network access.
 * Remove @Ignore and set environment variables to run locally.
 *
 * See HqTestConfig for required environment variables.
 *
 * Run with:
 *   export COMMCARE_HQ_URL="https://www.commcarehq.org"
 *   export COMMCARE_USERNAME="user@domain"
 *   export COMMCARE_PASSWORD="password"
 *   export COMMCARE_DOMAIN="demo"
 *   ./gradlew :app:jvmTest --tests "*HqIntegrationTest*"
 */
@Ignore("Requires real CommCare HQ credentials — set env vars and remove @Ignore to run")
class HqIntegrationTest {

    private fun httpGet(urlString: String, headers: Map<String, String> = emptyMap()): HttpResponse {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }

        return try {
            val code = conn.responseCode
            val body = if (code in 200..299) {
                conn.inputStream.readBytes().decodeToString()
            } else {
                conn.errorStream?.readBytes()?.decodeToString() ?: ""
            }
            HttpResponse(code, body)
        } finally {
            conn.disconnect()
        }
    }

    data class HttpResponse(val code: Int, val body: String)

    @Test
    fun testLogin() {
        assertTrue(HqTestConfig.isConfigured, "HQ credentials not configured")

        val response = httpGet(
            HqTestConfig.restoreUrl(),
            mapOf("Authorization" to HqTestConfig.basicAuthHeader())
        )

        assertTrue(
            response.code in listOf(200, 412),
            "Expected 200 or 412 (no new data), got ${response.code}"
        )

        if (response.code == 200) {
            assertTrue(
                response.body.contains("<OpenRosaResponse") || response.body.contains("<restoredata"),
                "Response should contain restore XML"
            )
            println("Login successful — received restore data (${response.body.length} bytes)")
        } else {
            println("Login successful — server returned 412 (no new data)")
        }
    }

    @Test
    fun testRestoreContainsCaseData() {
        assertTrue(HqTestConfig.isConfigured, "HQ credentials not configured")

        val response = httpGet(
            HqTestConfig.restoreUrl(),
            mapOf("Authorization" to HqTestConfig.basicAuthHeader())
        )

        assertEquals(200, response.code, "Expected 200 for initial restore")
        assertTrue(response.body.isNotEmpty(), "Restore body should not be empty")

        // Check for restore XML structure
        val hasRestoreId = response.body.contains("<restore_id>") || response.body.contains("<Sync>")
        assertTrue(hasRestoreId, "Restore should contain sync token or restore_id")

        println("Restore received — ${response.body.length} bytes")
        if (response.body.contains("<case ")) {
            println("  Contains case data")
        }
        if (response.body.contains("<fixture ")) {
            println("  Contains fixture data")
        }
    }

    @Test
    fun testAppInstall() {
        assertTrue(HqTestConfig.isConfigured, "HQ credentials not configured")
        assertTrue(HqTestConfig.appId.isNotBlank(), "COMMCARE_APP_ID not set")

        val profileUrl = "${HqTestConfig.hqUrl.trimEnd('/')}/a/${HqTestConfig.domain}" +
            "/apps/api/download_ccz/?app_id=${HqTestConfig.appId}"

        val response = httpGet(
            profileUrl,
            mapOf("Authorization" to HqTestConfig.basicAuthHeader())
        )

        assertTrue(
            response.code in 200..399,
            "Expected success or redirect for app download, got ${response.code}"
        )

        println("App download request returned ${response.code} (${response.body.length} bytes)")
    }

    @Test
    fun testFormSubmission() {
        assertTrue(HqTestConfig.isConfigured, "HQ credentials not configured")

        // Create a minimal valid OpenRosa submission XML
        val submissionXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <data xmlns:jrm="http://dev.commcarehq.org/jr/xforms"
                  xmlns="http://commcarehq.org/test"
                  uiVersion="1" version="1" name="test">
                <meta>
                    <deviceID>commcare-ios-test</deviceID>
                    <timeStart>2026-03-16T00:00:00.000Z</timeStart>
                    <timeEnd>2026-03-16T00:00:01.000Z</timeEnd>
                    <username>${HqTestConfig.username}</username>
                    <userID>test-user-id</userID>
                    <instanceID>test-${System.currentTimeMillis()}</instanceID>
                </meta>
            </data>
        """.trimIndent()

        // Note: This test only verifies connectivity and auth, not actual submission
        // (which would require a properly registered form xmlns)
        val url = URL(HqTestConfig.submitUrl())
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Authorization", HqTestConfig.basicAuthHeader())
        conn.setRequestProperty("Content-Type", "text/xml")
        conn.doOutput = true
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000

        try {
            conn.outputStream.write(submissionXml.toByteArray())
            val code = conn.responseCode

            // We expect either success (201) or a form-not-registered error (422/500)
            // Either way, connectivity and auth work
            assertTrue(
                code != 401 && code != 403,
                "Auth should work for submission endpoint, got $code"
            )
            println("Form submission test: HTTP $code")
        } finally {
            conn.disconnect()
        }
    }
}
