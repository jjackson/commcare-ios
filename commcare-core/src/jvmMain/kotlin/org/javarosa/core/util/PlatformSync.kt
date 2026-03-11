package org.javarosa.core.util

actual inline fun <R> platformSynchronized(lock: Any, block: () -> R): R = synchronized(lock, block)
