package org.javarosa.core.util

actual fun platformMaxMemory(): Long = Runtime.getRuntime().maxMemory()
