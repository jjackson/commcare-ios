package org.commcare.app.platform

/**
 * iOS printing stub.
 * Full implementation requires UIPrintInteractionController integration.
 */
actual class PlatformPrinting actual constructor() {
    actual fun canPrint(): Boolean {
        // TODO: Use UIPrintInteractionController.isPrintingAvailable()
        return false
    }

    actual fun printHtml(html: String, jobTitle: String, onComplete: (Boolean) -> Unit) {
        onComplete(false)
    }
}
