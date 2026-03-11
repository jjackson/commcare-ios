package org.javarosa.core.model.condition

import kotlin.jvm.JvmField

import org.javarosa.core.util.ListMultimap
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QuerySensitiveTreeElementWrapper
import org.commcare.cases.query.queryset.CurrentModelQuerySet
import org.commcare.cases.util.QueryUtils
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.ConcreteInstanceRoot
import org.javarosa.core.model.instance.DataInstance
import org.javarosa.core.model.instance.ExternalDataInstance
import org.javarosa.core.model.instance.FormInstance
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.core.model.trace.BulkEvaluationTrace
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.model.trace.EvaluationTraceReporter
import org.javarosa.core.model.utils.CacheHost
import org.javarosa.core.services.Logger
import org.javarosa.xpath.IExprDataType
import org.javarosa.xpath.XPathLazyNodeset
import org.javarosa.xpath.XPathMissingInstanceException
import org.javarosa.xpath.expr.ExpressionCacher
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathExpression

/**
 * A collection of objects that affect the evaluation of an expression, like
 * function handlers and (not supported) variable bindings.
 */
class EvaluationContext {

    /**
     * Whether XPath expressions being evaluated should be traced during
     * execution for debugging.
     */
    private var mAccumulateExprs: Boolean = false

    /**
     * During debugging this context is the base that holds the trace root and
     * aggregates ongoing execution.
     */
    private var debugContext: EvaluationContext? = null

    /**
     * The current execution trace being evaluated in debug mode
     */
    private var currentTraceLevel: EvaluationTrace? = null

    /**
     * The root of the current execution trace
     */
    private var traceRoot: EvaluationTrace? = null

    /**
     * An optional reporter for traced evaluations
     */
    private var traceReporter: EvaluationTraceReporter? = null

    // Unambiguous anchor reference for relative paths
    val contextRef: TreeReference?

    private val functionHandlers: HashMap<String, IFunctionHandler>
    private val variables: HashMap<String, Any?>

    // Do we want to evaluate constraints?
    @JvmField
    var isConstraint: Boolean = false

    // validate this value when isConstraint is set
    @JvmField
    var candidateValue: IAnswerData? = null

    // Responsible for informing itext what form is requested if relevant
    private var outputTextForm: String? = null

    private val formInstances: HashMap<String, DataInstance<*>>

    // original context reference used for evaluating current()
    private var original: TreeReference? = null

    // Keeps track of the overall context for the executing query stack
    private var queryContext: QueryContext? = null

    /**
     * What element in a nodeset is the context currently pointing to?
     * Used for calculating the position() xpath function.
     */
    private var currentContextPosition: Int = -1

    private var expressionCacher: ExpressionCacher? = null

    private val instance: DataInstance<*>?

    constructor(instance: DataInstance<*>?) : this(instance, HashMap())

    constructor(base: EvaluationContext?, context: TreeReference?) :
            this(base, base?.instance, context, base?.formInstances ?: HashMap())

    constructor(
        base: EvaluationContext?,
        formInstances: HashMap<String, DataInstance<*>>,
        context: TreeReference?
    ) : this(base, base?.instance, context, formInstances)

    constructor(
        instance: FormInstance?,
        formInstances: HashMap<String, DataInstance<*>>,
        base: EvaluationContext?
    ) : this(base, instance, base?.contextRef, formInstances)

    constructor(
        instance: DataInstance<*>?,
        formInstances: HashMap<String, DataInstance<*>>
    ) {
        this.formInstances = formInstances
        this.instance = instance
        this.contextRef = TreeReference.rootRef()
        functionHandlers = HashMap()
        variables = HashMap()
        this.setQueryContext(QueryContext())
    }

    /**
     * Copy Constructor
     */
    private constructor(
        base: EvaluationContext?,
        instance: DataInstance<*>?,
        contextNode: TreeReference?,
        formInstances: HashMap<String, DataInstance<*>>
    ) {
        // TODO: These should be deep, not shallow
        this.functionHandlers = base?.functionHandlers ?: HashMap()

        this.formInstances = HashMap()
        this.copyInstances(formInstances)

        this.variables = HashMap()
        // TODO: this is actually potentially much slower than
        // our old strategy (but is needed for this object to
        // be threadsafe). We should evaluate the potential impact.
        if (base != null) {
            this.shallowVariablesCopy(base.variables)
        }

        this.contextRef = contextNode
        this.instance = instance

        this.isConstraint = base?.isConstraint ?: false
        this.candidateValue = base?.candidateValue

        this.outputTextForm = base?.outputTextForm
        this.original = base?.original

        // Hrm....... not sure about this one. this only happens after a rescoping,
        // and is fixed on the context. Anything that changes the context should
        // invalidate this
        this.currentContextPosition = base?.currentContextPosition ?: -1

        if (base != null && base.mAccumulateExprs) {
            this.mAccumulateExprs = true
            this.debugContext = base.debugContext
        }

        this.expressionCacher = base?.expressionCacher
        setQueryContext(base?.queryContext ?: QueryContext())
    }

