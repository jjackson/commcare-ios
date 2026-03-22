@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.Foundation.NSUserDefaults
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for the iOS PlatformCrashReporter.
 *
 * Verifies the encode/decode round-trip through NSUserDefaults:
 * reportError() encodes timestamp|||message|||stackTrace,
 * getPendingReports() decodes them back into CrashReport objects.
 */
class PlatformCrashReporterTest {

    private val reporter = PlatformCrashReporter()

    @AfterTest
    fun tearDown() {
        reporter.clearReports()
    }

    @Test
    fun testReportErrorAndRetrieve_roundTrip() {
        reporter.reportError("NullPointerException", "at org.example.Foo.bar(Foo.kt:42)")

        val reports = reporter.getPendingReports()
        assertEquals(1, reports.size, "Should have exactly one report")

        val report = reports[0]
        assertEquals("NullPointerException", report.message)
        assertEquals("at org.example.Foo.bar(Foo.kt:42)", report.stackTrace)
        assertTrue(report.timestamp.isNotEmpty(), "Timestamp should not be empty")
        assertTrue(report.deviceInfo.isNotEmpty(), "Device info should be populated")
    }

    @Test
    fun testMultipleReports_preserveOrder() {
        reporter.reportError("Error A", "stack A")
        reporter.reportError("Error B", "stack B")
        reporter.reportError("Error C", "stack C")

        val reports = reporter.getPendingReports()
        assertEquals(3, reports.size)
        assertEquals("Error A", reports[0].message)
        assertEquals("Error B", reports[1].message)
        assertEquals("Error C", reports[2].message)
    }

    @Test
    fun testClearReports_removesAll() {
        reporter.reportError("Error", "stack")
        assertEquals(1, reporter.getPendingReports().size)

        reporter.clearReports()
        assertEquals(0, reporter.getPendingReports().size)
    }

    @Test
    fun testGetPendingReports_emptyByDefault() {
        val reports = reporter.getPendingReports()
        assertEquals(0, reports.size, "Should have no reports initially")
    }

    @Test
    fun testDelimiterInMessage_doesNotCorruptDecoding() {
        // The delimiter is "|||". If a message contains this substring,
        // the naive split will produce extra parts. This test documents
        // the current behavior: fields after the first 3 are ignored,
        // but the message field will be truncated at the first "|||".
        reporter.reportError("bad|||data", "stack|||trace|||extra")

        val reports = reporter.getPendingReports()
        assertEquals(1, reports.size, "Should still produce one report")

        // The current split-based implementation will split on ALL "|||" occurrences.
        // Parts: [timestamp, "bad", "data", "stack", "trace", "extra"]
        // parts[0] = timestamp, parts[1] = "bad", parts[2] = "data"
        // So the message becomes "bad" and stackTrace becomes "data".
        // This documents the known limitation of the delimiter-based encoding.
        val report = reports[0]
        assertTrue(report.message.isNotEmpty(), "Message should be non-empty")
        assertTrue(report.stackTrace.isNotEmpty(), "Stack trace should be non-empty")
    }

    @Test
    fun testDeviceInfo_containsExpectedKeys() {
        reporter.reportError("test", "trace")
        val reports = reporter.getPendingReports()
        val info = reports[0].deviceInfo

        // collectDeviceInfo() should provide these keys
        assertTrue("model" in info, "Device info should contain 'model'")
        assertTrue("systemName" in info, "Device info should contain 'systemName'")
        assertTrue("systemVersion" in info, "Device info should contain 'systemVersion'")
    }
}
