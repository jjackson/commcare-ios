@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryRecord
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVEncoderAudioQualityKey
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSURL

actual class PlatformAudioCapture actual constructor() {
    private var recorder: AVAudioRecorder? = null
    private var outputPath: String? = null
    private var pendingResult: ((String?) -> Unit)? = null

    actual fun startRecording(onResult: (String?) -> Unit) {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(AVAudioSessionCategoryRecord, error = null)
        session.setActive(active = true, error = null)

        val path = NSTemporaryDirectory() + NSUUID().UUIDString + ".m4a"
        outputPath = path

        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to 1633772320L, // kAudioFormatMPEG4AAC
            AVSampleRateKey to 44100.0,
            AVNumberOfChannelsKey to 1L,
            AVEncoderAudioQualityKey to 96L // AVAudioQualityHigh = 96
        )

        val url = NSURL.fileURLWithPath(path)
        val audioRecorder = AVAudioRecorder(url, settings, null)
        if (audioRecorder.prepareToRecord() && audioRecorder.record()) {
            recorder = audioRecorder
            pendingResult = onResult
        } else {
            onResult(null)
        }
    }

    actual fun stopRecording() {
        recorder?.stop()
        recorder = null
        val path = outputPath
        outputPath = null
        val callback = pendingResult
        pendingResult = null
        callback?.invoke(path)
    }

    actual fun isRecording(): Boolean = recorder?.isRecording() == true
}
