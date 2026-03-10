package org.commcare.suite.model

import org.commcare.util.DatumUtil
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

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
                values.add(DatumUtil.getReturnValueFromSelection(node, ref, ec))
            }
        }
        return values
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        _key = ExtUtil.readString(`in`)
        nodeset = ExtUtil.read(`in`, TreeReference::class.java, pf) as TreeReference
        ref = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathPathExpr
        excludeExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, _key)
        ExtUtil.write(out, nodeset)
        ExtUtil.write(out, ExtWrapTagged(ref!!))
        val excl = excludeExpr
        ExtUtil.write(out, ExtWrapNullable(if (excl == null) null else ExtWrapTagged(excl)))
    }
}
