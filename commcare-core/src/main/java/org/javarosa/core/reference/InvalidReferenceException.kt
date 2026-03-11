package org.javarosa.core.reference

/**
 * An invalid reference exception is thrown whenever
 * a URI string cannot be resolved to a reference in
 * the current environment.
 *
 * @author ctsims
 */
class InvalidReferenceException(message: String, private val reference: String) : Exception(message) {
    fun getReferenceString(): String = reference
}
