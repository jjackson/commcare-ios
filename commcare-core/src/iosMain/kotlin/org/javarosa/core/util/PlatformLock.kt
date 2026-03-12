@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

import platform.Foundation.NSRecursiveLock

actual class PlatformLock actual constructor() {
    private val lock = NSRecursiveLock()

    internal var _locked = false

    actual fun lock() { lock.lock(); _locked = true }
    actual fun unlock() { _locked = false; lock.unlock() }
}

actual val PlatformLock.isLocked: Boolean get() = this._locked
