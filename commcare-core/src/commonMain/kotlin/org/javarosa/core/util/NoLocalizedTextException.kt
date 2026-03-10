package org.javarosa.core.util

/**
 * @author Clayton Sims
 */
class NoLocalizedTextException(
    message: String,
    val missingKeyNames: String,
    val localeMissingKey: String
) : RuntimeException(message)
