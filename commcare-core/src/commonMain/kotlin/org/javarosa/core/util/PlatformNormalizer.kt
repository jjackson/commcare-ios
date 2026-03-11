package org.javarosa.core.util

/**
 * Cross-platform Unicode normalization.
 * On JVM, delegates to java.text.Normalizer.
 * On iOS, delegates to Foundation string normalization.
 */
expect fun platformNormalizeNFD(input: String): String
