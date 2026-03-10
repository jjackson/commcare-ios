package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.Collections

/**
 * Data class for single value query data elements
 * ```
 *  <data
 *    key="device_id"
 *    ref="instance('session')/session/context/deviceid"
 *    exclude="true()"
 * />
 * ```
 *
 * The `exclude` attribute is optional.
 */
class ValueQueryData : QueryData {
    private var _key: String? = null
    private var ref: XPathExpression? = null
    private var excludeExpr: XPathExpression? = null

    constructor()

    constructor(key: String?, ref: XPathExpression?, excludeExpr: XPathExpression?) {
        this._key = key
        this.ref = ref
        this.excludeExpr = excludeExpr
    }

    override fun getKey(): String = _key!!

    override fun getValues(context: EvaluationContext): Iterable<String> {
        return if (excludeExpr == null || !(excludeExpr!!.eval(context) as Boolean)) {
            Collections.singletonList(FunctionUtils.toString(ref!!.eval(context)))
        } else {
            Collections.emptyList()
        }
    }

    @Throws(IOException::class, DeserializationException::class)
    override fun readExternal(`in`: DataInputStream, pf: PrototypeFactory) {
        _key = ExtUtil.readString(`in`)
        ref = ExtUtil.read(`in`, ExtWrapTagged(), pf) as XPathExpression
        excludeExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
    }

    @Throws(IOException::class)
    override fun writeExternal(out: DataOutputStream) {
        ExtUtil.writeString(out, _key)
        ExtUtil.write(out, ExtWrapTagged(ref!!))
        val excl = excludeExpr
        ExtUtil.write(out, ExtWrapNullable(if (excl == null) null else ExtWrapTagged(excl)))
    }
}
