package org.javarosa.core.util

import platform.Foundation.NSLog

actual fun platformStdErrPrintln(message: String) {
    NSLog("%@", message)
}