    fun getInstance(id: String?): DataInstance<*>? {
        return if (formInstances.containsKey(id)) formInstances[id] else null
    }

    fun setOriginalContext(ref: TreeReference?) {
        this.original = ref
    }

    fun getOriginalContext(): TreeReference? {
        return if (this.original == null) {
            this.contextRef
        } else {
            this.original
        }
    }

    fun enableExpressionCaching() {
        this.expressionCacher = ExpressionCacher()
    }

    fun expressionCachingEnabled(): Boolean {
        return expressionCacher != null
    }

    fun expressionCacher(): ExpressionCacher? {
        return expressionCacher
    }

    fun addFunctionHandler(fh: IFunctionHandler) {
        functionHandlers[fh.getName()] = fh
    }

    fun getFunctionHandlers(): HashMap<String, IFunctionHandler> {
        return functionHandlers
    }

    fun setOutputTextForm(form: String?) {
        this.outputTextForm = form
    }

    fun getOutputTextForm(): String? {
        return outputTextForm
    }

    private fun shallowVariablesCopy(variablesToCopy: HashMap<String, Any?>) {
        val e: Iterator<*> = variablesToCopy.keys.iterator()
        while (e.hasNext()) {
            val key = e.next() as String
            variables[key] = variablesToCopy[key]
        }
    }

    /**
     * This is not a true deep copy since it does not copy the underlying data structures,
     * but it does isolate some changes to the instances which happen when spawning new contexts
     * e.g. replacing the root.
     */
    private fun copyInstances(formInstances: HashMap<String, DataInstance<*>>?) {
        if (formInstances != null) {
            for ((key, value) in formInstances) {
                var inst = value
                if (inst is ExternalDataInstance) {
                    inst = inst.copy()
                }
                this.formInstances[key] = inst
            }
        }
    }

    fun setVariables(variables: HashMap<String, *>) {
        val e: Iterator<*> = variables.keys.iterator()
        while (e.hasNext()) {
            val key = e.next() as String
            setVariable(key, variables[key])
        }
    }

    fun setVariable(name: String, value: Any?) {
        // No such thing as a null xpath variable. Empty
        // values in XPath just get converted to ""
        if (value == null ||
            (value is String && value.toString().trim().isEmpty())) {
            variables[name] = ""
            return
        }
        // Otherwise check whether the value is one of the normal first
        // order datatypes used in xpath evaluation
        if (value is Boolean ||
            value is Double ||
            value is String ||
            value is org.javarosa.core.model.utils.PlatformDate ||
            value is IExprDataType) {
            variables[name] = value
            return
        }

        // Some datatypes can be trivially converted to a first order
        // xpath datatype
        if (value is Int) {
            variables[name] = value.toDouble()
            return
        }
        if (value is Float) {
            variables[name] = value.toDouble()
        } else {
            // Otherwise we just hope for the best, I suppose? Should we log this?
            variables[name] = value
        }
    }

    fun getVariable(name: String?): Any? {
        return variables[name]
    }

    fun getCurrentQueryContext(): QueryContext {
        return queryContext!!
    }

    fun setQueryContext(queryContext: QueryContext) {
        this.queryContext = queryContext
        queryContext.setTraceRoot(this)
    }

    fun expandReference(ref: TreeReference): ArrayList<TreeReference>? {
        return expandReference(ref, false)
    }

