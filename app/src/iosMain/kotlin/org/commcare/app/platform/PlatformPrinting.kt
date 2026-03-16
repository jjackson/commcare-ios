package org.commcare.app.platform

import platform.UIKit.UIPrintInteractionController
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInfoOutputGeneral
import platform.UIKit.UIMarkupTextPrintFormatter
import platform.UIKit.UIApplication

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
        printInfo.outputType = UIPrintInfoOutputGeneral
        printInfo.jobName = jobTitle
        controller.printInfo = printInfo

        val formatter = UIMarkupTextPrintFormatter(markupText = html)
        controller.printFormatter = formatter

        controller.presentAnimated(true) { _, completed, _ ->
            onComplete(completed)
        }
    }
}
