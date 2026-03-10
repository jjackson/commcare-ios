package org.javarosa.model.xform

import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.PlatformIOException
import java.io.OutputStream
import org.javarosa.xml.JvmXmlSerializer
import org.javarosa.xml.PlatformXmlSerializer

/**
 * A quick rewrite of the basics for writing higher level xml documents straight to
 * output streams.
 *
 * @author Clayton Sims
 */
class DataModelSerializer {

    private val serializer: PlatformXmlSerializer
    private val factory: InstanceInitializationFactory?

    @Throws(PlatformIOException::class)
    constructor(stream: OutputStream, factory: InstanceInitializationFactory?) {
        serializer = JvmXmlSerializer(stream, "UTF-8")
        this.factory = factory
    }

    constructor(serializer: PlatformXmlSerializer) {
        this.serializer = serializer
        this.factory = null
    }

    @Throws(PlatformIOException::class)
    fun serialize(instance: ExternalDataInstance, base: TreeReference?) {
        val specializedInstance = instance.initialize(factory, instance.getInstanceId())
        serialize(specializedInstance, base)
    }

    @Throws(PlatformIOException::class)
    fun serialize(instance: DataInstance<*>, base: TreeReference?) {
        // TODO: Namespaces?
        val root: AbstractTreeElement? = if (base == null) {
            instance.getRoot()
        } else {
            instance.resolveReference(base)
        }
        serialize(root!!)
    }

    @Throws(PlatformIOException::class)
    fun serialize(root: AbstractTreeElement) {
        serializer.startTag(root.getNamespace(), root.getName()!!)

        serializeAttributes(root)
        for (i in 0 until root.getNumChildren()) {
            val childAt = root.getChildAt(i) as AbstractTreeElement
            serializeNode(childAt)
        }

        serializer.endTag(root.getNamespace(), root.getName()!!)
        serializer.flush()
    }

    @Throws(PlatformIOException::class)
    fun serializeNode(instanceNode: AbstractTreeElement) {
        // don't serialize template nodes or non-relevant nodes
        if (!instanceNode.isRelevant || instanceNode.getMult() == TreeReference.INDEX_TEMPLATE) {
            return
        }

        serializer.startTag(instanceNode.getNamespace(), instanceNode.getName()!!)
        serializeAttributes(instanceNode)

        if (instanceNode.getValue() != null) {
            serializer.text(instanceNode.getValue()!!.uncast().getString()!!)
        } else {
            for (i in 0 until instanceNode.getNumChildren()) {
                serializeNode(instanceNode.getChildAt(i) as AbstractTreeElement)
            }
        }

        serializer.endTag(instanceNode.getNamespace(), instanceNode.getName()!!)
    }

    @Throws(PlatformIOException::class)
    private fun serializeAttributes(instanceNode: AbstractTreeElement) {
        for (i in 0 until instanceNode.getAttributeCount()) {
            var value = instanceNode.getAttributeValue(i)
            value = value ?: ""
            serializer.attribute(instanceNode.getAttributeNamespace(i), instanceNode.getAttributeName(i)!!, value)
        }
    }
}
