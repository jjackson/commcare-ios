@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView
import platform.UIKit.UIColor
import platform.UIKit.UIViewController
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSData
import platform.Foundation.writeToFile

actual class PlatformSignatureCapture actual constructor() {
    actual fun captureSignature(onResult: (String?) -> Unit) {
        val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (rootVc == null) {
            onResult(null)
            return
        }

        val sigVc = SignatureViewController(onResult)
        rootVc.presentViewController(sigVc, animated = true, completion = null)
    }
}

private class SignatureViewController(
    private val onResult: (String?) -> Unit
) : UIViewController(nibName = null, bundle = null) {

    private lateinit var canvasView: UIView

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.whiteColor

        val boundsWidth: Double
        val boundsHeight: Double
        view.bounds.useContents {
            boundsWidth = size.width
            boundsHeight = size.height
        }

        canvasView = UIView(frame = CGRectMake(0.0, 60.0, boundsWidth, boundsHeight - 120.0))
        canvasView.backgroundColor = UIColor.whiteColor
        view.addSubview(canvasView)

        val doneButton = UIButton.buttonWithType(UIButtonTypeSystem)
        doneButton.setTitle("Done", forState = 0u)
        doneButton.setFrame(CGRectMake(boundsWidth - 80.0, 10.0, 70.0, 40.0))
        doneButton.addTarget(this, action = platform.objc.sel_registerName("doneTapped"), forControlEvents = 1u shl 6) // touchUpInside
        view.addSubview(doneButton)

        val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
        cancelButton.setTitle("Cancel", forState = 0u)
        cancelButton.setFrame(CGRectMake(10.0, 10.0, 70.0, 40.0))
        cancelButton.addTarget(this, action = platform.objc.sel_registerName("cancelTapped"), forControlEvents = 1u shl 6)
        view.addSubview(cancelButton)
    }

    @kotlinx.cinterop.ObjCAction
    fun doneTapped() {
        // Render canvas to image
        val canvasWidth: Double
        val canvasHeight: Double
        canvasView.bounds.useContents {
            canvasWidth = size.width
            canvasHeight = size.height
        }
        val size = CGSizeMake(canvasWidth, canvasHeight)
        UIGraphicsBeginImageContextWithOptions(size, false, 0.0)
        canvasView.drawViewHierarchyInRect(canvasView.bounds, afterScreenUpdates = true)
        val image = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        dismissViewControllerAnimated(true) {
            if (image != null) {
                val data = UIImagePNGRepresentation(image)
                if (data != null) {
                    val filePath = NSTemporaryDirectory() + NSUUID().UUIDString + ".png"
                    (data as NSData).writeToFile(filePath, atomically = true)
                    onResult(filePath)
                    return@dismissViewControllerAnimated
                }
            }
            onResult(null)
        }
    }

    @kotlinx.cinterop.ObjCAction
    fun cancelTapped() {
        dismissViewControllerAnimated(true) {
            onResult(null)
        }
    }
}
