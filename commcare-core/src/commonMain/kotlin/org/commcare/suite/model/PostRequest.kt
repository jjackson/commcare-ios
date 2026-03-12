package org.commcare.suite.model

import org.javarosa.core.util.ListMultimap

import org.commcare.session.RemoteQuerySessionManager
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.core.util.externalizable.SerializationHelpers
import org.javarosa.xpath.expr.XPathExpression

import org.javarosa.core.util.PlatformUrl
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException

/**
 * Entry config for posting data to a remote server as part of synchronous
 * request transaction.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class PostRequest : Externalizable {
    private var url: PlatformUrl? = null
    private var relevantExpr: XPathExpression? = null
    private var params: List<QueryData>? = null

    constructor()

    constructor(url: PlatformUrl?, relevantExpr: XPathExpression?, params: List<QueryData>?) {
        this.url = url
        this.params = params
        this.relevantExpr = relevantExpr
    }

    fun getUrl(): PlatformUrl? = url

    /**
     * Evaluates parameters for post request
     *
     * @param evalContext        Context params needs to be evaluated in
     * @param includeBlankValues whether to include blank values in the return map
     * @return Evaluated params
     */
    fun getEvaluatedParams(evalContext: EvaluationContext, includeBlankValues: Boolean): ListMultimap<String, String> {
        val evaluatedParams: ListMultimap<String, String> = ListMultimap()
        for (queryData in params!!) {
            val `val` = queryData.getValues(evalContext)
            if (`val`.iterator().hasNext()) {
                evaluatedParams.putAll(queryData.getKey(), `val`)
            } else if (includeBlankValues) {
                evaluatedParams.put(queryData.getKey(), "")
            }
        }
        return evaluatedParams
    }

    fun isRelevant(evalContext: EvaluationContext): Boolean {
        return if (relevantExpr == null) {
            true
        } else {
            val localEvalContext = evalContext.spawnWithCleanLifecycle()
            val evaluatedParams = getEvaluatedParams(localEvalContext, true)
            evaluatedParams.keySet().forEach { key ->
                localEvalContext.setVariable(key, evaluatedParams.get(key).joinToString(" "))
            }
            val re = relevantExpr!!
            val result = RemoteQuerySessionManager.evalXpathExpression(re, localEvalContext)
            "true" == result
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        params = SerializationHelpers.readListPoly(`in`, pf) as List<QueryData>
        url = PlatformUrl(SerializationHelpers.readString(`in`))
        relevantExpr = SerializationHelpers.readNullableTagged(`in`, pf) as XPathExpression?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        SerializationHelpers.writeListPoly(out, params!!)
        SerializationHelpers.writeString(out, url.toString())
        SerializationHelpers.writeNullableTagged(out, relevantExpr)
    }
}
