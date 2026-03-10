@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Cross-platform IOException replacement.
 * On JVM, this is a typealias to java.io.IOException.
 * On iOS, this is a simple Exception subclass.
 */
expect open class PlatformIOException : Exception {
    constructor()
    constructor(message: String?)
    constructor(message: String?, cause: Throwable?)
}
