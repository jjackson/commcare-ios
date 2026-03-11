package org.javarosa.core.util

actual fun platformStdErrPrintln(message: String) {
    System.err.println(message)
}
