@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

/**
 * Cross-platform reentrant lock.
 * On JVM, this is a typealias to java.util.concurrent.locks.ReentrantLock.
 * On iOS, this wraps NSRecursiveLock.
 */
expect class PlatformLock() {
    fun lock()
    fun unlock()
}

expect val PlatformLock.isLocked: Boolean
