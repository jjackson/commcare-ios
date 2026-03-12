package org.commcare.modern.reference

import org.javarosa.core.reference.PrefixedRootFactory
import org.javarosa.core.reference.Reference
import kotlin.jvm.JvmField

/**
 * @author ctsims
 */
class JavaFileRoot(
    @JvmField val localRoot: String
) : PrefixedRootFactory(arrayOf("file")) {

    override fun factory(terminal: String, URI: String): Reference {
        return JavaFileReference(localRoot, terminal)
    }
}