    /**
     * Search for all repeated nodes that match the pattern of the 'ref'
     * argument.
     *
     * '/' returns {'/'}
     * can handle sub-repetitions (e.g., {/a[1]/b[1], /a[1]/b[2], /a[2]/b[1]})
     *
     * @param ref Potentially ambiguous reference
     * @return Null if 'ref' is relative reference. Otherwise, returns a vector
     * of references that point to nodes that match 'ref' argument. These
     * references are unambiguous (no index will ever be INDEX_UNBOUND) template
     * nodes won't be included when matching INDEX_UNBOUND, but will be when
     * INDEX_TEMPLATE is explicitly set.
     */
    fun expandReference(ref: TreeReference, includeTemplates: Boolean): ArrayList<TreeReference>? {
        if (!ref.isAbsolute) {
            return null
        }

        val baseInstance = retrieveInstance(ref)
        val v = ArrayList<TreeReference>()

        expandReferenceAccumulator(ref, baseInstance, baseInstance.getRoot()!!.getRef(), v, includeTemplates)
        return v
    }

    /**
     * Recursive helper function for expandReference that performs the search
     * for all repeated nodes that match the pattern of the 'ref' argument.
     *
     * @param sourceRef      original path we're matching against
     * @param sourceInstance original node obtained from sourceRef
     * @param workingRef     explicit path that refers to the current node
     * @param refs           Accumulator vector to collect matching paths. Contained
     *                       references are unambiguous. Template nodes won't be included when
     *                       matching INDEX_UNBOUND, but will be when INDEX_TEMPLATE is explicitly
     *                       set.
     */
    private fun expandReferenceAccumulator(
        sourceRef: TreeReference, sourceInstance: DataInstance<*>,
        workingRef: TreeReference?, refs: ArrayList<TreeReference>,
        includeTemplates: Boolean
    ) {
        if (workingRef == null) {
            throw RuntimeException(
                "Encountered invalid instance definition while evaluating " + sourceRef.toString() +
                        " for instance " + sourceInstance.getInstanceId() + " with root: " + sourceInstance.getRoot()
            )
        }

        val depth = workingRef.size()

        if (depth == sourceRef.size()) {
            // We've matched fully
            // TODO: Should this reference be cloned?
            refs.add(workingRef)
            return
        }
        // Get the next set of matching references
        val name = sourceRef.getName(depth)
        val mult = sourceRef.getMultiplicity(depth)
        var predicates = sourceRef.getPredicate(depth)
        val originalPredicates = predicates

        // Batch fetch is going to mutate the predicates vector, create a copy
        if (predicates != null) {
            val predCopy = ArrayList<XPathExpression>(predicates.size)
            for (xpe in predicates) {
                predCopy.add(xpe)
            }
            predicates = predCopy
        }

        val node = sourceInstance.resolveReference(workingRef, this)

        this.openBulkTrace()

        // Use the reference's simple predicates to filter the potential
        // nodeset.  Predicates used in filtering are removed from the
        // predicate input argument.
        var childSet: Collection<TreeReference>? = if (node != null && name != null && predicates != null) {
            node.tryBatchChildFetch(name, mult, predicates, this)
        } else {
            null
        }

        this.reportBulkTraceResults(originalPredicates, predicates, childSet)
        this.closeTrace()

        if (childSet == null && node != null && name != null) {
            childSet = loadReferencesChildren(node, name, mult, includeTemplates)
        }

        val subContext = queryContext!!
            .checkForDerivativeContextAndReturn(childSet?.size ?: 0)

        // If we forked a new query body from above (IE: a new large query) and there wasn't an
        // original context before, we can anticipate that the subcontext below will reference
        // into the returned body as the original context, which is ugly, but opens up
        // intense optimizations
        if (this.getOriginalContextForPropogation() == null && subContext !== queryContext) {
            subContext.setHackyOriginalContextBody(CurrentModelQuerySet(childSet!!))
        }

        // Create a place to store the current position markers
        val positionContext = IntArray(if (predicates == null) 0 else predicates.size)

        for (refToExpand in childSet ?: return) {
            var passedAll = true
            if (predicates != null && predicates.size > 0) {
                // Evaluate and filter predicates not processed by
                // tryBatchChildFetch
                var predIndex = -1
                for (predExpr in predicates) {
                    predIndex++
                    // Just by getting here we're establishing a position for
                    // evaluating the current context. If we break, we won't
                    // push up the next one
                    positionContext[predIndex]++

                    val evalContext = rescope(
                        refToExpand, positionContext[predIndex],
                        subContext
                    )
                    var o: Any? = predExpr.eval(sourceInstance, evalContext)
                    o = FunctionUtils.unpack(o)

                    var passed = false
                    if (o is Double) {
                        // If a predicate expression is just an Integer, check
                        // if its equal to the current position context

                        // The spec just says "number" for when to use this;
                        // Not clear what to do with a non-integer/rounding.
                        val intVal = FunctionUtils.toInt(o).toInt()
                        passed = intVal == positionContext[predIndex]
                    } else if (o is Boolean) {
                        passed = o
                    }

                    if (!passed) {
                        passedAll = false
                        break
                    }
                }
            }
            if (passedAll) {
                expandReferenceAccumulator(sourceRef, sourceInstance, refToExpand, refs, includeTemplates)
            }
        }
    }

