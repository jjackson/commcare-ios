package org.commcare.cases.util

import org.commcare.cases.query.IndexedSetMemberLookup
import org.commcare.cases.query.IndexedValueLookup
import org.commcare.cases.query.NegativeIndexedValueLookup
import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QueryPlanner
import org.commcare.cases.query.handlers.BasicStorageBackedCachingQueryHandler
import org.commcare.modern.engine.cases.RecordSetResultCache
import org.commcare.modern.util.PerformanceTuningUtil
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.DataUtil
import org.javarosa.xpath.expr.FunctionUtils
import org.javarosa.xpath.expr.XPathEqExpr
import org.javarosa.xpath.expr.XPathExpression
import org.javarosa.xpath.expr.XPathPathExpr
import org.javarosa.xpath.expr.XPathSelectedFunc

/**
 * @author ctsims
 */
abstract class StorageBackedTreeRoot<T : AbstractTreeElement> : AbstractTreeElement {

    private var _queryPlanner: QueryPlanner? = null
    protected var defaultCacher: BasicStorageBackedCachingQueryHandler? = null

    @JvmField
    protected val objectIdMapping: HashMap<Int, Int> = HashMap()

    /**
     * Super basic cache for Key/Index responses from the DB
     */
    private val mIndexResultCache: HashMap<String, LinkedHashSet<Int>> = HashMap()

    /**
     * Get the key/value meta lookups for the most recent batch fetch. Used to prime a couple
     * of other caches, although this should eventually migrate to use the new cueing framework
     */
    @JvmField
    protected var mMostRecentBatchFetch: Array<Array<String>>? = null

    protected abstract fun getChildHintName(): String

    protected abstract fun getStorageIndexMap(): HashMap<XPathPathExpr, String>

    protected abstract fun getStorage(): IStorageUtilityIndexed<*>

    protected abstract fun initStorageCache()

    protected open fun translateFilterExpr(
        expressionTemplate: XPathPathExpr,
        matchingExpr: XPathPathExpr,
        indices: HashMap<XPathPathExpr, String>
    ): String? {
        return indices[expressionTemplate]
    }

    override fun tryBatchChildFetch(
        name: String,
        mult: Int,
        predicates: ArrayList<XPathExpression>,
        evalContext: EvaluationContext
    ): Collection<TreeReference>? {
        // Restrict what we'll handle for now. All we want to deal with is predicate expressions on case blocks
        if (name != getChildHintName() || mult != TreeReference.INDEX_UNBOUND || predicates == null) {
            return null
        }

        val indices = getStorageIndexMap()

        val profiles = ArrayList<PredicateProfile>()

        val queryContext = evalContext.getCurrentQueryContext()

        // First, attempt to use 'preferred' optimizations detectable by the query planner
        // using advanced inspection of the predicates
        val preferredProfiles = ArrayList<PredicateProfile>()

        val collected = getQueryPlanner().collectPredicateProfiles(
            predicates, queryContext, evalContext
        )
        if (collected != null) {
            preferredProfiles.addAll(collected)
        }

        // For now we are going to skip looking deeper if we trigger
        // any of the planned optimizations
        if (preferredProfiles.size > 0) {
            val response = processPredicatesAndPrepareResponse(
                preferredProfiles,
                queryContext, predicates
            )

            // For now if there are any results we should press forward. We don't have a meaningful
            // way to combine these results with native optimizations
            if (response != null) {
                return response
            }
        }

        // Otherwise, identify predicates that we _might_ be able to evaluate more efficiently
        // based on normal keyed behavior
        collectNativePredicateProfiles(predicates, indices, evalContext, profiles)

        return processPredicatesAndPrepareResponse(profiles, queryContext, predicates)
    }

    private fun processPredicatesAndPrepareResponse(
        profiles: ArrayList<PredicateProfile>,
        queryContext: QueryContext,
        predicates: ArrayList<XPathExpression>
    ): Collection<TreeReference>? {
        // Now go through each profile and see if we can match / process any of them. If not, we
        // will return null and move on
        val toRemove = ArrayList<Int>()
        val selectedElements = processPredicates(toRemove, profiles, queryContext)

        // if we weren't able to evaluate any predicates, signal that.
        if (selectedElements == null) {
            return null
        }

        // otherwise, remove all of the predicates we've already evaluated
        for (i in toRemove.size - 1 downTo 0) {
            predicates.removeAt(toRemove[i])
        }

        return buildReferencesFromFetchResults(selectedElements)
    }

