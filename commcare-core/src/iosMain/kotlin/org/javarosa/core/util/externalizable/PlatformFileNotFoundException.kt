@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

actual open class PlatformFileNotFoundException : PlatformIOException {
    actual constructor() : super()
    actual constructor(message: String?) : super(message)
}
