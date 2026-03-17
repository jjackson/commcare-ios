package org.commcare.app.platform

/**
 * JVM stub for audio capture — always returns null (unavailable).
 */
actual class PlatformAudioCapture actual constructor() {
    actual fun startRecording(onResult: (String?) -> Unit) {
        onResult(null)
    }

    actual fun stopRecording() {}

    actual fun isRecording(): Boolean = false
}
