package org.javarosa.core.util

/**
 * Platform-specific high-resolution time source.
 */
expect fun platformNanoTime(): Long
