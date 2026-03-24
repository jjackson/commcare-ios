package org.commcare.app.viewmodel

import org.commcare.app.platform.CrashReport
import org.commcare.app.platform.PlatformCrashReporter
import org.commcare.app.platform.PlatformCrashUploader
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.*

/**
 * Tests for PlatformCrashUploader — verifies HTTP upload behavior,
 * URL construction, JSON serialization, and partial-failure semantics.
 */
class CrashUploaderTest {

    /** Mock HTTP client that records requests and returns a configurable response code. */
    private class MockClient(private val responseCode: Int = 200) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()
        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            return HttpResponse(responseCode, emptyMap(), "OK".encodeToByteArray())
        }
    }

    /** Mock HTTP client that fails with an exception on every request. */
    private class FailingClient : PlatformHttpClient {
        override fun execute(request: HttpRequest): HttpResponse {
            throw RuntimeException("Network unreachable")
        }
    }

    /** Injects test reports into the JVM PlatformCrashReporter via reflection. */
    private fun injectReports(reporter: PlatformCrashReporter, reports: List<CrashReport>) {
        val field = PlatformCrashReporter::class.java.getDeclaredField("reports")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val list = field.get(reporter) as MutableList<CrashReport>
        list.addAll(reports)
    }

    private fun sampleReport(message: String = "NullPointerException"): CrashReport {
        return CrashReport(
            timestamp = "2026-03-24T12:00:00Z",
            message = message,
            stackTrace = "at com.example.Foo.bar(Foo.kt:42)\nat com.example.Main.main(Main.kt:10)",
            deviceInfo = mapOf("platform" to "JVM", "os" to "macOS")
        )
    }

    @Test
    fun testNoReportsSkipsUpload() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "test", "Basic dGVzdA==")

        val count = uploader.uploadPendingReports(reporter)

        assertEquals(0, count, "Empty reporter should upload 0 reports")
        assertTrue(client.requests.isEmpty(), "No HTTP requests should be made")
    }

    @Test
    fun testUploadSingleReportSuccess() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(sampleReport()))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "test-domain", "Basic abc")
        val count = uploader.uploadPendingReports(reporter)

        assertEquals(1, count, "Should upload 1 report")
        assertEquals(1, client.requests.size, "Should make 1 HTTP request")
        // Reports should be cleared after successful upload
        assertEquals(0, reporter.getPendingReports().size, "Reports should be cleared after success")
    }

    @Test
    fun testUploadMultipleReportsSuccess() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(
            sampleReport("Error A"),
            sampleReport("Error B"),
            sampleReport("Error C")
        ))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "d", "Basic x")
        val count = uploader.uploadPendingReports(reporter)

        assertEquals(3, count)
        assertEquals(3, client.requests.size, "Should make 3 HTTP requests")
        assertEquals(0, reporter.getPendingReports().size, "All reports cleared on full success")
    }

    @Test
    fun testUploadUrlFormat() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(sampleReport()))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com/", "my-domain", "Basic x")
        uploader.uploadPendingReports(reporter)

        val request = client.requests.single()
        assertEquals("https://hq.example.com/a/my-domain/phone/post_crash/", request.url)
        assertEquals("POST", request.method)
        assertEquals("Basic x", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Content-Type"])
    }

    @Test
    fun testUploadUrlTrimsTrailingSlash() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(sampleReport()))

        // serverUrl has trailing slash
        val uploader = PlatformCrashUploader(client, "https://hq.example.com///", "d", "Basic x")
        uploader.uploadPendingReports(reporter)

        val url = client.requests.single().url
        assertTrue(url.startsWith("https://hq.example.com"), "Trailing slashes should be trimmed")
        assertFalse(url.contains("///"), "Multiple trailing slashes should not appear")
    }

    @Test
    fun testServerErrorDoesNotClearReports() {
        val client = MockClient(500)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(sampleReport()))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "d", "Basic x")
        val count = uploader.uploadPendingReports(reporter)

        assertEquals(0, count, "500 should not count as uploaded")
        assertEquals(1, reporter.getPendingReports().size, "Reports should be preserved on failure")
    }

    @Test
    fun testNetworkExceptionDoesNotClearReports() {
        val client = FailingClient()
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(sampleReport()))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "d", "Basic x")
        val count = uploader.uploadPendingReports(reporter)

        assertEquals(0, count, "Network failure should not count as uploaded")
        assertEquals(1, reporter.getPendingReports().size, "Reports should be preserved on exception")
    }

    @Test
    fun testJsonBodyContainsExpectedFields() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(sampleReport("Test error message")))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "d", "Basic x")
        uploader.uploadPendingReports(reporter)

        val body = client.requests.single().body!!.decodeToString()
        assertTrue(body.contains("\"timestamp\""), "JSON should contain timestamp field")
        assertTrue(body.contains("\"message\""), "JSON should contain message field")
        assertTrue(body.contains("\"stack_trace\""), "JSON should contain stack_trace field")
        assertTrue(body.contains("\"device_info\""), "JSON should contain device_info field")
        assertTrue(body.contains("Test error message"), "JSON should contain the actual message")
        assertTrue(body.contains("JVM"), "JSON should contain device platform")
    }

    @Test
    fun testJsonEscapesSpecialCharacters() {
        val client = MockClient(200)
        val reporter = PlatformCrashReporter()
        injectReports(reporter, listOf(
            CrashReport(
                timestamp = "2026-03-24",
                message = "Error with \"quotes\" and \\backslash",
                stackTrace = "line1\nline2\ttab",
                deviceInfo = mapOf("key" to "value\"special")
            )
        ))

        val uploader = PlatformCrashUploader(client, "https://hq.example.com", "d", "Basic x")
        uploader.uploadPendingReports(reporter)

        val body = client.requests.single().body!!.decodeToString()
        // Verify escaped characters are present (not raw special chars)
        assertTrue(body.contains("\\\"quotes\\\""), "Quotes should be escaped")
        assertTrue(body.contains("\\\\backslash"), "Backslashes should be escaped")
        assertTrue(body.contains("\\n"), "Newlines should be escaped")
        assertTrue(body.contains("\\t"), "Tabs should be escaped")
    }

    @Test
    fun testCrashReportDataClass() {
        val report = CrashReport(
            timestamp = "2026-03-24T12:00:00Z",
            message = "NullPointerException",
            stackTrace = "at com.example.Foo.bar(Foo.kt:42)\nat com.example.Main.main(Main.kt:10)",
            deviceInfo = mapOf("platform" to "JVM", "os" to "macOS")
        )
        assertEquals("NullPointerException", report.message)
        assertEquals("JVM", report.deviceInfo["platform"])
        assertEquals("macOS", report.deviceInfo["os"])
        assertTrue(report.stackTrace.contains("Foo.kt:42"))
    }
}
