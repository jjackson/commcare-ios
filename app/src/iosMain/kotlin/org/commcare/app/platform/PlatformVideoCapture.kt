@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.Foundation.NSURL
import platform.darwin.NSObject

actual class PlatformVideoCapture actual constructor() {
    private var retainedDelegate: VideoPickerDelegate? = null

    actual fun captureVideo(onResult: (String?) -> Unit) {
        if (!UIImagePickerController.isSourceTypeAvailable(
                UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
            onResult(null)
            return
        }
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
        picker.mediaTypes = listOf("public.movie")

        val delegate = VideoPickerDelegate(onResult) { retainedDelegate = null }
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

private class VideoPickerDelegate(
    private val onResult: (String?) -> Unit,
    private val onDismiss: () -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        val mediaUrl = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
        val path = mediaUrl?.path
        picker.dismissViewControllerAnimated(true) {
            onResult(path)
            onDismiss()
        }
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true) {
            onResult(null)
            onDismiss()
        }
    }
}
