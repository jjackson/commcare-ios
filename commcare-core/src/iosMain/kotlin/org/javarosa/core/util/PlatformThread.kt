package org.javarosa.core.util

actual object PlatformThread {
    actual fun interrupted(): Boolean {
        // iOS doesn't have thread interruption in the same way as JVM.
        // Always return false — cooperative cancellation on iOS will use
        // a different mechanism if needed.
        return false
    }

    actual fun sleep(millis: Long) {
        platform.posix.usleep((millis * 1000).toUInt())
    }

    actual fun startThread(block: () -> Unit) {
        // On iOS, use a simple dispatch to background
        // For crash handling, just execute inline
        block()
    }
}
