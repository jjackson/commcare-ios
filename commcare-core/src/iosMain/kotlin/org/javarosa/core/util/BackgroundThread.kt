package org.javarosa.core.util

import kotlin.concurrent.AtomicInt
import platform.Foundation.NSOperationQueue

actual class BackgroundThread actual constructor(private val block: () -> Unit) {
    private val state = AtomicInt(0) // 0=not started, 1=running, 2=completed

    actual fun start() {
        state.value = 1
        NSOperationQueue().addOperationWithBlock {
            try {
                block()
            } finally {
                state.value = 2
            }
        }
    }

    actual val isAlive: Boolean get() = state.value == 1
}
