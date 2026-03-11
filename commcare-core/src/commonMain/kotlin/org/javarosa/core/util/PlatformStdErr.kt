package org.javarosa.core.util

/**
 * Platform-specific stderr output.
 *
 * On JVM: delegates to System.err.println().
 * On iOS: delegates to NSLog or platform.posix.
 */
expect fun platformStdErrPrintln(message: String)