    private fun collectNativePredicateProfiles(
        predicates: ArrayList<XPathExpression>,
        indices: HashMap<XPathPathExpr, String>,
        evalContext: EvaluationContext,
        optimizations: ArrayList<PredicateProfile>
    ) {
        for (xpe in predicates) {
            // what we want here is a static evaluation of the expression to see if it consists of evaluating
            // something we index with something static.
            if (xpe is XPathEqExpr) {
                val left = xpe.a
                if (left is XPathPathExpr) {
                    val en = indices.keys.iterator()
                    var matched = false
                    while (en.hasNext()) {
                        val expr = en.next()
                        if (expr.matches(left)) {
                            val filterIndex = translateFilterExpr(expr, left, indices)

                            // TODO: We need a way to determine that this value does not also depend on anything in the current context
                            val o = FunctionUtils.unpack(xpe.b?.eval(evalContext))
                            if (xpe.op == XPathEqExpr.EQ) {
                                val lookup = IndexedValueLookup(filterIndex!!, o!!)
                                optimizations.add(lookup)
                                matched = true
                                break
                            } else if (xpe.op == XPathEqExpr.NEQ) {
                                val lookup = NegativeIndexedValueLookup(filterIndex!!, o!!)
                                optimizations.add(lookup)
                                matched = true
                                break
                            }
                        }
                    }
                    if (matched) continue
                }
            } else if (xpe is XPathSelectedFunc) {
                val lookupArg = xpe.args[1]
                if (lookupArg is XPathPathExpr) {
                    val en = indices.keys.iterator()
                    var matched = false
                    while (en.hasNext()) {
                        val expr = en.next()
                        if (expr.matches(lookupArg)) {
                            val filterIndex = translateFilterExpr(expr, lookupArg, indices)

                            // TODO: We need a way to determine that this value does not also depend on anything in the current context
                            val o = FunctionUtils.unpack(xpe.args[0].eval(evalContext))

                            optimizations.add(IndexedSetMemberLookup(filterIndex!!, o!!))
                            matched = true
                            break
                        }
                    }
                    if (matched) continue
                }
            }

            // There's only one case where we want to keep moving along, and we would have triggered it if it were going to happen,
            // so otherwise, just get outta here.
            break
        }
    }

    protected open fun getQueryPlanner(): QueryPlanner {
        if (_queryPlanner == null) {
            _queryPlanner = QueryPlanner()
            initBasicQueryHandlers(_queryPlanner!!)
        }
        return _queryPlanner!!
    }

    protected open fun initBasicQueryHandlers(queryPlanner: QueryPlanner) {
        defaultCacher = BasicStorageBackedCachingQueryHandler()

        // TODO: Move the actual indexed query optimization used in this
        // method into its own (or a matching) cache method
        queryPlanner.addQueryHandler(defaultCacher!!)
    }

    private fun processPredicates(
        toRemove: ArrayList<Int>,
        profiles: ArrayList<PredicateProfile>,
        currentQueryContext: QueryContext
    ): Collection<Int>? {
        var selectedElements: Collection<Int>? = null
        val storage = getStorage()
        var predicatesProcessed = 0
        var context = currentQueryContext
        while (profiles.size > 0) {
            val startCount = profiles.size
            val plannedQueryResults =
                this.getQueryPlanner().attemptProfiledQuery(profiles, context)

            if (plannedQueryResults != null) {
                // merge with any other sets of cases
                selectedElements = if (selectedElements == null) {
                    plannedQueryResults
                } else {
                    DataUtil.intersection(selectedElements, plannedQueryResults)
                }
            } else {
                var cases: Collection<Int>?
                try {
                    // Get all of the cases that meet this criteria
                    cases = this.getNextIndexMatch(profiles, storage, context)
                } catch (iae: IllegalArgumentException) {
                    // Encountered a new index type
                    break
                }

                // merge with any other sets of cases
                selectedElements = if (selectedElements == null) {
                    cases
                } else {
                    DataUtil.intersection(selectedElements, cases)
                }
            }

            if (selectedElements != null && selectedElements.isEmpty()) {
                // There's nothing left! We can completely wipe the remaining profiles
                profiles.clear()
            }

            val numPredicatesRemoved = startCount - profiles.size
            for (i in 0 until numPredicatesRemoved) {
                // Note that this predicate is evaluated and doesn't need to be evaluated in the future.
                toRemove.add(DataUtil.integer(predicatesProcessed))
                predicatesProcessed++
            }
            context = context.testForInlineScopeEscalation(selectedElements!!.size)
        }
        return selectedElements
    }

    private fun buildReferencesFromFetchResults(selectedElements: Collection<Int>): Collection<TreeReference> {
        val base = this.getRef()

        initStorageCache()

        val filtered = ArrayList<TreeReference>()
        for (i in selectedElements) {
            // this takes _waaaaay_ too long, we need to refactor this
            val ref = base.clone()
            val realIndex = objectIdMapping[i]!!
            ref.add(this.getChildHintName(), realIndex)
            filtered.add(ref)
        }
        return filtered
    }

