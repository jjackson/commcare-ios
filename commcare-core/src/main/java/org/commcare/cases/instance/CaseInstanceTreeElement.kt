package org.commcare.cases.instance

import org.commcare.cases.model.Case
import org.commcare.cases.query.IndexedSetMemberLookup
import org.commcare.cases.query.IndexedValueLookup
import org.commcare.cases.query.NegativeIndexedValueLookup
import org.commcare.cases.query.PredicateProfile
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QueryPlanner
import org.commcare.cases.query.handlers.ModelQueryLookupHandler
import org.commcare.cases.query.queryset.CaseModelQuerySetMatcher
import org.commcare.modern.engine.cases.CaseIndexQuerySetTransform
import org.commcare.modern.engine.cases.CaseIndexTable
import org.commcare.modern.engine.cases.query.CaseIndexPrefetchHandler
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.CacheHost
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.DataUtil
import org.javarosa.model.xform.XPathReference
import org.javarosa.xpath.expr.XPathPathExpr
import kotlin.jvm.JvmField

/**
 * The root element for the `<casedb>` abstract type. All children are
 * nodes in the case database. Depending on instantiation, the `<casedb>`
 * may include only a subset of the full db.
 *
 * @author ctsims
 */
class CaseInstanceTreeElement : StorageInstanceTreeElement<Case, CaseChildElement>, CacheHost {

    private val multiplicityIdMapping: HashMap<Int, Int> = HashMap()

    private val caseIndexTable: CaseIndexTable?

    //We're storing this here for now because this is a safe lifecycle object that must represent
    //a single snapshot of the case database, but it could be generalized later.
    private var mIndexCache: HashMap<String, LinkedHashSet<Int>> = HashMap()

    constructor(
        instanceRoot: AbstractTreeElement?,
        storage: IStorageUtilityIndexed<Case>
    ) : this(instanceRoot, storage, null)

    constructor(
        instanceRoot: AbstractTreeElement?,
        storage: IStorageUtilityIndexed<Case>,
        caseIndexTable: CaseIndexTable?
    ) : super(instanceRoot, storage, MODEL_NAME, "case") {
        this.caseIndexTable = caseIndexTable
    }

    override fun buildElement(
        storageInstance: StorageInstanceTreeElement<Case, CaseChildElement>,
        recordId: Int,
        id: String?,
        mult: Int
    ): CaseChildElement {
        return CaseChildElement(storageInstance, recordId, null, mult)
    }

    override fun initBasicQueryHandlers(queryPlanner: QueryPlanner) {
        super.initBasicQueryHandlers(queryPlanner)
        queryPlanner.addQueryHandler(CaseIndexPrefetchHandler(caseIndexTable))
        val matcher = CaseModelQuerySetMatcher(multiplicityIdMapping)
        matcher.addQuerySetTransform(CaseIndexQuerySetTransform(caseIndexTable))
        queryPlanner.addQueryHandler(ModelQueryLookupHandler(matcher))
    }

    override fun getNumberOfBatchableKeysInProfileSet(profiles: ArrayList<PredicateProfile>): Int {
        var keysToBatch = 0
        //Otherwise see how many of these we can bulk process
        for (i in 0 until profiles.size) {
            //If the current key is an index fetch, we actually can't do it in bulk,
            //so we need to stop
            if (profiles[i].getKey().startsWith(Case.INDEX_CASE_INDEX_PRE) ||
                (profiles[i] !is IndexedValueLookup &&
                        profiles[i] !is NegativeIndexedValueLookup)
            ) {
                break
            }
            keysToBatch++
        }
        return keysToBatch
    }

    override fun getChildTemplate(): CaseChildElement {
        return CaseChildElement.buildCaseChildTemplate(this)
    }

    override fun translateFilterExpr(
        expressionTemplate: XPathPathExpr,
        matchingExpr: XPathPathExpr,
        indices: HashMap<XPathPathExpr, String>
    ): String? {
        var filter = super.translateFilterExpr(expressionTemplate, matchingExpr, indices)

        //If we're matching a case index, we've got some magic to take care of. First,
        //generate the expected case ID
        if (expressionTemplate === CASE_INDEX_EXPR) {
            filter = filter + matchingExpr.steps[1].name!!.name
        }

        return filter
    }

    override fun getStorageIndexMap(): HashMap<XPathPathExpr, String> {
        val indices = HashMap<XPathPathExpr, String>()

        //TODO: Much better matching
        indices[CASE_ID_EXPR] = Case.INDEX_CASE_ID
        indices[CASE_ID_EXPR_TWO] = Case.INDEX_CASE_ID
        indices[CASE_TYPE_EXPR] = Case.INDEX_CASE_TYPE
        indices[CASE_STATUS_EXPR] = Case.INDEX_CASE_STATUS
        indices[CASE_INDEX_EXPR] = Case.INDEX_CASE_INDEX_PRE
        indices[OWNER_ID_EXPR] = Case.INDEX_OWNER_ID
        indices[EXTERNAL_ID_EXPR] = Case.INDEX_EXTERNAL_ID
        indices[CATEGORY_EXPR] = Case.INDEX_CATEGORY
        indices[STATE_EXPR] = Case.INDEX_STATE

        return indices
    }

    override val storageCacheName: String?
        get() = MODEL_NAME

