package org.javarosa.core.util.externalizable

/**
 * Thrown when trying to create an object during serialization, but object cannot be created.
 */
class CannotCreateObjectException(message: String) : RuntimeException(message)
