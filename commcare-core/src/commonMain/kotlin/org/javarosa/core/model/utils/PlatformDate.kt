@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform Date replacement.
 * On JVM, this is a typealias to java.util.Date.
 * On iOS, this is a simple wrapper around epoch milliseconds.
 *
 * Use getTime()/setTime() for cross-platform code.
 * On JVM, Kotlin property syntax (.time) also works via Java getter synthesis.
 */
expect open class PlatformDate {
    constructor()
    constructor(time: Long)
    fun getTime(): Long
    fun setTime(time: Long)
}
