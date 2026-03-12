package org.javarosa.core.reference

/**
 * A ResourceReferenceFactory provides references of the form
 * `jr://resource/`.
 *
 * @author ctsims
 */
class ResourceReferenceFactory : PrefixedRootFactory(arrayOf("resource")) {
    override fun factory(terminal: String, URI: String): Reference {
        return ResourceReference(terminal)
    }
}
