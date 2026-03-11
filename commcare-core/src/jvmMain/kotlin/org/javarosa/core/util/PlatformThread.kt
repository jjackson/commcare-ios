package org.javarosa.core.util

actual object PlatformThread {
    actual fun interrupted(): Boolean = Thread.interrupted()

    actual fun sleep(millis: Long) = Thread.sleep(millis)

    actual fun startThread(block: () -> Unit) {
        Thread(block).start()
    }
}
