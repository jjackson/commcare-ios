@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util.externalizable

/**
 * Cross-platform equivalent of java.io.FileNotFoundException.
 * On JVM: typealias to java.io.FileNotFoundException.
 * On iOS: custom exception class extending PlatformIOException.
 */
expect open class PlatformFileNotFoundException : PlatformIOException {
    constructor()
    constructor(message: String?)
}