    /**
     * Gather references to a nodes children with a specific name and
     * multiplicity.
     *
     * @param node             Element of which to collect child references.
     * @param childName        Only collect child references with this name.
     * @param childMult        Collect a particular element/attribute or unbounded.
     * @param includeTemplates Should the result include template elements?
     * @return A list of references to a node's children that have a given name
     * and multiplicity.
     */
    private fun loadReferencesChildren(
        node: AbstractTreeElement,
        childName: String,
        childMult: Int,
        includeTemplates: Boolean
    ): ArrayList<TreeReference> {
        val childSet = ArrayList<TreeReference>()
        QueryUtils.prepareSensitiveObjectForUseInCurrentContext(node, getCurrentQueryContext())

        @Suppress("NAME_SHADOWING")
        val node = QuerySensitiveTreeElementWrapper.WrapWithContext(node, getCurrentQueryContext())
        // NOTE: This currently won't propagate the wrapped context.

        if (node.hasChildren()) {
            if (childMult == TreeReference.INDEX_UNBOUND) {
                val count = node.getChildMultiplicity(childName)
                for (i in 0 until count) {
                    val child = node.getChild(childName, i)
                    if (child != null) {
                        childSet.add(child.getRef())
                    } else {
                        throw IllegalStateException("Missing or non-sequential nodes expanding a reference: " + node.getRef())
                    }
                }
                if (includeTemplates) {
                    val template = node.getChild(childName, TreeReference.INDEX_TEMPLATE)
                    if (template != null) {
                        childSet.add(template.getRef())
                    }
                }
            } else if (childMult != TreeReference.INDEX_ATTRIBUTE) {
                // TODO: Make this test childMult >= 0?
                // If the multiplicity is a simple integer, just get the
                // appropriate child
                val child = node.getChild(childName, childMult)
                if (child != null) {
                    childSet.add(child.getRef())
                }
            }
        }

        // Working reference points to an attribute; add it to set to
        // process
        if (childMult == TreeReference.INDEX_ATTRIBUTE) {
            val attribute = node.getAttribute(null, childName)
            if (attribute != null) {
                childSet.add(attribute.getRef())
            }
        }
        return childSet
    }

    /**
     * Create a copy of the evaluation context, with a new context ref.
     *
     * When determining what the original reference field of the new object
     * should be:
     * - Use the 'original' field from the original object.
     * - If it is unset, use the original objects context reference.
     * - If that is '/' then use the new context reference
     *
     * @param newContextRef      the new context anchor reference
     * @param newContextPosition the new position of the context (in a repeat
     *                           group)
     * @param subContext         the new query context for optimization, may differ from this
     *                           context if there has been a drastic change in query scope
     * @return a copy of this evaluation context, with a new context reference
     * set and the original context reference correspondingly updated.
     */
    fun rescope(
        newContextRef: TreeReference, newContextPosition: Int,
        subContext: QueryContext
    ): EvaluationContext {
        val ec = EvaluationContext(this, newContextRef)
        ec.setQueryContext(subContext)
        ec.currentContextPosition = newContextPosition

        var originalContextRef = this.getOriginalContextForPropogation()
        if (originalContextRef == null) {
            originalContextRef = newContextRef
        }
        ec.setOriginalContext(originalContextRef)

        return ec
    }

    /**
     * @return An evaluation context that should be used by a derived context as the original
     * context, if one exists. If one does not exist, returns null;
     */
    private fun getOriginalContextForPropogation(): TreeReference? {
        // If we have an original context reference, use it
        if (this.original != null) {
            return this.getOriginalContext()
        } else {
            // Otherwise, if the old context reference isn't '/', use that. If
            // the context ref is '/', use the new context ref as the original
            if (TreeReference.rootRef() != this.contextRef) {
                return this.contextRef
            } else {
                // Otherwise propagate the original context reference field
                // with the new context reference argument
                return null
            }
        }
    }

    fun getInstanceIds(): List<String> {
        return ArrayList(formInstances.keys)
    }

    fun getMainInstance(): DataInstance<*>? {
        return instance
    }

