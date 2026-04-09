package org.commcare.app.engine

import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.model.xform.DataModelSerializer
import org.javarosa.xml.PlatformXmlSerializer
import org.javarosa.xml.createXmlSerializer

/**
 * Cross-platform form instance serializer.
 * Uses DataModelSerializer + PlatformXmlSerializer to produce XML bytes
 * from a completed FormDef, working on both JVM and iOS.
 */
object FormSerializer {

    /**
     * Serialize a completed form's instance data to XML string.
     *
     * Writes the root element ourselves so we can include the form's xmlns
     * as a direct attribute, then delegates child-node serialization to
     * DataModelSerializer. The form's xmlns lives on `FormInstance.schema`,
     * not on the root `AbstractTreeElement`'s namespace field, so the stock
     * `DataModelSerializer.serialize(root)` path drops it entirely —
     * producing XML that HQ's receiver rejects with HTTP 422 (missing form
     * xmlns) or 500 (missing case namespace for child elements). See #394
     * (form submission root cause trail).
     *
     * Uses a direct `attribute("xmlns", schema)` call instead of
     * `serializer.setPrefix("", schema)` because kxml2 (the JVM impl)
     * throws `IllegalStateException: Cannot set default namespace for
     * elements in no namespace` when a subsequent `startTag` is called with
     * an empty namespace — which DataModelSerializer does for child nodes
     * whose TreeElement has no stored namespace.
     */
    fun serializeForm(formDef: FormDef): String {
        val instance = formDef.getInstance() ?: throw IllegalStateException("No form instance")
        val serializer = createXmlSerializer()
        val root = instance.getRoot()
        val xmlns = instance.schema

        // Open the root element ourselves so we can inject xmlns.
        serializer.startTag("", root.getName()!!)
        if (!xmlns.isNullOrEmpty()) {
            serializer.attribute("", "xmlns", xmlns)
        }
        // Copy existing attributes from the root (uiVersion, version, name).
        for (i in 0 until root.getAttributeCount()) {
            val value = root.getAttributeValue(i) ?: ""
            serializer.attribute(
                root.getAttributeNamespace(i) ?: "",
                root.getAttributeName(i)!!,
                value
            )
        }

        // Delegate child serialization to DataModelSerializer's serializeNode,
        // which handles nested namespaces, skip logic, and attributes.
        val dms = DataModelSerializer(serializer)
        for (i in 0 until root.getNumChildren()) {
            val child = root.getChildAt(i) as AbstractTreeElement
            dms.serializeNode(child)
        }

        serializer.endTag("", root.getName()!!)
        serializer.flush()
        return serializer.toByteArray().decodeToString()
    }
}
