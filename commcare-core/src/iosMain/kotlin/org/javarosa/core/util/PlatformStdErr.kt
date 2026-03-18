package org.javarosa.core.util

actual fun platformStdErrPrintln(message: String) {
    println("[ERR] $message")
}
