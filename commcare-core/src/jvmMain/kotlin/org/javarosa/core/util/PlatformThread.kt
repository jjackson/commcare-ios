package org.javarosa.core.util

actual fun platformIsInterrupted(): Boolean = Thread.interrupted()
