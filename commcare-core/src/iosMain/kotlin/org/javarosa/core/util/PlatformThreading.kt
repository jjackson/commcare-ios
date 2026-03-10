package org.javarosa.core.util

import platform.Foundation.NSThread

actual fun platformIsThreadInterrupted(): Boolean = NSThread.currentThread.isCancelled()

actual fun platformSleep(millis: Long) = NSThread.sleepForTimeInterval(millis / 1000.0)
