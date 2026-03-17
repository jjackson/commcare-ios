package org.commcare.app.oracle

import org.commcare.app.platform.PlatformAudioCapture
import org.commcare.app.platform.PlatformBarcodeScanner
import org.commcare.app.platform.PlatformImageCapture
import org.commcare.app.platform.PlatformLocationProvider
import org.commcare.app.platform.PlatformSignatureCapture
import org.commcare.app.viewmodel.QuestionType
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Integration-style oracle test exercising all media/location/barcode platform abstractions.
 * Verifies that all JVM stubs correctly return null and that QuestionType enum covers all types.
 */
class MediaCaptureOracleTest {

    @Test
    fun testAllCaptureStubsReturnNull() {
        val results = mutableListOf<String?>()

        PlatformImageCapture().captureFromCamera { results.add(it) }
        PlatformImageCapture().pickFromGallery { results.add(it) }
        PlatformAudioCapture().startRecording { results.add(it) }
        PlatformSignatureCapture().captureSignature { results.add(it) }
        PlatformBarcodeScanner().scanBarcode { results.add(it) }

        var locResult: Any? = "sentinel"
        PlatformLocationProvider().requestLocation { locResult = it }

        assertTrue(results.all { it == null }, "All JVM media stubs should return null")
        assertNull(locResult, "JVM location stub should return null")
    }

    @Test
    fun testQuestionTypeEnumCoversMediaTypes() {
        val mediaTypes = listOf(
            QuestionType.IMAGE,
            QuestionType.AUDIO,
            QuestionType.SIGNATURE,
            QuestionType.GEOPOINT,
            QuestionType.BARCODE
        )
        for (type in mediaTypes) {
            assertTrue(QuestionType.entries.contains(type), "QuestionType should contain $type")
        }
    }
}
