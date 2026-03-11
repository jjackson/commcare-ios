@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

import platform.Foundation.NSRecursiveLock

actual class PlatformLock actual constructor() {
    private val lock = NSRecursiveLock()

    actual fun lock() = lock.lock()
    actual fun unlock() = lock.unlock()
}
