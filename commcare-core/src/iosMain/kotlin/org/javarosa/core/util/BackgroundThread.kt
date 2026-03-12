package org.javarosa.core.util

/**
 * iOS implementation runs the block synchronously.
 * Real threading support can be added when needed.
 */
actual class BackgroundThread actual constructor(private val block: () -> Unit) {
    private var completed = false

    actual fun start() {
        block()
        completed = true
    }

    actual val isAlive: Boolean get() = !completed
}