    fun resolveReference(qualifiedRef: TreeReference): AbstractTreeElement? {
        if (org.javarosa.core.util.PlatformThread.interrupted()) {
            throw RequestAbandonedException()
        }
        var resolveInstance: DataInstance<*>? = this.getMainInstance()
        if (qualifiedRef.getInstanceName() != null &&
            (resolveInstance == null || resolveInstance.getInstanceId() == null ||
                    resolveInstance.getInstanceId() != qualifiedRef.getInstanceName())) {
            resolveInstance = this.getInstance(qualifiedRef.getInstanceName())
        }
        if (resolveInstance == null) {
            val e = XPathMissingInstanceException(qualifiedRef)
            Logger.exception(e.message, e)
            throw e
        }
        return resolveInstance.resolveReference(qualifiedRef, this)
    }

    /**
     * The context's current position in terms the nodes available for the
     * context's path. I.e. if the context points to the 3rd node that /a/b/c
     * resolves to, then the current position is 3.
     */
    fun getContextPosition(): Int {
        return currentContextPosition
    }

    /**
     * Get the relevant cache host for the provided ref, if one exists.
     */
    fun getCacheHost(ref: TreeReference): CacheHost? {
        val cacheInstance = retrieveInstance(ref) ?: return null
        return cacheInstance.getCacheHost()
    }

    /**
     * Get the instance of the reference argument, if it's present in this
     * context's form instances. Otherwise returns the main instance of this
     * evaluation context.
     *
     * @param ref retrieve the instance of this reference, if loaded in the
     *            context
     * @return the instance that the reference argument names, if loaded,
     * otherwise the main instance if present.
     */
    private fun retrieveInstance(ref: TreeReference): DataInstance<*> {
        if (ref.getInstanceName() != null &&
            formInstances.containsKey(ref.getInstanceName())) {
            return formInstances[ref.getInstanceName()]!!
        } else if (instance != null) {
            return instance
        }

        throw RuntimeException(
            "Unable to expand reference " +
                    ref.toString(true) +
                    ", no appropriate instance in evaluation context"
        )
    }

    /**
     * Creates a record that an expression is about to be evaluated.
     *
     * @param xPathExpression the expression being evaluated
     */
    fun openTrace(xPathExpression: XPathExpression) {
        if (mAccumulateExprs) {
            val expressionString = xPathExpression.toPrettyString()
            val newLevel = EvaluationTrace(expressionString)
            openTrace(newLevel)
        }
    }

    /**
     * Creates a record that an expression is about to be evaluated.
     *
     * @param newLevel The new trace to be added to the current evaluation
     */
    fun openTrace(newLevel: EvaluationTrace) {
        if (mAccumulateExprs) {
            val dc = debugContext!!
            newLevel.setParent(dc.currentTraceLevel)
            if (dc.currentTraceLevel != null) {
                dc.currentTraceLevel!!.addSubTrace(newLevel)
            }

            dc.currentTraceLevel = newLevel
        }
    }

    /**
     * Creates a record that we are going to attempt to expanding a set of bulk lookup
     * predicates
     */
    private fun openBulkTrace() {
        if (mAccumulateExprs) {
            val newLevel = BulkEvaluationTrace()
            // We can't really track bulk traces from root contexts
            openTrace(newLevel)
        }
    }

    /**
     * Creates a record that we are going to attempt to expand a set of bulk lookup predicates
     */
    private fun reportBulkTraceResults(
        startingSet: ArrayList<XPathExpression>?,
        finalSet: ArrayList<XPathExpression>?,
        childSet: Collection<TreeReference>?
    ) {
        if (mAccumulateExprs) {
            val dc = debugContext!!
            if (dc.currentTraceLevel !is BulkEvaluationTrace) {
                throw RuntimeException("Predicate tree mismatch")
            }
            val trace = dc.currentTraceLevel as BulkEvaluationTrace
            trace.setEvaluatedPredicates(startingSet, finalSet, childSet)
            if (!trace.isBulkEvaluationSucceeded()) {
                val parentTrace = trace.getParent()
                if (parentTrace == null) {
                    trace.markClosed()
                    // no need to remove from the parent context if it doesn't exist
                    return
                }
                val traces = trace.getParent()!!.getSubTraces()
                synchronized(traces) {
                    traces.remove(trace)
                }
            }
        }
    }

