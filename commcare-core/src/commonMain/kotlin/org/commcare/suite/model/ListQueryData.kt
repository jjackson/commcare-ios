package org.commcare.suite.model

import org.commcare.util.DatumUtil
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Data class for list query data elements
 *
 * ```
 * <data
 *   key="case_id_list">
 *   nodeset="instance('selected-cases')/session-data/value"
 *   exclude="count(instance('casedb')/casedb/case[@case_id = current()/.]) = 1"
 *   ref="."
 * />
 * ```
 *
 * The `exclude` attribute is optional.
 */
class ListQueryData : QueryData {
    private var _key: String? = null
    private var nodeset: TreeReference? = null
    private var excludeExpr: XPathExpression? = null
    private var ref: XPathPathExpr? = null

    constructor()

    constructor(key: String?, nodeset: TreeReference?, excludeExpr: XPathExpression?, ref: XPathPathExpr?) {
        this._key = key
        this.nodeset = nodeset
        this.excludeExpr = excludeExpr
        this.ref = ref
    }

    override fun getKey(): String = _key!!

    override fun getValues(ec: EvaluationContext): Iterable<String> {
        val values = ArrayList<String>()
        val result = ec.expandReference(nodeset!!)
        for (node in result!!) {
            val temp = EvaluationContext(ec, node)
            if (excludeExpr == null || !(excludeExpr!!.eval(temp) as Boolean)) {
                values.add(DatumUtil.getReturnValueFromSelection(node, ref!!, ec))
            }
        }
        return values
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        _key = SerializationHelpers.readString(`in`)
        nodeset = SerializationHelpers.readExternalizable(`in`, pf) { TreeReference() }
        ref = SerializationHelpers.readTagged(`in`, pf) as XPathPathExpr
        excludeExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, _key ?: "")
        SerializationHelpers.write(out, nodeset!!)
        SerializationHelpers.writeTagged(out, ref!!)
        SerializationHelpers.writeNullableTagged(out, excludeExpr)
    }
}
