@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import java.io.ByteArrayOutputStream
import java.io.OutputStream

actual typealias PlatformOutputStream = OutputStream

actual fun createByteArrayOutputStream(): PlatformOutputStream =
    ByteArrayOutputStream()

actual fun byteArrayOutputStreamToBytes(stream: PlatformOutputStream): ByteArray =
    (stream as ByteArrayOutputStream).toByteArray()
