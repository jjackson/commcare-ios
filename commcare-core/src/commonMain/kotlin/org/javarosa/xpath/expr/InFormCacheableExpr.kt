package org.javarosa.xpath.expr

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.ContainsUncacheableExpressionAnalyzer
import org.javarosa.xpath.analysis.ReferencesMainInstanceAnalyzer
import org.javarosa.xpath.analysis.TopLevelContextTypesAnalyzer
import org.javarosa.xpath.analysis.XPathAnalyzable

/**
 * Superclass for an XPathExpression that keeps track of all information related to if it can be
 * cached, and contains wrapper functions for all caching operations.
 *
 * @author Aliza Stone
 */
abstract class InFormCacheableExpr : XPathAnalyzable {

    private var justRetrieved: Any? = null
    var cacheState: CacheableExprState = CacheableExprState()

    internal fun isCached(ec: EvaluationContext): Boolean {
        if (ec.expressionCachingEnabled()) {
            queueUpCachedValue(ec)
            return justRetrieved != null
        }
        return false
    }

    private fun queueUpCachedValue(ec: EvaluationContext) {
        justRetrieved = ec.expressionCacher()!!.getCachedValue(cacheKey(ec))
    }

    /**
     * queueUpCachedValue must always be called first!
     */
    internal fun getCachedValue(): Any? {
        return justRetrieved
    }

    internal fun cache(value: Any?, ec: EvaluationContext) {
        if (ec.expressionCachingEnabled() &&
            expressionIsCacheable(ec) &&
            relevantContextNodesAreCacheable(ec)
        ) {
            if (value != null) {
                ec.expressionCacher()!!.cache(cacheKey(ec), value)
            }
        }
    }

    private fun cacheKey(ec: EvaluationContext): ExpressionCacheKey {
        return ExpressionCacheKey(
            this,
            if (cacheState.contextRefIsRelevant) ec.contextRef else null,
            if (cacheState.originalContextRefIsRelevant) ec.getOriginalContext() else null
        )
    }

    private fun expressionIsCacheable(ec: EvaluationContext): Boolean {
        if (!cacheState.computedCacheability) {
            cacheState.exprIsCacheable = rootExpressionTypeIsCacheable() && fullExpressionIsCacheable(ec)
            cacheState.computedCacheability = true
        }
        return cacheState.exprIsCacheable
    }

    protected open fun rootExpressionTypeIsCacheable(): Boolean {
        return true
    }

    private fun fullExpressionIsCacheable(ec: EvaluationContext): Boolean {
        return try {
            !referencesMainFormInstance(this, ec) &&
                    !containsUncacheableSubExpression(this, ec)
        } catch (e: AnalysisInvalidException) {
            // If the analysis didn't complete then we assume it's not cacheable
            false
        }
    }

    fun relevantContextNodesAreCacheable(ec: EvaluationContext): Boolean {
        if (!cacheState.computedContextTypes) {
            val relevantContextTypes = TopLevelContextTypesAnalyzer().accumulate(this)
            cacheState.contextRefIsRelevant =
                relevantContextTypes != null && relevantContextTypes.contains(TreeReference.CONTEXT_INHERITED)
            cacheState.originalContextRefIsRelevant =
                relevantContextTypes != null && relevantContextTypes.contains(TreeReference.CONTEXT_ORIGINAL)
            cacheState.computedContextTypes = true
        }
        return !(cacheState.contextRefIsRelevant &&
                contextRefIsUncacheableInForm(ec.contextRef)) &&
                !(cacheState.originalContextRefIsRelevant &&
                        contextRefIsUncacheableInForm(ec.getOriginalContext()))
    }

    companion object {
        @Throws(AnalysisInvalidException::class)
        fun referencesMainFormInstance(expr: XPathAnalyzable, ec: EvaluationContext): Boolean {
            return ReferencesMainInstanceAnalyzer(ec).computeResult(expr)
        }

        @Throws(AnalysisInvalidException::class)
        fun containsUncacheableSubExpression(expr: XPathAnalyzable, ec: EvaluationContext): Boolean {
            return ContainsUncacheableExpressionAnalyzer(ec).computeResult(expr)
        }

        /**
         * Why this is true: Since a context ref in an EvaluationContext will always be fully-qualified,
         * its context type will always be either CONTEXT_INSTANCE or CONTEXT_ABSOLUTE. Within a form,
         * CONTEXT_ABSOLUTE always means the context ref is in the main form instance, and is therefore
         * uncacheable, while CONTEXT_INSTANCE always means it is in an external instance, and is
         * therefore cacheable
         */
        private fun contextRefIsUncacheableInForm(contextRef: TreeReference?): Boolean {
            return contextRef != null && contextRef.contextType == TreeReference.CONTEXT_ABSOLUTE
        }
    }
}
