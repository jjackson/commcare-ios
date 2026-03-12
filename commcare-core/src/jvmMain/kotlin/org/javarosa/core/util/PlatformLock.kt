@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package org.javarosa.core.util

actual typealias PlatformLock = java.util.concurrent.locks.ReentrantLock

private fun java.util.concurrent.locks.ReentrantLock.checkLocked(): Boolean = isLocked

actual val PlatformLock.isLocked: Boolean get() = this.checkLocked()
