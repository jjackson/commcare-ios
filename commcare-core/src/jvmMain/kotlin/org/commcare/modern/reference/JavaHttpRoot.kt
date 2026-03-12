package org.commcare.modern.reference

import org.javarosa.core.reference.PrefixedRootFactory
import org.javarosa.core.reference.Reference

/**
 * @author ctsims
 */
class JavaHttpRoot : PrefixedRootFactory(arrayOf("http://", "https://")) {

    override fun factory(terminal: String, URI: String): Reference {
        return JavaHttpReference(URI)
    }
}
