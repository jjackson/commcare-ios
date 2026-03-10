@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.xml

/**
 * Cross-platform XML parser exception replacement.
 * On JVM, this is a typealias to org.xmlpull.v1.XmlPullParserException.
 * On iOS, this is a simple Exception subclass.
 */
expect open class PlatformXmlParserException(message: String) : Exception
