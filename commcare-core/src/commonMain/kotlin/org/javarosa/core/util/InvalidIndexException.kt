package org.javarosa.core.util

/**
 * Thrown when an index used contains an invalid value
 *
 * @author ctsims
 */
class InvalidIndexException(message: String, val index: String) : RuntimeException(message)
