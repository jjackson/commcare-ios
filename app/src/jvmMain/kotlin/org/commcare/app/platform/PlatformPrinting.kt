package org.commcare.app.platform

/**
 * JVM stub for printing — always unavailable.
 */
actual class PlatformPrinting actual constructor() {
    actual fun canPrint(): Boolean = false

    actual fun printHtml(html: String, jobTitle: String, onComplete: (Boolean) -> Unit) {
        onComplete(false)
    }
}
