@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

actual typealias PlatformDataOutputStream = DataOutputStream

actual fun serializeToBytes(block: (PlatformDataOutputStream) -> Unit): ByteArray {
    val baos = ByteArrayOutputStream()
    val dos = DataOutputStream(baos)
    block(dos)
    dos.flush()
    return baos.toByteArray()
}
