@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGPoint
import platform.UIKit.UIView
import platform.UIKit.UIColor
import platform.UIKit.UIViewController
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIApplication
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIBezierPath
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
    private val path = UIBezierPath()

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.whiteColor

        val bounds = view.bounds
        canvasView = UIView(frame = CGRectMake(0.0, 60.0, bounds.size.width, bounds.size.height - 120.0))
        canvasView.backgroundColor = UIColor.whiteColor
        view.addSubview(canvasView)

        val doneButton = UIButton.buttonWithType(UIButtonTypeSystem)
        doneButton.setTitle("Done", forState = 0u)
        doneButton.frame = CGRectMake(bounds.size.width - 80.0, 10.0, 70.0, 40.0)
        doneButton.addTarget(this, action = platform.objc.sel_registerName("doneTapped"), forControlEvents = 1u shl 6) // touchUpInside
        view.addSubview(doneButton)

        val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
        cancelButton.setTitle("Cancel", forState = 0u)
        cancelButton.frame = CGRectMake(10.0, 10.0, 70.0, 40.0)
        cancelButton.addTarget(this, action = platform.objc.sel_registerName("cancelTapped"), forControlEvents = 1u shl 6)
        view.addSubview(cancelButton)
    }

    @kotlinx.cinterop.ObjCAction
    fun doneTapped() {
        // Render canvas to image
        val size = canvasView.bounds.size
        UIGraphicsBeginImageContext(size)
        canvasView.layer.renderInContext(UIGraphicsGetCurrentContext()!!)
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
