package org.javarosa.core.model.instance

/**
 * An Invalid Reference exception is thrown whenever
 * a valid TreeReference is expected by an operation.
 *
 * @author ctsims
 */
class InvalidReferenceException(
    message: String?,
    @JvmField val invalid: TreeReference
) : Exception(message) {

    val invalidReference: TreeReference
        get() = invalid
}
