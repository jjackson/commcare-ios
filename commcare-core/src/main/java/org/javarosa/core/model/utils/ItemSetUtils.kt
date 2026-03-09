package org.javarosa.core.model.utils

import org.commcare.cases.query.ScopeLimitedReferenceRequestCache
import org.commcare.modern.util.Pair
import org.javarosa.core.model.ItemsetBinding
import org.javarosa.core.model.SelectChoice
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.ReducingTraceReporter
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.analysis.AnalysisInvalidException
import org.javarosa.xpath.analysis.TreeReferenceAccumulatingAnalyzer
import java.util.Vector
import javax.annotation.Nullable

// helper class for common functions related to @code{ItemsetBinding}
object ItemSetUtils {

    @JvmStatic
    fun populateDynamicChoices(
        itemset: ItemsetBinding,
        evaluationContext: EvaluationContext
    ) {
        populateDynamicChoices(itemset, null, evaluationContext, null, false)
    }

    /**
     * Identify the itemset in the backend model, and create a set of SelectChoice
     * objects at the current question reference based on the data in the model.
     *
     * Will modify the itemset binding to contain the relevant choices
     *
     * @param itemset The binding for an itemset, where the choices will be populated
     * @param curQRef A reference to the current question's element, which will be
     *                used to determine the values to be chosen from.
     */
    @JvmStatic
    fun populateDynamicChoices(
        itemset: ItemsetBinding,
        @Nullable curQRef: TreeReference?,
        evaluationContext: EvaluationContext,
        @Nullable mainInstance: FormInstance?,
        profileEnabled: Boolean
    ) {
        val formInstance: DataInstance<*>?
        if (itemset.nodesetRef!!.getInstanceName() != null) {
            formInstance = evaluationContext.getInstance(itemset.nodesetRef!!.getInstanceName())
            if (formInstance == null) {
                throw XPathException("Instance ${itemset.nodesetRef!!.getInstanceName()} not found")
            }
        } else {
            formInstance = mainInstance
        }

        if (formInstance == null) {
            throw XPathException("No instance definition available to populate items found at '${itemset.nodesetRef}'")
        }

        var ec: EvaluationContext = if (curQRef == null) {
            evaluationContext
        } else {
            EvaluationContext(evaluationContext, itemset.contextRef!!.contextualize(curQRef))
        }

        var reporter: ReducingTraceReporter? = null
        if (profileEnabled) {
            reporter = ReducingTraceReporter(false)
            ec.setDebugModeOn(reporter)
        }

        ec = getPotentiallyLimitedScopeContext(ec, itemset)

        val matches = itemset.nodesetExpr!!.evalNodeset(formInstance, ec)

        if (reporter != null) {
            InstrumentationUtils.printAndClearTraces(reporter, "itemset expansion")
        }

        if (matches == null) {
            val instanceName = itemset.nodesetRef!!.getInstanceName()
            if (instanceName == null) {
                // itemset references a path rooted in the main instance
                throw XPathException("No items found at '${itemset.nodesetRef}'")
            } else {
                // itemset references a path rooted in a lookup table
                throw XPathException(
                    "Make sure the '$instanceName' lookup table is available, and that its contents are accessible to the current user."
                )
            }
        }

        val choices = Vector<SelectChoice>()
        //Escalate the new context if our result set is substantial, this will prevent reverting
        //from a bulk read mode to a scanned read mode
        val newContext = ec.getCurrentQueryContext()
            .checkForDerivativeContextAndReturn(matches.size)
        ec.setQueryContext(newContext)

        for (i in 0 until matches.size) {
            choices.addElement(
                buildSelectChoice(
                    matches.elementAt(i), itemset, formInstance,
                    mainInstance, ec, i
                )
            )
        }
        if (reporter != null) {
            InstrumentationUtils.printAndClearTraces(reporter, "ItemSet Field Population")
        }

        itemset.setChoices(choices)
    }

    /**
     * Returns an evaluation context which can be used to evaluate the itemset's references, and
     * if possible will be more efficient than the base context provided through static analysis
     * of the itemset expressions.
     */
    private fun getPotentiallyLimitedScopeContext(
        questionContext: EvaluationContext,
        itemset: ItemsetBinding
    ): EvaluationContext {
        val references: Set<TreeReference>
        try {
            references = pullAllReferencesFromItemset(questionContext, itemset)
        } catch (e: AnalysisInvalidException) {
            return questionContext
        }

        val newContext = questionContext.spawnWithCleanLifecycle()

        val isolatedContext = newContext.getCurrentQueryContext()
        val cache = isolatedContext.getQueryCache(ScopeLimitedReferenceRequestCache::class.java)
        cache.addTreeReferencesToLimitedScope(references)
        return newContext
    }

