package org.javarosa.core.reference

/**
 * A ReferenceFactory is responsible for knowing how to derive a
 * reference for a range of URI's.
 *
 * @author ctsims
 */
interface ReferenceFactory {
    fun derives(URI: String): Boolean

    @Throws(InvalidReferenceException::class)
    fun derive(URI: String): Reference

    @Throws(InvalidReferenceException::class)
    fun derive(URI: String, context: String): Reference
}