    /**
     * Attempt to process one or more of the elements from the heads of the key/value vector, and return the
     * matching ID's. If an argument is processed, they should be removed from the key/value vector
     *
     * **Important:** This method and any re-implementations *must remove at least one key/value pair
     * from the incoming Vectors*, or must throw an IllegalArgumentException to denote that the provided
     * key can't be processed in the current context. The method can optionally remove/process more than one
     * key at a time, but is expected to process at least the first.
     *
     * @param profiles    A vector of pending optimizations to be attempted. The keys should be processed left->right
     * @param storage The storage to be processed
     * @param currentQueryContext
     * @return A ArrayList of integer ID's for records in the provided storage which match one or more of the keys provided.
     * @throws IllegalArgumentException If there was no index matching possible on the provided key and the key/value vectors
     *                                  won't be shortened.
     */
    @Throws(IllegalArgumentException::class)
    protected open fun getNextIndexMatch(
        profiles: ArrayList<PredicateProfile>,
        storage: IStorageUtilityIndexed<*>,
        currentQueryContext: QueryContext
    ): Collection<Int> {
        val numKeysToProcess = this.getNumberOfBatchableKeysInProfileSet(profiles)

        if (numKeysToProcess < 1) {
            throw IllegalArgumentException("No optimization path found for optimization type")
        }

        val namesToMatch = ArrayList<String>()
        val valuesToMatch = ArrayList<String>()
        val namesToInverseMatch = ArrayList<String>()
        val valuesToInverseMatch = ArrayList<String>()

        var cacheKey = ""
        var keyDescription = ""

        for (i in numKeysToProcess - 1 downTo 0) {
            var name = ""
            var value = ""
            var operator = ""
            if (profiles[i] is IndexedValueLookup) {
                name = profiles[i].getKey()
                value = (profiles[i] as IndexedValueLookup).value as String
                namesToMatch.add(name)
                valuesToMatch.add(value)
                operator = "="
            } else if (profiles[i] is NegativeIndexedValueLookup) {
                name = profiles[i].getKey()
                value = (profiles[i] as NegativeIndexedValueLookup).value as String
                namesToInverseMatch.add(name)
                valuesToInverseMatch.add(value)
                operator = "!="
            }
            cacheKey += "|$name$operator$value"
            keyDescription += "$name|"
        }
        val namesArray = namesToMatch.toTypedArray()
        val valuesArray: Array<Any> = valuesToMatch.toTypedArray()
        val inverseNames = namesToInverseMatch.toTypedArray()
        val inverseValues: Array<Any> = valuesToInverseMatch.toTypedArray()
        mMostRecentBatchFetch = arrayOf(namesArray, valuesArray.map { it as String }.toTypedArray(), inverseNames, inverseValues.map { it as String }.toTypedArray())

        val storageTreeName = this.storageCacheName

        val ids: LinkedHashSet<Int>
        if (mIndexResultCache.containsKey(cacheKey)) {
            ids = mIndexResultCache[cacheKey]!!
        } else {
            val trace = EvaluationTrace(
                String.format("Storage [%s] Key Lookup [%s]", storageTreeName, keyDescription)
            )
            ids = LinkedHashSet()
            storage.getIDsForValues(namesArray, valuesArray, inverseNames, inverseValues, ids)
            trace.setOutcome("Results: " + ids.size)
            currentQueryContext.reportTrace(trace)

            mIndexResultCache[cacheKey] = ids
        }

        if (ids.size > 50 && ids.size < PerformanceTuningUtil.getMaxPrefetchCaseBlock()) {
            val cue = currentQueryContext.getQueryCache(RecordSetResultCache::class.java)
            val cacheName = storageCacheName
            if (cacheName != null) {
                cue.reportBulkRecordSet(cacheKey, cacheName, ids)
            }
        }

        // Ok, we matched! Remove all of the keys that we matched
        for (i in 0 until numKeysToProcess) {
            profiles.removeAt(0)
        }
        return ids
    }

    /**
     * Provide the number of keys that should be included in a general multi-key metadata lookup
     * from the provided set. Each key in the returned set should be an indexed value lookup
     * which can be matched in flat metadata with no additional processing.
     *
     * @param profiles A set of potential predicate profiles for bulk processing
     * @return The number of elements to process from the provided set. If only the first
     * profile would be processed, for instance, this method should return 1
     */
    protected open fun getNumberOfBatchableKeysInProfileSet(profiles: ArrayList<PredicateProfile>): Int {
        var keysToBatch = 0
        // Otherwise see how many of these we can bulk process
        for (i in 0 until profiles.size) {
            // If the current key isn't an indexedvalue lookup, we can't process in this step
            if (profiles[i] !is IndexedValueLookup &&
                profiles[i] !is NegativeIndexedValueLookup
            ) {
                break
            }

            // otherwise, it's now in our queue
            keysToBatch++
        }
        return keysToBatch
    }

    /**
     * @return A string which will provide a unique name for the storage that is used in this tree
     * root. Used to differentiate the record ID's retrieved during operations on this root in
     * internal caches
     */
    abstract val storageCacheName: String?
}
