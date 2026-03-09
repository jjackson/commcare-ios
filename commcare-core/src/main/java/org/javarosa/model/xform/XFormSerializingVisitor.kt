package org.javarosa.model.xform

import org.javarosa.core.data.IDataPointer
import org.javarosa.core.model.IAnswerDataSerializer
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.IInstanceSerializingVisitor
import org.javarosa.core.services.transport.payload.ByteArrayPayload
import org.javarosa.core.services.transport.payload.DataPointerPayload
import org.javarosa.core.services.transport.payload.IDataPayload
import org.javarosa.core.services.transport.payload.MultiMessagePayload
import org.javarosa.xform.util.XFormAnswerDataSerializer
import org.javarosa.xform.util.XFormSerializer
import org.kxml2.kdom.Document
import org.kxml2.kdom.Element
import org.kxml2.kdom.Node
import java.io.IOException
import java.util.Vector

/**
 * A visitor-esque class which walks a FormInstance and constructs an XML document
 * containing its instance.
 *
 * The XML node elements are constructed in a depth-first manner, consistent with
 * standard XML document parsing.
 *
 * @author Clayton Sims
 */
class XFormSerializingVisitor : IInstanceSerializingVisitor {

    /**
     * The XML document containing the instance that is to be returned
     */
    private var theXmlDoc: Document? = null

    /**
     * The serializer to be used in constructing XML for AnswerData elements
     */
    private var serializer: IAnswerDataSerializer? = null

    /**
     * The root of the xml document which should be included in the serialization
     */
    private var rootRef: TreeReference? = null

    private var dataPointers: Vector<IDataPointer> = Vector()

    private val respectRelevance: Boolean

    constructor() : this(true)

    constructor(respectRelevance: Boolean) {
        this.respectRelevance = respectRelevance
    }

    private fun init() {
        theXmlDoc = null
        dataPointers = Vector()
    }

    @Throws(IOException::class)
    override fun serializeInstance(model: FormInstance): ByteArray {
        return serializeInstance(model, XPathReference("/"))
    }

    @Throws(IOException::class)
    override fun serializeInstance(model: FormInstance, ref: XPathReference): ByteArray {
        init()
        rootRef = DataInstance.unpackReference(ref)
        if (this.serializer == null) {
            this.setAnswerDataSerializer(XFormAnswerDataSerializer())
        }

        model.accept(this)
        return XFormSerializer.getUtfBytesFromDocument(theXmlDoc!!)
    }

    @Throws(IOException::class)
    override fun createSerializedPayload(model: FormInstance): IDataPayload {
        return createSerializedPayload(model, XPathReference("/"))
    }

    @Throws(IOException::class)
    override fun createSerializedPayload(model: FormInstance, ref: XPathReference): IDataPayload {
        init()
        rootRef = DataInstance.unpackReference(ref)
        if (this.serializer == null) {
            this.setAnswerDataSerializer(XFormAnswerDataSerializer())
        }
        model.accept(this)
        // TODO: Did this strip necessary data?
        val form = XFormSerializer.getUtfBytesFromDocument(theXmlDoc!!)
        if (dataPointers.size == 0) {
            return ByteArrayPayload(form, null, IDataPayload.PAYLOAD_TYPE_XML)
        }
        val payload = MultiMessagePayload()
        payload.addPayload(ByteArrayPayload(form, "xml_submission_file", IDataPayload.PAYLOAD_TYPE_XML))
        val en = dataPointers.elements()
        while (en.hasMoreElements()) {
            val pointer = en.nextElement()
            payload.addPayload(DataPointerPayload(pointer))
        }
        return payload
    }

    override fun visit(tree: FormInstance) {
        theXmlDoc = Document()

        var root: TreeElement? = tree.resolveReference(rootRef!!)

        // For some reason resolveReference won't ever return the root, so we'll
        // catch that case and just start at the root.
        if (root == null) {
            root = tree.getRoot()
        }

        if (root != null) {
            theXmlDoc!!.addChild(Node.ELEMENT, serializeNode(root))
        }

        val top = theXmlDoc!!.getElement(0)

        val prefixes = tree.getNamespacePrefixes()
        for (prefix in prefixes) {
            if (prefix != null) {
                top.setPrefix(prefix, tree.getNamespaceURI(prefix))
            }
        }
        if (tree.schema != null) {
            top.setNamespace(tree.schema)
            top.setPrefix("", tree.schema)
        }
    }

    private fun serializeNode(instanceNode: TreeElement): Element? {
        var e = Element() // don't set anything on this element yet, as it might get overwritten

        // don't serialize template nodes or non-relevant nodes
        if ((respectRelevance && !instanceNode.isRelevant) || instanceNode.getMult() == TreeReference.INDEX_TEMPLATE) {
            return null
        }

        if (instanceNode.getValue() != null) {
            val serializedAnswer = serializer!!.serializeAnswerData(instanceNode.getValue(), instanceNode.getDataType())

            if (serializedAnswer is Element) {
                e = serializedAnswer
            } else if (serializedAnswer is String) {
                e = Element()
                e.addChild(Node.TEXT, serializedAnswer)
            } else {
                throw RuntimeException(
                    "Can't handle serialized output for${instanceNode.getValue()}, $serializedAnswer"
                )
            }

            if (serializer!!.containsExternalData(instanceNode.getValue()) == true) {
                val pointers = serializer!!.retrieveExternalDataPointer(instanceNode.getValue())
                if (pointers != null) {
                    for (pointer in pointers) {
                        dataPointers.addElement(pointer)
                    }
                }
            }
        } else {
            // make sure all children of the same tag name are written en bloc
            val childNames = Vector<String>()
            for (i in 0 until instanceNode.getNumChildren()) {
                val childName = instanceNode.getChildAt(i)!!.getName()
                if (!childNames.contains(childName)) {
                    childNames.addElement(childName)
                }
            }

            for (i in 0 until childNames.size) {
                val childName = childNames.elementAt(i)
                val mult = instanceNode.getChildMultiplicity(childName)
                for (j in 0 until mult) {
                    val child = serializeNode(instanceNode.getChild(childName, j)!!)
                    if (child != null) {
                        e.addChild(Node.ELEMENT, child)
                    }
                }
            }
        }

        e.setName(instanceNode.getName())

        // add hard-coded attributes
        for (i in 0 until instanceNode.getAttributeCount()) {
            val namespace = instanceNode.getAttributeNamespace(i)
            val name = instanceNode.getAttributeName(i)
            var value = instanceNode.getAttributeValue(i)
            // is it legal for getAttributeValue() to return null? playing it safe for now and assuming yes
            if (value == null) {
                value = ""
            }
            e.setAttribute(namespace, name, value)
        }
        if (instanceNode.getNamespace() != null) {
            e.setNamespace(instanceNode.getNamespace())
        }

        return e
    }

    override fun setAnswerDataSerializer(ads: IAnswerDataSerializer) {
        this.serializer = ads
    }

    override fun newInstance(): IInstanceSerializingVisitor {
        val modelSerializer = XFormSerializingVisitor()
        modelSerializer.setAnswerDataSerializer(this.serializer!!)
        return modelSerializer
    }
}
