package org.commcare.app.platform

/**
 * Platform-specific printing support.
 * iOS: UIPrintInteractionController, JVM: stub.
 */
expect class PlatformPrinting() {
    /**
     * Check if printing is available on this device.
     */
    fun canPrint(): Boolean

    /**
     * Print HTML content.
     * @param html HTML string to print
     * @param jobTitle Title for the print job
     * @param onComplete Callback with success/failure
     */
    fun printHtml(html: String, jobTitle: String, onComplete: (Boolean) -> Unit)
}
