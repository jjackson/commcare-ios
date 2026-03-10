@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.model.utils

/**
 * Cross-platform Date replacement.
 * On JVM, this is a typealias to java.util.Date.
 * On iOS, this is a simple wrapper around epoch milliseconds.
 *
 * Access `.time` (epoch millis) via platform-specific property:
 * - JVM: synthesized from Date.getTime()/setTime()
 * - iOS: declared directly as var
 */
expect open class PlatformDate {
    /** Create a Date representing the current time. */
    constructor()

    /** Create a Date from milliseconds since Unix epoch. */
    constructor(time: Long)

    /** Get milliseconds since Unix epoch. */
    fun getTime(): Long

    /** Set milliseconds since Unix epoch. */
    fun setTime(time: Long)
}
