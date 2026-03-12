package org.javarosa.xpath.expr

import org.javarosa.core.io.PlatformOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import org.javarosa.model.xform.DataModelSerializer
import org.javarosa.xml.createXmlSerializer
import org.javarosa.xpath.XPathNodeset
import kotlin.jvm.JvmStatic

/**
 * JVM-only utility for serializing XPath expression results to output streams.
 * Extracted from XPathExpression.companion to allow XPathExpression to move to commonMain.
 */
object XPathResultSerializer {

    @JvmStatic
    @Throws(PlatformIOException::class)
    fun serializeResult(value: Any?, output: PlatformOutputStream) {
        if (value is XPathNodeset && !isLeafNode(value)) {
            serializeElements(value, output)
        } else {
            output.write(FunctionUtils.toString(value).toByteArray(Charsets.UTF_8))
        }
    }

    private fun isLeafNode(value: XPathNodeset): Boolean {
        val refs = value.getReferences()
        if (refs == null || refs.size != 1) {
            return false
        }

        val instance = value.getInstance() ?: return false
        val treeElement = instance.resolveReference(refs[0]) ?: return false
        return treeElement.getNumChildren() == 0
    }

    @Throws(PlatformIOException::class)
    private fun serializeElements(nodeset: XPathNodeset, output: PlatformOutputStream) {
        val serializer = createXmlSerializer(output, "UTF-8")

        val s = DataModelSerializer(serializer)

        val instance = nodeset.getInstance() ?: return
        val refs = nodeset.getReferences() ?: return

        for (ref in refs) {
            val treeElement = instance.resolveReference(ref) ?: continue
            s.serializeNode(treeElement)
        }
        serializer.flush()
    }
}
