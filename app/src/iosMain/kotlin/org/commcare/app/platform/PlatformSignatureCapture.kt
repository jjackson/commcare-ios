@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIEvent
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UITouch
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

actual class PlatformSignatureCapture actual constructor() {
    actual fun captureSignature(onResult: (String?) -> Unit) {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
            ?.windows?.firstOrNull { (it as? UIWindow)?.isKeyWindow() == true } as? UIWindow
        val rootVc = window?.rootViewController
        if (rootVc == null) {
            onResult(null)
            return
        }

        val sigVc = SignatureViewController(onResult)
        rootVc.presentViewController(sigVc, animated = true, completion = null)
    }
}

/**
 * A line segment from (startX, startY) to (endX, endY).
 */
private data class LineSegment(
    val startX: Double,
    val startY: Double,
    val endX: Double,
    val endY: Double
)

/**
 * Custom UIView subclass that captures touch events and draws signature strokes
 * using Core Graphics.
 */
private class SignatureCanvasView : UIView {

    @kotlinx.cinterop.ObjCObjectBase.OverrideInit
    constructor(frame: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>) : super(frame)

    private val segments = mutableListOf<LineSegment>()
    private var lastX: Double = 0.0
    private var lastY: Double = 0.0

    fun clearSignature() {
        segments.clear()
        setNeedsDisplay()
    }

    fun hasSignature(): Boolean = segments.isNotEmpty()

    override fun touchesBegan(touches: Set<*>, withEvent: UIEvent?) {
        val touch = touches.firstOrNull() as? UITouch ?: return
        touch.locationInView(this).useContents {
            lastX = x
            lastY = y
        }
    }

    override fun touchesMoved(touches: Set<*>, withEvent: UIEvent?) {
        val touch = touches.firstOrNull() as? UITouch ?: return
        val currentX: Double
        val currentY: Double
        touch.locationInView(this).useContents {
            currentX = x
            currentY = y
        }
        segments.add(LineSegment(lastX, lastY, currentX, currentY))
        lastX = currentX
        lastY = currentY
        setNeedsDisplay()
    }

    override fun touchesEnded(touches: Set<*>, withEvent: UIEvent?) {
        // Final point already captured in touchesMoved; nothing extra needed.
    }

    override fun drawRect(rect: kotlinx.cinterop.CValue<platform.CoreGraphics.CGRect>) {
        val ctx = UIGraphicsGetCurrentContext() ?: return
        CGContextSetStrokeColorWithColor(ctx, UIColor.blackColor.CGColor)
        CGContextSetLineWidth(ctx, 2.0)

        for (seg in segments) {
            CGContextMoveToPoint(ctx, seg.startX, seg.startY)
            CGContextAddLineToPoint(ctx, seg.endX, seg.endY)
            CGContextStrokePath(ctx)
        }
    }
}

private class SignatureViewController(
    private val onResult: (String?) -> Unit
) : UIViewController(nibName = null, bundle = null) {

    private lateinit var canvasView: SignatureCanvasView

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.whiteColor

        val boundsWidth: Double
        val boundsHeight: Double
        view.bounds.useContents {
            boundsWidth = size.width
            boundsHeight = size.height
        }

        canvasView = SignatureCanvasView(frame = CGRectMake(0.0, 60.0, boundsWidth, boundsHeight - 120.0))
        canvasView.backgroundColor = UIColor.whiteColor
        view.addSubview(canvasView)

        // Cancel button (top-left)
        val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
        cancelButton.setTitle("Cancel", forState = 0uL)
        cancelButton.setFrame(CGRectMake(10.0, 10.0, 70.0, 40.0))
        cancelButton.addTarget(
            this,
            action = platform.objc.sel_registerName("cancelTapped"),
            forControlEvents = 1uL shl 6 // touchUpInside
        )
        view.addSubview(cancelButton)

        // Clear button (center)
        val clearButton = UIButton.buttonWithType(UIButtonTypeSystem)
        clearButton.setTitle("Clear", forState = 0uL)
        clearButton.setFrame(CGRectMake((boundsWidth - 70.0) / 2.0, 10.0, 70.0, 40.0))
        clearButton.addTarget(
            this,
            action = platform.objc.sel_registerName("clearTapped"),
            forControlEvents = 1uL shl 6
        )
        view.addSubview(clearButton)

        // Done button (top-right)
        val doneButton = UIButton.buttonWithType(UIButtonTypeSystem)
        doneButton.setTitle("Done", forState = 0uL)
        doneButton.setFrame(CGRectMake(boundsWidth - 80.0, 10.0, 70.0, 40.0))
        doneButton.addTarget(
            this,
            action = platform.objc.sel_registerName("doneTapped"),
            forControlEvents = 1uL shl 6
        )
        view.addSubview(doneButton)
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
    fun clearTapped() {
        canvasView.clearSignature()
    }

    @kotlinx.cinterop.ObjCAction
    fun cancelTapped() {
        dismissViewControllerAnimated(true) {
            onResult(null)
        }
    }
}
