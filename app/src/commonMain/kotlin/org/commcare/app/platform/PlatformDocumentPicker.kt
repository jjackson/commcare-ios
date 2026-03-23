package org.commcare.app.platform

/**
 * Platform-specific document picker for file uploads.
 * iOS uses UIDocumentPickerViewController. JVM stub returns null.
 */
expect class PlatformDocumentPicker() {
    /**
     * Open a document picker for supported file types (PDF, Word, Excel, text).
     * @param onResult Callback with the file path of the selected document, or null if cancelled.
     */
    fun pickDocument(onResult: (String?) -> Unit)
}
