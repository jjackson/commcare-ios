package org.javarosa.core.reference

/**
 * PrefixedRootFactory provides a clean way to implement
 * the vast majority of behavior for a reference factory.
 *
 * @author ctsims
 */
abstract class PrefixedRootFactory(roots: Array<String>) : ReferenceFactory {

    @kotlin.jvm.JvmField
    val roots: Array<String> = Array(roots.size) { i ->
        if (roots[i].contains("://")) {
            roots[i]
        } else {
            "jr://" + roots[i]
        }
    }

    @Throws(InvalidReferenceException::class)
    override fun derive(URI: String): Reference {
        for (root in roots) {
            if (URI.contains(root)) {
                return factory(URI.substring(root.length), URI)
            }
        }
        throw InvalidReferenceException(
            "Invalid attempt to derive a reference from a prefixed root. Valid prefixes for this factory are ${roots.contentToString()}",
            URI
        )
    }

    protected abstract fun factory(terminal: String, URI: String): Reference

    @Throws(InvalidReferenceException::class)
    override fun derive(URI: String, context: String): Reference {
        val referenceURI = context.substring(0, context.lastIndexOf('/') + 1) + URI
        return ReferenceManager.instance().DeriveReference(referenceURI)
    }

    override fun derives(URI: String): Boolean {
        for (root in roots) {
            if (URI.contains(root)) {
                return true
            }
        }
        return false
    }
}
