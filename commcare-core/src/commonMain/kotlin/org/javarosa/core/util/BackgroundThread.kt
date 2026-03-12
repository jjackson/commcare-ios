package org.javarosa.core.util

/**
 * A background task that can be checked for completion.
 * On JVM: wraps java.lang.Thread.
 * On iOS: runs synchronously (real threading can be added when needed).
 */
expect class BackgroundThread(block: () -> Unit) {
    fun start()
    val isAlive: Boolean
}
