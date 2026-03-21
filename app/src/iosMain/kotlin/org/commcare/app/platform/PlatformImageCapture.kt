@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIApplication
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSData
import platform.Foundation.writeToFile
import platform.darwin.NSObject

actual class PlatformImageCapture actual constructor() {
    // Retain delegate for the duration of the picker — iOS delegate is a weak reference
    private var retainedDelegate: ImagePickerDelegate? = null

    actual fun captureFromCamera(onResult: (String?) -> Unit) {
        launchPicker(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera, onResult)
    }

    actual fun pickFromGallery(onResult: (String?) -> Unit) {
        launchPicker(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary, onResult)
    }

    private fun launchPicker(sourceType: UIImagePickerControllerSourceType, onResult: (String?) -> Unit) {
        if (!UIImagePickerController.isSourceTypeAvailable(sourceType)) {
            onResult(null)
            return
        }

        val picker = UIImagePickerController()
        picker.sourceType = sourceType

        val delegate = ImagePickerDelegate { result ->
            onResult(result)
            retainedDelegate = null
        }
        retainedDelegate = delegate
        picker.delegate = delegate

        val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
        rootVc?.presentViewController(picker, animated = true, completion = null)
    }
}

private class ImagePickerDelegate(
    private val onComplete: (String?) -> Unit
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {

    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        val image = didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage] as? UIImage
        if (image != null) {
            val data = UIImageJPEGRepresentation(image, 0.8)
            if (data != null) {
                val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".jpg"
                (data as NSData).writeToFile(path, atomically = true)
                onComplete(path)
                return
            }
        }
        onComplete(null)
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onComplete(null)
    }
}
