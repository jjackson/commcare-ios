package org.commcare.suite.model

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression

import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

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
            listOf(FunctionUtils.toString(ref!!.eval(context)))
        } else {
            emptyList()
        }
    }

    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        _key = SerializationHelpers.readString(`in`)
        ref = SerializationHelpers.readTagged(`in`, pf) as XPathExpression
        excludeExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeString(out, _key!!)
        SerializationHelpers.writeTagged(out, ref!!)
        SerializationHelpers.writeNullableTagged(out, excludeExpr)
    }
}
