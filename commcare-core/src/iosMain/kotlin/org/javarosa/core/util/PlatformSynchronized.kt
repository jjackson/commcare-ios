package org.javarosa.core.util

import platform.Foundation.NSRecursiveLock

@PublishedApi
internal val globalLocks = HashMap<Int, NSRecursiveLock>()
@PublishedApi
internal val globalLocksGuard = NSRecursiveLock()

actual inline fun <R> platformSynchronized(lock: Any, block: () -> R): R {
    val lockId = lock.hashCode()
    val nsLock = globalLocksGuard.let {
        it.lock()
        try {
            globalLocks.getOrPut(lockId) { NSRecursiveLock() }
        } finally {
            it.unlock()
        }
    }
    nsLock.lock()
    try {
        return block()
    } finally {
        nsLock.unlock()
    }
}
