@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.UniformTypeIdentifiers.UTTypePlainText
import platform.Foundation.NSURL
import platform.darwin.NSObject

actual class PlatformDocumentPicker actual constructor() {
    private var retainedDelegate: DocumentPickerDelegate? = null

    actual fun pickDocument(onResult: (String?) -> Unit) {
        val types = listOf(UTTypePDF, UTTypeItem, UTTypePlainText)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = types)

        val delegate = DocumentPickerDelegate(onResult) { retainedDelegate = null }
        retainedDelegate = delegate
        picker.delegate = delegate

        val rootVC = getKeyWindow()?.rootViewController
        rootVC?.presentViewController(picker, animated = true, completion = null)
    }

    private fun getKeyWindow(): UIWindow? {
        val scenes = UIApplication.sharedApplication.connectedScenes
        for (scene in scenes) {
            if ((scene as? UIWindowScene)?.activationState ==
                UISceneActivationStateForegroundActive) {
                return (scene as? UIWindowScene)?.keyWindow
            }
        }
        return null
    }
}

private class DocumentPickerDelegate(
    private val onResult: (String?) -> Unit,
    private val onDismiss: () -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(
        controller: UIDocumentPickerViewController,
        didPickDocumentsAtURLs: List<*>
    ) {
        val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL
        val path = url?.path
        onResult(path)
        onDismiss()
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onResult(null)
        onDismiss()
    }
}
