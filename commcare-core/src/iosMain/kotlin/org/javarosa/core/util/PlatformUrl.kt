@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

import org.javarosa.core.util.externalizable.PlatformIOException
import platform.Foundation.NSURL

actual class PlatformUrl actual constructor(spec: String) {
    private val urlString: String

    init {
        if (NSURL.URLWithString(spec) == null) {
            throw PlatformMalformedUrlException("no protocol: $spec")
        }
        urlString = spec
    }

    actual override fun toString(): String = urlString
}

actual open class PlatformMalformedUrlException(message: String? = null) :
    PlatformIOException(message ?: "Malformed URL")
