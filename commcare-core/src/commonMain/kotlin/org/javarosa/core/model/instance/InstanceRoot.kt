package org.javarosa.core.model.instance

/**
 * Wrapper class used to hold the root element of an ExternalDataInstance
 * along with any other metadata about the instance.
 */
interface InstanceRoot {
    fun getRoot(): AbstractTreeElement?

    fun setupNewCopy(instance: ExternalDataInstance)
}
