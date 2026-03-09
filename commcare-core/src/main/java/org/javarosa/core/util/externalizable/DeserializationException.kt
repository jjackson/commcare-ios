package org.javarosa.core.util.externalizable

/**
 * Thrown when trying to create an object during serialization, but object cannot be created because:
 *
 * 1) We don't know what object to create
 *
 * @author Clayton Sims
 */
class DeserializationException : Exception {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
