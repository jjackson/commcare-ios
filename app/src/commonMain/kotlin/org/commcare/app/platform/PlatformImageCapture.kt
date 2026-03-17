package org.commcare.app.platform

/**
 * Platform-specific image capture (camera + gallery).
 * iOS uses UIImagePickerController. JVM stub returns null.
 */
expect class PlatformImageCapture() {
    /**
     * Capture an image from the camera.
     * @param onResult Callback with the file path of the captured image, or null if cancelled.
     */
    fun captureFromCamera(onResult: (String?) -> Unit)

    /**
     * Pick an image from the gallery.
     * @param onResult Callback with the file path of the selected image, or null if cancelled.
     */
    fun pickFromGallery(onResult: (String?) -> Unit)
}
