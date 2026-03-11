@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import java.io.ByteArrayInputStream
import java.io.DataInputStream

actual typealias PlatformDataInputStream = DataInputStream

actual fun createDataInputStream(data: ByteArray): PlatformDataInputStream =
    DataInputStream(ByteArrayInputStream(data))
