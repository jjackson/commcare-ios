@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Cross-platform URL abstraction.
 * On JVM, this is a typealias to java.net.URL.
 * On iOS, this wraps a validated URL string.
 */
expect class PlatformUrl(spec: String) {
    override fun toString(): String
}

/**
 * Exception thrown when a URL string is malformed.
 * On JVM, this is a typealias to java.net.MalformedURLException.
 */
expect open class PlatformMalformedUrlException : PlatformIOException
