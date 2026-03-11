package org.javarosa.core.util

actual fun platformIsInterrupted(): Boolean = false

actual fun platformSleep(millis: Long) {
    platform.posix.usleep((millis * 1000u).toUInt())
}

actual fun platformStartCrashThread(exception: RuntimeException) {
    // iOS is single-threaded; just throw directly
    throw exception
}
