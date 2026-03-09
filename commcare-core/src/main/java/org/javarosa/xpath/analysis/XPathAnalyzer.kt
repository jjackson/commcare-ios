package org.javarosa.xpath.analysis

import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.xpath.expr.XPathFuncExpr

/**
 * An XPathAnalyzer is an object that can perform static analysis of any XPathAnalyzable
 * (an XPathExpression or TreeReference) to ascertain specific semantic information about the
 * raw content of the expression string itself
 *
 * @author Aliza Stone
 */
abstract class XPathAnalyzer {

    @JvmField
    internal var originalContextRef: TreeReference? = null
    @JvmField
    internal var contextRef: TreeReference? = null
    @JvmField
    protected var subAnalyzers: MutableList<XPathAnalyzer> = ArrayList()
    @JvmField
    protected var isSubAnalyzer: Boolean = false
    @JvmField
    protected var shortCircuit: Boolean = false

    protected fun setContext(context: EvaluationContext) {
        setContext(context.contextRef, context.getOriginalContext())
    }

    protected fun setContext(contextRef: TreeReference?) {
        setContext(contextRef, null)
    }

    protected fun setContext(contextRef: TreeReference?, originalContextRef: TreeReference?) {
        this.contextRef = contextRef
        this.originalContextRef = originalContextRef
    }

    fun getContextRef(): TreeReference? {
        return this.contextRef
    }

    fun getOriginalContextRef(): TreeReference? {
        if (this.originalContextRef != null) {
            return this.originalContextRef
        }
        // Means that we only have 1 level of context
        return this.contextRef
    }

    @Throws(AnalysisInvalidException::class)
    protected fun requireOriginalContext(forReference: TreeReference?) {
        if (getOriginalContextRef() == null) {
            throw AnalysisInvalidException.INSTANCE_NO_ORIGINAL_CONTEXT_REF
        }
    }

    @Throws(AnalysisInvalidException::class)
    protected fun requireContext(forReference: TreeReference?) {
        if (getContextRef() == null) {
            throw AnalysisInvalidException.INSTANCE_NO_CONTEXT_REF
        }
    }

    @Throws(AnalysisInvalidException::class)
    open fun doAnalysis(analyzable: XPathAnalyzable?) {
        // So that the default behavior is to do nothing
    }

    // TODO: There should be special handling for references that contain "../" as well
    @Throws(AnalysisInvalidException::class)
    open fun doAnalysis(ref: TreeReference) {
        if (ref.contextType == TreeReference.CONTEXT_INHERITED) {
            doAnalysisForRelativeTreeRef(ref)
        } else if (ref.contextType == TreeReference.CONTEXT_ORIGINAL) {
            doAnalysisForTreeRefWithCurrent(ref)
        } else {
            doNormalTreeRefAnalysis(ref)
        }
    }

    @Throws(AnalysisInvalidException::class)
    open fun doNormalTreeRefAnalysis(treeReference: TreeReference) {
        // So that we can override in subclasses for which this is relevant
    }

    // This implementation should work for most analyzers, but some subclasses may want to override
    // and provide more specific behavior
    @Throws(AnalysisInvalidException::class)
    open fun doAnalysisForTreeRefWithCurrent(expressionWithContextTypeCurrent: TreeReference) {
        requireOriginalContext(expressionWithContextTypeCurrent)
        doNormalTreeRefAnalysis(expressionWithContextTypeCurrent.contextualize(getOriginalContextRef()!!)!!)
    }

    // This implementation should work for most analyzers, but some subclasses may want to override
    // and provide more specific behavior
    @Throws(AnalysisInvalidException::class)
    open fun doAnalysisForRelativeTreeRef(expressionWithContextTypeRelative: TreeReference) {
        requireContext(expressionWithContextTypeRelative)
        doNormalTreeRefAnalysis(expressionWithContextTypeRelative.contextualize(getContextRef()!!)!!)
    }

    open fun doAnalysis(expr: XPathFuncExpr?) {
        // So that we can override in subclasses for which this is relevant
    }

    open fun shouldIncludePredicates(): Boolean {
        return true
    }

    open fun spawnSubAnalyzer(subContext: TreeReference?): XPathAnalyzer {
        val subAnalyzer = initSameTypeAnalyzer()
        subAnalyzer.isSubAnalyzer = true
        subAnalyzer.originalContextRef = this.getOriginalContextRef()
        subAnalyzer.contextRef = subContext
        this.subAnalyzers.add(subAnalyzer)
        return subAnalyzer
    }

    internal abstract fun initSameTypeAnalyzer(): XPathAnalyzer

    open fun shortCircuit(): Boolean {
        return shortCircuit
    }
}
