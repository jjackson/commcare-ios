package org.commcare.app.oracle

import org.commcare.app.platform.PlatformAudioCapture
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Oracle tests for PlatformAudioCapture.
 * JVM stub always returns null — verifies the API contract.
 */
class AudioCaptureOracleTest {

    @Test
    fun testStartRecordingReturnsNull() {
        val capture = PlatformAudioCapture()
        var result: String? = "not-called"
        capture.startRecording { result = it }
        assertNull(result, "JVM stub should return null for audio recording")
    }

    @Test
    fun testIsRecordingReturnsFalse() {
        val capture = PlatformAudioCapture()
        assertFalse(capture.isRecording(), "JVM stub should never be recording")
    }

    @Test
    fun testStopRecordingDoesNotThrow() {
        val capture = PlatformAudioCapture()
        capture.stopRecording() // Should not throw
    }
}
