package org.javarosa.core.model.instance

/**
 * Wrapper class for instances that do not require additional metadata.
 * This applies to instances which are pre-defined by the platform such as
 * the `commcaresession` instance.
 */
open class ConcreteInstanceRoot(
    @JvmField protected var root: AbstractTreeElement?
) : InstanceRoot {

    override fun getRoot(): AbstractTreeElement? {
        return root
    }

    override fun setupNewCopy(instance: ExternalDataInstance) {
        instance.copyFromSource(this)
    }

    companion object {
        @JvmField
        val NULL: InstanceRoot = ConcreteInstanceRoot(null)
    }
}
