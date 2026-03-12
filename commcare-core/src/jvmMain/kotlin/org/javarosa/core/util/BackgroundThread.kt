package org.javarosa.core.util

actual class BackgroundThread actual constructor(block: () -> Unit) {
    private val thread = Thread(block)
    actual fun start() = thread.start()
    actual val isAlive: Boolean get() = thread.isAlive
}
