package org.javarosa.model.xform

import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.TreeReference
import org.kxml2.io.KXmlSerializer
import java.io.IOException
import java.io.OutputStream

/**
 * A quick rewrite of the basics for writing higher level xml documents straight to
 * output streams.
 *
 * @author Clayton Sims
 */
class DataModelSerializer {

    private val serializer: KXmlSerializer
    private val factory: InstanceInitializationFactory?

    @Throws(IOException::class)
    constructor(stream: OutputStream, factory: InstanceInitializationFactory?) {
        serializer = KXmlSerializer()
        serializer.setOutput(stream, "UTF-8")
        this.factory = factory
    }

    constructor(serializer: KXmlSerializer) {
        this.serializer = serializer
        this.factory = null
    }

    @Throws(IOException::class)
    fun serialize(instance: ExternalDataInstance, base: TreeReference) {
        val specializedInstance = instance.initialize(factory, instance.getInstanceId())
        serialize(specializedInstance, base)
    }

    @Throws(IOException::class)
    fun serialize(instance: DataInstance<*>, base: TreeReference?) {
        // TODO: Namespaces?
        val root: AbstractTreeElement? = if (base == null) {
            instance.getRoot()
        } else {
            instance.resolveReference(base)
        }
        serialize(root!!)
    }

    @Throws(IOException::class)
    fun serialize(root: AbstractTreeElement) {
        serializer.startTag(root.getNamespace(), root.getName())

        serializeAttributes(root)
        for (i in 0 until root.getNumChildren()) {
            val childAt = root.getChildAt(i) as AbstractTreeElement
            serializeNode(childAt)
        }

        serializer.endTag(root.getNamespace(), root.getName())
        serializer.flush()
    }

    @Throws(IOException::class)
    fun serializeNode(instanceNode: AbstractTreeElement) {
        // don't serialize template nodes or non-relevant nodes
        if (!instanceNode.isRelevant || instanceNode.getMult() == TreeReference.INDEX_TEMPLATE) {
            return
        }

        serializer.startTag(instanceNode.getNamespace(), instanceNode.getName())
        serializeAttributes(instanceNode)

        if (instanceNode.getValue() != null) {
            serializer.text(instanceNode.getValue()!!.uncast().getString())
        } else {
            for (i in 0 until instanceNode.getNumChildren()) {
                serializeNode(instanceNode.getChildAt(i) as AbstractTreeElement)
            }
        }

        serializer.endTag(instanceNode.getNamespace(), instanceNode.getName())
    }

    @Throws(IOException::class)
    private fun serializeAttributes(instanceNode: AbstractTreeElement) {
        for (i in 0 until instanceNode.getAttributeCount()) {
            var value = instanceNode.getAttributeValue(i)
            value = value ?: ""
            serializer.attribute(instanceNode.getAttributeNamespace(i), instanceNode.getAttributeName(i), value)
        }
    }
}
