package org.commcare.cases.entity

import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.queryset.CurrentModelQuerySet
import org.commcare.suite.model.Detail
import org.commcare.suite.model.DetailField
import org.commcare.suite.model.DetailGroup
import org.commcare.suite.model.Text
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.ReducingTraceReporter
import org.javarosa.core.model.utils.InstrumentationUtils
import org.javarosa.xpath.XPathException
import org.javarosa.xpath.parser.XPathSyntaxException
import kotlin.jvm.JvmField

/**
 * @author ctsims
 */
open class NodeEntityFactory(
    @JvmField
    protected val detail: Detail,
    @JvmField
    protected val ec: EvaluationContext
) {
    private var mEntitySetInitialized = false
    private var traceReporter: ReducingTraceReporter? = null

    @JvmField
    protected var progressListener: EntityLoadingProgressListener? = null

    /**
     * Flag that denotes cancellation of the underlying process responsible for loading entities
     * Implementations of long running methods can check this to interrupt and exit early
     */
    @Volatile
    @JvmField
    protected var isCancelled = false

    fun activateDebugTraceOutput() {
        this.traceReporter = ReducingTraceReporter(false)
    }

    fun getDetail(): Detail {
        return detail
    }

    open fun getEntity(data: TreeReference): Entity<TreeReference> {
        val nodeContext = EvaluationContext(ec, data)
        val reporter = traceReporter
        if (reporter != null) {
            nodeContext.setDebugModeOn(reporter)
        }
        detail.populateEvaluationContextVariables(nodeContext)

        val length = detail.headerForms.size
        val extraKey = loadCalloutDataMapKey(nodeContext)

        val fieldData = arrayOfNulls<Any>(length)
        val sortData = arrayOfNulls<String>(length)
        val altTextData = arrayOfNulls<String>(length)
        val relevancyData = BooleanArray(length)
        var count = 0
        for (f in detail.fields) {
            try {
                fieldData[count] = f.template?.evaluate(nodeContext)
                val sortText: Text? = f.sort
                sortData[count] = sortText?.evaluate(nodeContext)
                relevancyData[count] = f.isRelevant(nodeContext)
                val altText: Text? = f.altText
                altTextData[count] = altText?.evaluate(nodeContext)
            } catch (e: XPathSyntaxException) {
                // TODO: 25/06/17 remove catch blocks from here
                // We are wrapping the original exception in a new XPathException to avoid
                // refactoring large number of functions caused by throwing XPathSyntaxException here.
                throw XPathException(e)
            }
            count++
        }

        var groupKey: String? = null
        val detailGroup: DetailGroup? = detail.group
        if (detailGroup != null) {
            groupKey = detailGroup.function?.eval(nodeContext) as String?
        }

        return Entity(fieldData, sortData, relevancyData, data, extraKey,
            detail.evaluateFocusFunction(nodeContext), groupKey, altTextData)
    }

    /**
     * Evaluate the lookup's 'template' detail block and use result as key for
     * attaching external (callout) data to the entity.
     */
    protected open fun loadCalloutDataMapKey(entityContext: EvaluationContext): String? {
        val callout = detail.callout
        if (callout != null) {
            val calloutResponseDetail: DetailField? = callout.responseDetailField
            if (calloutResponseDetail != null) {
                val extraDataKey = calloutResponseDetail.template?.evaluate(entityContext)
                if (extraDataKey is String) {
                    return extraDataKey
                }
            }
        }
        return null
    }

    fun expandReferenceList(treeReference: TreeReference): List<TreeReference> {
        val tracableContext = EvaluationContext(ec, ec.getOriginalContext())
        val reporter = traceReporter
        if (reporter != null) {
            tracableContext.setDebugModeOn(reporter)
        }
        val result = tracableContext.expandReference(treeReference) ?: java.util.ArrayList()
        printAndClearTraces("case load expand")

        setEvaluationContextDefaultQuerySet(ec, result)

        return result
    }

    fun printAndClearTraces(description: String) {
        val reporter = traceReporter
        if (reporter != null) {
            InstrumentationUtils.printAndClearTraces(reporter, description)
        }
    }

    /**
     * Lets the evaluation context know what the 'overall' query set in play is. This allows the
     * query planner to know that we aren't just looking to expand results for a specific element,
     * we're currently iterating over a potentially large set of elements and should batch
     * appropriately
     */
    protected open fun setEvaluationContextDefaultQuerySet(
        ec: EvaluationContext,
        result: List<TreeReference>
    ) {
        val newContext: QueryContext = ec.getCurrentQueryContext()
            .checkForDerivativeContextAndReturn(result.size)

        newContext.setHackyOriginalContextBody(CurrentModelQuerySet(result))

        ec.setQueryContext(newContext)
    }

    /**
     * Performs the underlying work to prepare the entity set
     * (see prepareEntities()). Separated out to enforce timing
     * related to preparing and utilizing results
     */
    protected open fun prepareEntitiesInternal(entities: List<Entity<TreeReference>>) {
        // No implementation in normal factory
    }

    /**
     * Optional: Allows the factory to make all of the entities that it has
     * returned "Ready" by performing any lazy evaluation needed for optimum
     * usage. This preparation occurs asynchronously, and the returned entity
     * set should not be manipulated until it has completed.
     */
    fun prepareEntities(entities: List<Entity<TreeReference>>) {
        synchronized(mPreparationLock) {
            prepareEntitiesInternal(entities)
            mEntitySetInitialized = true
        }
    }

    /**
     * Performs the underlying work to check on the entitySet preparation
     * (see isEntitySetReady()). Separated out to enforce timing
     * related to preparing and utilizing results
     */
    protected open fun isEntitySetReadyInternal(): Boolean {
        return true
    }

    /**
     * Called only after a call to prepareEntities, this signals whether
     * the entities returned are ready for bulk operations.
     *
     * @return True if entities returned from the factory are again ready
     * for use. False otherwise.
     */
    val isEntitySetReady: Boolean
        get() {
            synchronized(mPreparationLock) {
                if (!mEntitySetInitialized) {
                    throw RuntimeException("A Node Entity Factory was not prepared before usage. prepareEntities() must be called before a call to isEntitySetReady()")
                }
                return isEntitySetReadyInternal()
            }
        }

    /**
     * Caches the provided entities. Default implementation throws RuntimeException.
     * Subclasses should override this method if they support caching.
     *
     * @param entities     List of entities to cache
     * @throws RuntimeException if caching is not supported
     */
    open fun cacheEntities(entities: List<Entity<TreeReference>>) {
        throw RuntimeException("Method not supported for normal Node Entity Factory")
    }

    /**
     * Sets the progress listener for entity loading operations.
     *
     * @param progressListener The progress listener to use
     */
    fun setEntityProgressListener(progressListener: EntityLoadingProgressListener) {
        if (this.progressListener != null) {
            throw RuntimeException(
                "Entity loading progress listener is already set in entity factory"
            )
        }
        this.progressListener = progressListener
    }

    fun markAsCancelled() {
        isCancelled = true
    }

    companion object {
        private val mPreparationLock = Any()
    }
}
