package org.javarosa.core.util

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping

actual fun platformNormalizeNFD(input: String): String {
    val nsString = NSString.create(string = input)
    return nsString.decomposedStringWithCanonicalMapping
}
