@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

/**
 * Cross-platform parse exception.
 * On JVM, this is a typealias to java.text.ParseException.
 */
expect open class PlatformParseException(message: String, errorOffset: Int) : Exception
