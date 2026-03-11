package org.javarosa.core.util

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

actual fun platformNormalizeNFD(input: String): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    return (input as NSString).decomposedStringWithCanonicalMapping
}
