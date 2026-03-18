package org.commcare.modern.reference

import org.javarosa.core.reference.PrefixedRootFactory
import org.javarosa.core.reference.Reference

/**
 * iOS implementation of HTTP reference factory.
 * Equivalent to JavaHttpRoot on JVM.
 * Handles "http://" and "https://" URI resolution via NSURLSession.
 */
class IosHttpRoot : PrefixedRootFactory(arrayOf("http://", "https://")) {

    override fun factory(terminal: String, URI: String): Reference {
        return IosHttpReference(URI)
    }
}
