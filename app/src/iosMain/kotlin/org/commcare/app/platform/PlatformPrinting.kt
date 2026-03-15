package org.commcare.app.platform

import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputGeneral
import platform.WebKit.WKWebView
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create

/**
 * iOS printing via UIPrintInteractionController.
 */
actual class PlatformPrinting actual constructor() {
    actual fun canPrint(): Boolean {
        return UIPrintInteractionController.isPrintingAvailable()
    }

    actual fun printHtml(html: String, jobTitle: String, onComplete: (Boolean) -> Unit) {
        if (!canPrint()) {
            onComplete(false)
            return
        }

        val printController = UIPrintInteractionController.sharedPrintController()
        val printInfo = UIPrintInfo.printInfo()
        printInfo.setJobName(jobTitle)
        printInfo.setOutputType(UIPrintInfoOutputGeneral)
        printController.printInfo = printInfo

        // Convert HTML string to printable format
        val htmlData = html.encodeToByteArray()
        val nsData = NSData.create(
            bytes = htmlData.toUByteArray().asByteArray().toCValues(),
            length = htmlData.size.toULong()
        )
        printController.printingItem = nsData

        printController.presentAnimated(true) { _, completed, error ->
            onComplete(completed)
        }
    }
}
