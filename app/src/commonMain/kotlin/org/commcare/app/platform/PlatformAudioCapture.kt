package org.commcare.app.platform

/**
 * Platform-specific audio recording.
 * iOS uses AVAudioRecorder. JVM stub returns null.
 */
expect class PlatformAudioCapture() {
    /**
     * Start recording audio. When complete, calls onResult with the file path.
     * @param onResult Callback with the file path of the recording, or null if cancelled/failed.
     */
    fun startRecording(onResult: (String?) -> Unit)

    /**
     * Stop the current recording.
     */
    fun stopRecording()

    /**
     * Whether a recording is currently in progress.
     */
    fun isRecording(): Boolean
}