    override fun getCacheIndex(ref: TreeReference): String? {
        //NOTE: there's no evaluation here as to whether the ref is suitable
        //we only follow one pattern for now and it's evaluated below.

        loadElements()

        //Testing - Don't bother actually seeing whether this fits
        val i = ref.getMultiplicity(1)
        if (i != -1) {
            val idVal = this.multiplicityIdMapping[DataUtil.integer(i)]
            return idVal?.toString()
        }
        return null
    }

    override fun isReferencePatternCachable(ref: TreeReference): Boolean {
        //we only support one pattern here, a raw, qualified
        //reference to an element at the case level with no
        //predicate support. The ref basically has to be a raw
        //pointer to one of this instance's children
        if (!ref.isAbsolute) {
            return false
        }

        if (ref.hasPredicates()) {
            return false
        }
        if (ref.size() != 2) {
            return false
        }

        if (!"casedb".equals(ref.getName(0), ignoreCase = true)) {
            return false
        }
        if (!"case".equals(ref.getName(1), ignoreCase = true)) {
            return false
        }
        return ref.getMultiplicity(1) >= 0
    }

    override fun getCachePrimeGuess(): Array<Array<String>> {
        return mMostRecentBatchFetch!!
    }

    override fun getNextIndexMatch(
        profiles: ArrayList<PredicateProfile>,
        storage: IStorageUtilityIndexed<*>,
        currentQueryContext: QueryContext
    ): Collection<Int> {
        //If the index object starts with "case-in-" it's actually a case index query and we need to run
        //this over the case index table
        val firstKey = profiles[0].getKey()
        if (firstKey.startsWith(Case.INDEX_CASE_INDEX_PRE)) {
            return performCaseIndexQuery(firstKey, profiles)
        }
        return super.getNextIndexMatch(profiles, storage, currentQueryContext)
    }

    @Synchronized
    override fun loadElements() {
        if (elements != null) {
            return
        }
        elements = ArrayList()

        var mult = 0

        val i = storage.iterate(false)
        while (i.hasMore()) {
            val id = i.nextID()
            elements!!.add(buildElement(this, id, null, mult))
            objectIdMapping[DataUtil.integer(id)] = DataUtil.integer(mult)
            multiplicityIdMapping[DataUtil.integer(mult)] = DataUtil.integer(id)
            mult++
        }
    }

    private fun performCaseIndexQuery(
        firstKey: String,
        optimizations: ArrayList<PredicateProfile>
    ): LinkedHashSet<Int> {
        //CTS - March 9, 2015 - Introduced a small cache for child index queries here because they
        //are a frequent target of bulk operations like graphing which do multiple requests across the
        //same query.

        val op = optimizations[0]

        //TODO: This should likely be generalized for a number of other queries with bulk/nodeset
        //returns
        val indexName = firstKey.substring(Case.INDEX_CASE_INDEX_PRE.length)

        var indexCacheKey: String? = null

        var matchingCases: LinkedHashSet<Int>?

        if (op is IndexedValueLookup) {
            val value = op.value as String

            //TODO: Evaluate whether our indices could contain "|" but I don't imagine how they could.
            indexCacheKey = "$indexName|$value"

            //Check whether we've got a cache of this index.
            if (mIndexCache.containsKey(indexCacheKey)) {
                //remove the match from the inputs
                optimizations.removeAt(0)
                return mIndexCache[indexCacheKey]!!
            }

            matchingCases = caseIndexTable!!.getCasesMatchingIndex(indexName, value)
        } else if (op is IndexedSetMemberLookup) {
            matchingCases = caseIndexTable!!.getCasesMatchingValueSet(indexName, op.valueSet)
        } else {
            throw IllegalArgumentException("No optimization path found for optimization type")
        }

        //Clear the most recent index and wipe it, because there is no way it is going to be useful
        //after this
        mMostRecentBatchFetch = arrayOf(emptyArray(), emptyArray(), emptyArray(), emptyArray())

        //remove the match from the inputs
        optimizations.removeAt(0)

        if (indexCacheKey != null) {
            //For now we're only going to run this on very small data sets because we don't
            //want to manage this too explicitly until we generalize. Almost all results here
            //will be very very small either way (~O(10's of cases)), so given that this only
            //exists across one session that won't get out of hand
            if (matchingCases.size < 50) {
                //Should never hit this, but don't wanna have any runaway memory if we do.
                if (mIndexCache.size > 100) {
                    mIndexCache.clear()
                }

                mIndexCache[indexCacheKey] = matchingCases
            }
        }
        return matchingCases
    }

    companion object {
        const val MODEL_NAME = "casedb"

        //Xpath parsing is sllllllloooooooowwwwwww
        @JvmField
        val CASE_ID_EXPR: XPathPathExpr = XPathReference.getPathExpr("@case_id")
        @JvmField
        val CASE_ID_EXPR_TWO: XPathPathExpr = XPathReference.getPathExpr("./@case_id")
        private val CASE_TYPE_EXPR: XPathPathExpr = XPathReference.getPathExpr("@case_type")
        private val CASE_STATUS_EXPR: XPathPathExpr = XPathReference.getPathExpr("@status")
        private val CASE_INDEX_EXPR: XPathPathExpr = XPathReference.getPathExpr("index/*")
        private val OWNER_ID_EXPR: XPathPathExpr = XPathReference.getPathExpr("@owner_id")
        private val EXTERNAL_ID_EXPR: XPathPathExpr = XPathReference.getPathExpr("@external_id")
        private val CATEGORY_EXPR: XPathPathExpr = XPathReference.getPathExpr("@category")
        private val STATE_EXPR: XPathPathExpr = XPathReference.getPathExpr("@state")
    }
}
