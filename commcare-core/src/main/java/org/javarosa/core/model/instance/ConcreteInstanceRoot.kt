package org.javarosa.core.model.instance

/**
 * Wrapper class for instances that do not require additional metadata.
 * This applies to instances which are pre-defined by the platform such as
 * the `commcaresession` instance.
 */
open class ConcreteInstanceRoot(
    private var _root: AbstractTreeElement?
) : InstanceRoot {

    override fun getRoot(): AbstractTreeElement? {
        return _root
    }

    override fun setupNewCopy(instance: ExternalDataInstance) {
        instance.copyFromSource(this)
    }

    companion object {
        @kotlin.jvm.JvmField
        val NULL: InstanceRoot = ConcreteInstanceRoot(null)
    }
}
