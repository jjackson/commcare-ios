@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.io

import java.io.ByteArrayInputStream
import java.io.InputStream

actual typealias PlatformInputStream = InputStream

actual fun createByteArrayInputStream(data: ByteArray): PlatformInputStream =
    ByteArrayInputStream(data)