    /**
     * Tries to get all of the absolute tree references which are referenced in the itemset, either in
     * the nodeset calculation, or the individual (label, value, etc...) itemset element calculations.
     *
     * If a value is returned, that value should contain all tree references which will need to be
     * evaluated to produce the itemset output
     *
     * @throws AnalysisInvalidException If the itemset's references could not be fully understood
     *                                  or qualified through static evaluation
     */
    @Throws(AnalysisInvalidException::class)
    private fun pullAllReferencesFromItemset(
        questionContext: EvaluationContext,
        itemset: ItemsetBinding
    ): Set<TreeReference> {
        val references = getAccumulatedReferencesOrThrow(questionContext, itemset.nodesetRef)

        val itemsetSubexpressionContext = EvaluationContext(questionContext, itemset.nodesetRef)

        references.addAll(getAccumulatedReferencesOrThrow(itemsetSubexpressionContext, itemset.labelRef))
        references.addAll(getAccumulatedReferencesOrThrow(itemsetSubexpressionContext, itemset.valueRef))
        references.addAll(getAccumulatedReferencesOrThrow(itemsetSubexpressionContext, itemset.sortRef))

        return references
    }

    @Throws(AnalysisInvalidException::class)
    private fun getAccumulatedReferencesOrThrow(
        subContext: EvaluationContext,
        newRef: TreeReference?
    ): MutableSet<TreeReference> {
        if (newRef == null) {
            return HashSet()
        }
        val analyzer = TreeReferenceAccumulatingAnalyzer(subContext)

        val newReferences = analyzer.accumulate(newRef)
            ?: throw AnalysisInvalidException.INSTANCE_ITEMSET_ACCUM_FAILURE

        return newReferences
    }

    // Builds select choices for a ItemsetBinding @param{itemset} by evaulating it against the given EvaluationContext @param{ec}
    private fun buildSelectChoice(
        choiceRef: TreeReference,
        itemset: ItemsetBinding,
        formInstance: DataInstance<*>,
        @Nullable mainInstance: FormInstance?,
        ec: EvaluationContext,
        index: Int
    ): SelectChoice {
        val subContext = EvaluationContext(ec, choiceRef)

        val label = itemset.labelExpr!!.evalReadable(formInstance, subContext)

        var value: String? = null
        var copyNode: TreeElement? = null

        if (itemset.copyMode && mainInstance != null) {
            copyNode = mainInstance.resolveReference(itemset.copyRef!!.contextualize(choiceRef)!!)
        }

        if (itemset.valueRef != null) {
            value = itemset.valueExpr!!.evalReadable(formInstance, subContext)
        }

        val choice = SelectChoice(
            label, if (value != null) value else "dynamic:$index",
            itemset.labelIsItext
        )

        choice.setIndex(index)

        if (itemset.copyMode) {
            choice.copyNode = copyNode
        }

        if (itemset.sortRef != null) {
            val evaluatedSortProperty = itemset.sortExpr!!.evalReadable(formInstance, subContext)
            choice.setSortProperty(evaluatedSortProperty)
        }
        return choice
    }

    // Get index of a value for the given itemset,
    // return -1 if value is not present in itemset
    @JvmStatic
    fun getIndexOf(itemsetBinding: ItemsetBinding, value: String): Int {
        for (i in 0 until itemsetBinding.getChoices()!!.size) {
            if (itemsetBinding.getChoices()!![i].value.contentEquals(value)) {
                return i
            }
        }
        return -1
    }

    // returns labels corresponding to choices associated with the given itemsetBinding
    @JvmStatic
    fun getChoices(itemsetBinding: ItemsetBinding): Pair<Array<String?>, Array<String?>> {
        val selectChoices = itemsetBinding.getChoices()!!
        val choiceLabels = arrayOfNulls<String>(selectChoices.size)
        val choiceKeys = arrayOfNulls<String>(selectChoices.size)
        for (i in 0 until selectChoices.size) {
            val selectChoice = selectChoices[i]
            choiceLabels[i] = selectChoice.labelInnerText
            choiceKeys[i] = selectChoice.value
        }
        return Pair(choiceKeys, choiceLabels)
    }
}
