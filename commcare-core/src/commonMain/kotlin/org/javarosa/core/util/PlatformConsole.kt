package org.javarosa.core.util

/**
 * Cross-platform stderr output.
 * On JVM, delegates to System.err.println().
 * On iOS, uses platform.Foundation.NSLog or println (stderr not directly accessible).
 */
expect fun platformStdErrPrintln(message: String)