    fun reportSubtrace(trace: EvaluationTrace) {
        if (mAccumulateExprs) {
            val dc = debugContext
            if (dc != null && dc.currentTraceLevel != null) {
                dc.currentTraceLevel!!.addSubTrace(trace)
            }
        }
    }

    /**
     * Records the outcome of the current trace by value.
     *
     * @param value The result of the currently open Trace Expression
     */
    fun reportTraceValue(value: Any?, fromCache: Boolean) {
        if (mAccumulateExprs) {
            // Lazy nodeset evaluation makes it impossible for the trace to
            // record predicate subexpressions properly, so trigger that
            // evaluation now.
            if (value is XPathLazyNodeset) {
                value.size()
            }
            debugContext!!.currentTraceLevel!!.setOutcome(value, fromCache)
        }
    }

    /**
     * Closes the current evaluation trace and records the
     * relevant outcomes and context
     */
    fun closeTrace() {
        if (mAccumulateExprs) {
            val dc = debugContext!!
            val currentTrace = dc.currentTraceLevel
            if (currentTrace != null) {
                if (dc.traceReporter != null &&
                    (currentTrace.getParent() == null || dc.traceReporter!!.reportAsFlat())) {
                    dc.traceReporter!!.reportTrace(currentTrace)
                }

                if (currentTrace.getParent() == null) {
                    dc.traceRoot = currentTrace
                }

                dc.currentTraceLevel = currentTrace.getParent()
            }
        }
    }

    /**
     * Sets this EC to be the base of a trace capture for debugging.
     */
    fun setDebugModeOn() {
        setDebugModeOn(null)
    }

    /**
     * Sets this EC to be the base of a trace capture for debugging.
     */
    fun setDebugModeOn(reporter: EvaluationTraceReporter?) {
        this.mAccumulateExprs = true
        this.debugContext = this
        this.traceReporter = reporter
    }

    /**
     * @return the trace of the expression evaluation that was performed
     * against this context.
     */
    fun getEvaluationTrace(): EvaluationTrace? {
        return traceRoot
    }

    /**
     * Spawn a new evaluation context with the same context information as this context
     * but which can maintain its own lifecycle, including a fresh query context and
     * capacity to abandon requests
     */
    fun spawnWithCleanLifecycle(): EvaluationContext {
        return spawnWithCleanLifecycle(null)
    }

    fun spawnWithCleanLifecycle(additionalInstances: Map<String, ExternalDataInstance>?): EvaluationContext {
        val ec = EvaluationContext(this, this.contextRef)
        val qc = ec.getCurrentQueryContext().forceNewChildContext()
        ec.setQueryContext(qc)
        if (additionalInstances != null) {
            ec.updateInstances(additionalInstances)
        }
        return ec
    }

    private fun updateInstances(instances: Map<String, ExternalDataInstance>) {
        val byRef = getInstancesByRef()
        instances.forEach { (name, newInstance) ->
            val ref = newInstance.getReference() ?: return@forEach
            if (!byRef.containsKey(ref)) {
                if (formInstances.containsKey(name)) {
                    throw RuntimeException(
                        String.format(
                            "EvaluationContext already contains an instance with "
                                    + "ID %s with a different ref", name
                        )
                    )
                }
                formInstances[name] = newInstance
            } else {
                for (existing in byRef[ref]) {
                    if (existing.getRoot() == null) {
                        // just in time initializing of the instance
                        val instanceId = existing.getInstanceId()
                        var root = newInstance.getRoot() as TreeElement
                        if (instanceId != name) {
                            root = root.deepCopy(false)
                            root.accept(object : ITreeVisitor {
                                override fun visit(tree: FormInstance) {
                                    throw RuntimeException("Not implemented")
                                }
                                override fun visit(element: AbstractTreeElement) {
                                    (element as TreeElement).setInstanceName(instanceId)
                                }
                            })
                        }
                        root.setParent(existing.getBase())
                        existing.copyFromSource(ConcreteInstanceRoot(root))
                    }
                }
            }
            if (!formInstances.containsKey(name) || formInstances[name]!!.getRoot() == null) {
                // instance name is the same so no need to rename it
                formInstances[name] = newInstance
            }
        }
    }

    private fun getInstancesByRef(): ListMultimap<String, ExternalDataInstance> {
        val result = ListMultimap<String, ExternalDataInstance>()
        formInstances.values.forEach { inst ->
            if (inst is ExternalDataInstance) {
                val ref = inst.getReference()
                if (ref != null) {
                    result.put(ref, inst)
                }
            }
        }
        return result
    }
}
