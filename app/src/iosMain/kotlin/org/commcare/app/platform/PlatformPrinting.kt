package org.commcare.app.platform

import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIMarkupTextPrintFormatter

actual class PlatformPrinting actual constructor() {
    actual fun canPrint(): Boolean {
        return UIPrintInteractionController.isPrintingAvailable()
    }

    actual fun printHtml(html: String, jobTitle: String, onComplete: (Boolean) -> Unit) {
        if (!canPrint()) {
            onComplete(false)
            return
        }

        val controller = UIPrintInteractionController.sharedPrintController()
        val printInfo = UIPrintInfo.printInfo()
        // UIPrintInfoOutputGeneral = 0 (general-purpose output)
        printInfo.outputType = 0L
        printInfo.jobName = jobTitle
        controller.printInfo = printInfo

        val formatter = UIMarkupTextPrintFormatter(markupText = html)
        controller.printFormatter = formatter

        controller.presentAnimated(true) { _, completed, _ ->
            onComplete(completed)
        }
    }
}
