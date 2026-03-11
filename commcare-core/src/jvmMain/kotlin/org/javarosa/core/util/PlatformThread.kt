package org.javarosa.core.util

actual fun platformIsInterrupted(): Boolean = Thread.interrupted()

actual fun platformSleep(millis: Long) {
    try {
        Thread.sleep(millis)
    } catch (_: InterruptedException) {
    }
}

actual fun platformStartCrashThread(exception: RuntimeException) {
    Thread { throw exception }.start()
}
