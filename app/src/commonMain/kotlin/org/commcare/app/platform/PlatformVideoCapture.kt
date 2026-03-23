package org.commcare.app.platform

/**
 * Platform-specific video capture.
 * iOS uses UIImagePickerController with .movie media type. JVM stub returns null.
 */
expect class PlatformVideoCapture() {
    /**
     * Record a video from the camera.
     * @param onResult Callback with the file path of the recorded video, or null if cancelled.
     */
    fun captureVideo(onResult: (String?) -> Unit)
}
