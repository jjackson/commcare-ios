package org.javarosa.core.util

actual fun platformIsThreadInterrupted(): Boolean = Thread.interrupted()

actual fun platformSleep(millis: Long) = Thread.sleep(millis)
