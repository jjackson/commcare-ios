@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.UIKit.UIViewController
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.CoreGraphics.CGRectMake
import platform.darwin.NSObject
import platform.darwin.dispatch_get_main_queue

actual class PlatformBarcodeScanner actual constructor() {
    actual fun scanBarcode(onResult: (String?) -> Unit) {
        val rootVc = UIApplication.sharedApplication.keyWindow?.rootViewController
        if (rootVc == null) {
            onResult(null)
            return
        }

        val scannerVc = BarcodeScannerViewController(onResult)
        rootVc.presentViewController(scannerVc, animated = true, completion = null)
    }
}

private class BarcodeScannerViewController(
    private val onResult: (String?) -> Unit
) : UIViewController(nibName = null, bundle = null) {

    private var captureSession: AVCaptureSession? = null

    override fun viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor.blackColor

        val session = AVCaptureSession()
        val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        if (device == null) {
            dismissViewControllerAnimated(true) { onResult(null) }
            return
        }

        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, error = null)
        if (input == null || !session.canAddInput(input)) {
            dismissViewControllerAnimated(true) { onResult(null) }
            return
        }
        session.addInput(input)

        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) {
            dismissViewControllerAnimated(true) { onResult(null) }
            return
        }
        session.addOutput(output)

        val delegate = MetadataDelegate(this)
        output.setMetadataObjectsDelegate(delegate, queue = dispatch_get_main_queue())
        output.metadataObjectTypes = listOf(
            AVMetadataObjectTypeQRCode,
            AVMetadataObjectTypeEAN13Code,
            AVMetadataObjectTypeEAN8Code,
            AVMetadataObjectTypeCode128Code,
            AVMetadataObjectTypeCode39Code,
            AVMetadataObjectTypeUPCECode
        )

        val previewLayer = AVCaptureVideoPreviewLayer(session = session)
        previewLayer.setFrame(view.bounds)
        previewLayer.videoGravity = AVLayerVideoGravityResizeAspectFill
        view.layer.addSublayer(previewLayer)

        // Cancel button
        val cancelButton = UIButton.buttonWithType(UIButtonTypeSystem)
        cancelButton.setTitle("Cancel", forState = 0u)
        cancelButton.setTitleColor(UIColor.whiteColor, forState = 0u)
        cancelButton.setFrame(CGRectMake(10.0, 40.0, 70.0, 40.0))
        cancelButton.addTarget(this, action = platform.objc.sel_registerName("cancelTapped"), forControlEvents = 1u shl 6)
        view.addSubview(cancelButton)

        captureSession = session
        session.startRunning()
    }

    fun handleBarcode(value: String) {
        captureSession?.stopRunning()
        dismissViewControllerAnimated(true) { onResult(value) }
    }

    @kotlinx.cinterop.ObjCAction
    fun cancelTapped() {
        captureSession?.stopRunning()
        dismissViewControllerAnimated(true) { onResult(null) }
    }

    override fun viewWillDisappear(animated: Boolean) {
        super.viewWillDisappear(animated)
        captureSession?.stopRunning()
    }
}

private class MetadataDelegate(
    private val controller: BarcodeScannerViewController
) : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection
    ) {
        val obj = didOutputMetadataObjects.firstOrNull() as? AVMetadataMachineReadableCodeObject
        val value = obj?.stringValue
        if (value != null) {
            controller.handleBarcode(value)
        }
    }
}
