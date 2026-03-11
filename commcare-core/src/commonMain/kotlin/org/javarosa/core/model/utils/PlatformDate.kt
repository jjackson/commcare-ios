@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform date representation backed by milliseconds since epoch.
 * On JVM, typealiased to java.util.Date for perfect backward compatibility.
 * On iOS, a simple wrapper around millis.
 */
expect class PlatformDate() {
    constructor(date: Long)
    fun getTime(): Long
    fun setTime(time: Long)
}
