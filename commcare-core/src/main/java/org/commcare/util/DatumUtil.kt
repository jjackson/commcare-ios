package org.commcare.util

import org.commcare.suite.model.SessionDatum
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.expr.XPathPathExpr

/**
 * Utils for getting values for datums
 */
object DatumUtil {

    @JvmStatic
    fun getReturnValueFromSelection(
        contextRef: TreeReference, needed: SessionDatum,
        context: EvaluationContext
    ): String {
        return getReturnValueFromSelection(contextRef, needed.getValue()!!, context)
    }

    @JvmStatic
    fun getReturnValueFromSelection(
        contextRef: TreeReference, value: String,
        context: EvaluationContext
    ): String {
        return getReturnValueFromSelection(contextRef, XPathReference.getPathExpr(value), context)
    }

    @JvmStatic
    fun getReturnValueFromSelection(
        contextRef: TreeReference, valueExpr: XPathPathExpr,
        context: EvaluationContext
    ): String {
        val elementRef = valueExpr.getReference()
        val contextualizedRef = elementRef.contextualize(contextRef) ?: return ""
        val element = context.resolveReference(contextualizedRef)

        if (element != null && element.getValue() != null) {
            return element.getValue()!!.uncast().getString() ?: ""
        }
        return ""
    }
}
