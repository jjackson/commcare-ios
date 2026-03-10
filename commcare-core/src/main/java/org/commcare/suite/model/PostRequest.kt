package org.commcare.suite.model

import org.javarosa.core.util.ListMultimap
import org.javarosa.core.util.externalizable.PlatformDataInputStream
import org.javarosa.core.util.externalizable.PlatformDataOutputStream

import org.commcare.session.RemoteQuerySessionManager
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.util.externalizable.DeserializationException
import org.javarosa.core.util.externalizable.ExtUtil
import org.javarosa.core.util.externalizable.ExtWrapList
import org.javarosa.core.util.externalizable.ExtWrapNullable
import org.javarosa.core.util.externalizable.ExtWrapTagged
import org.javarosa.core.util.externalizable.Externalizable
import org.javarosa.core.util.externalizable.PrototypeFactory
import org.javarosa.xpath.expr.XPathExpression

import java.io.DataInputStream
import java.io.DataOutputStream
import org.javarosa.core.util.externalizable.PlatformIOException
import java.net.URL

/**
 * Entry config for posting data to a remote server as part of synchronous
 * request transaction.
 *
 * @author Phillip Mates (pmates@dimagi.com).
 */
class PostRequest : Externalizable {
    private var url: URL? = null
    private var relevantExpr: XPathExpression? = null
    private var params: List<QueryData>? = null

    constructor()

    constructor(url: URL?, relevantExpr: XPathExpression?, params: List<QueryData>?) {
        this.url = url
        this.params = params
        this.relevantExpr = relevantExpr
    }

    fun getUrl(): URL? = url

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
                localEvalContext.setVariable(key, java.lang.String.join(" ", evaluatedParams.get(key)))
            }
            val re = relevantExpr!!
            val result = RemoteQuerySessionManager.evalXpathExpression(re, localEvalContext)
            "true" == result
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Throws(PlatformIOException::class, DeserializationException::class)
    override fun readExternal(`in`: PlatformDataInputStream, pf: PrototypeFactory) {
        params = ExtUtil.read(`in`, ExtWrapList(ExtWrapTagged()), pf) as List<QueryData>
        url = URL(ExtUtil.readString(`in`))
        relevantExpr = ExtUtil.read(`in`, ExtWrapNullable(ExtWrapTagged()), pf) as XPathExpression?
    }

    @Throws(PlatformIOException::class)
    override fun writeExternal(out: PlatformDataOutputStream) {
        ExtUtil.write(out, ExtWrapList(params!!, ExtWrapTagged()))
        ExtUtil.writeString(out, url.toString())
        val re = relevantExpr
        ExtUtil.write(out, ExtWrapNullable(if (re == null) null else ExtWrapTagged(re)))
    }
}
