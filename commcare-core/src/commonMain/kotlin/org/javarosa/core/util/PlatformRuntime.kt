package org.javarosa.core.util

/**
 * Returns the maximum amount of memory available to the runtime in bytes.
 * Returns -1 if unknown.
 */
expect fun platformMaxMemory(): Long
