package org.commcare.cases.instance

import org.commcare.cases.model.StorageIndexedTreeElementModel
import org.commcare.cases.query.QueryContext
import org.commcare.cases.query.QuerySensitive
import org.commcare.cases.query.ScopeLimitedReferenceRequestCache
import org.commcare.modern.engine.cases.RecordObjectCache
import org.commcare.modern.engine.cases.RecordSetResultCache
import org.commcare.modern.util.Pair
import org.javarosa.core.model.data.UncastData
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.model.xform.XPathReference

/**
 * Child TreeElement of an indexed fixture whose data is loaded from a DB.
 *
 * i.e. 'product' of "instance('product-list')/products/product"
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
class IndexedFixtureChildElement internal constructor(
    parent: StorageInstanceTreeElement<StorageIndexedTreeElementModel, *>,
    mult: Int,
    recordId: Int
) : StorageBackedChildElement<StorageIndexedTreeElementModel>(
    parent, mult, recordId, parent.getName()!!, parent.getChildHintName()
), QuerySensitive {

    private var empty: TreeElement? = null

    override fun cache(context: QueryContext?): TreeElement {
        if (recordId == TreeReference.INDEX_TEMPLATE) {
            return empty!!
        }

        synchronized(parent.treeCache) {
            val partialMatch = detectAndProcessLimitedScopeResponse(recordId, context)
            if (partialMatch != null) {
                return partialMatch
            }

            val element = parent.treeCache.retrieve(recordId)
            if (element != null) {
                return element
            }

            val model = parent.getElement(recordId, context)
            val cacheBuilder = buildElementFromModel(model)

            parent.treeCache.register(recordId, cacheBuilder)

            return cacheBuilder
        }
    }

    /**
     * Identifies whether in the current context it is potentially the case that a "partial"
     * response is acceptable, and builds the response if so.
     *
     * Returns null if that strategy is not applicable or if a partial response could not
     * be generated
     */
    private fun detectAndProcessLimitedScopeResponse(recordId: Int, context: QueryContext?): TreeElement? {
        if (context == null) {
            return null
        }
        val cache = context.getQueryCacheOrNull(ScopeLimitedReferenceRequestCache::class)
            ?: return null

        //If cache already contains partial match, return it here...
        val instanceName = this.getInstanceName() ?: return null
        val cachedPartialMatch = cache.getCachedElementIfExists(instanceName, recordId)
        if (cachedPartialMatch != null) {
            return cachedPartialMatch
        }

        if (!cache.isInstancePotentiallyScopeLimited(instanceName)) {
            return null
        }

        val scopeSufficientColumnList = getScopeSufficientColumnListIfExists(cache) ?: return null

        val objectMetadata = getElementMetadata(recordId, scopeSufficientColumnList, context)

        val partialMatch = this.buildPartialElementFromMetadata(scopeSufficientColumnList, objectMetadata)
        cache.cacheElement(instanceName, recordId, partialMatch)
        return partialMatch
    }

    internal fun getElementMetadata(recordId: Int, metaFields: Array<String>, context: QueryContext?): Array<String> {
        if (context == null || this.parent.storageCacheName == null) {
            return readSingleRecordMetadataFromStorage(recordId, metaFields, context)
        }

        val recordObjectCache: RecordObjectCache<Array<String>>? =
            StorageInstanceTreeElement.getRecordObjectCacheIfRelevant(context)
        val recordObjectKey = parent.storageCacheName + "_partial"

        //If no object cache is available, perform the read naively
        if (recordObjectCache == null) {
            return readSingleRecordMetadataFromStorage(recordId, metaFields, context)
        }

        //If the object is already cached, return it from there.
        if (recordObjectCache.isLoaded(recordObjectKey, recordId)) {
            return recordObjectCache.getLoadedRecordObject(recordObjectKey, recordId)!!
        }

        //Otherwise, see if we have a record set result which can be used to load the record in
        //bulk along with other records.
        val recordSetCache = context.getQueryCacheOrNull(RecordSetResultCache::class)

        val recordSetKey = parent.storageCacheName

        if (!StorageInstanceTreeElement.canLoadRecordFromGroup(recordSetCache, recordSetKey, recordId)) {
            return readSingleRecordMetadataFromStorage(recordId, metaFields, context)
        }

        return populateMetaDataCacheAndReadForRecord(
            recordSetCache!!, recordSetKey!!, recordObjectCache, recordObjectKey, metaFields, context
        )
    }

    private fun populateMetaDataCacheAndReadForRecord(
        recordSetCache: RecordSetResultCache,
        recordSetKey: String,
        recordObjectCache: RecordObjectCache<Array<String>>,
        recordObjectKey: String,
        metaFields: Array<String>,
        context: QueryContext
    ): Array<String> {
        val tranche = recordSetCache.getRecordSetForRecordId(recordSetKey, recordId)!!

        val loadTrace = EvaluationTrace(
            "Model [$recordObjectKey]: Limited Scope Partial Bulk Load [${tranche.first}}"
        )

        val body = tranche.second
        parent.getStorage().bulkReadMetadata(body, metaFields, recordObjectCache.getLoadedCaseMap(recordObjectKey))
        loadTrace.setOutcome("Loaded: " + body.size)

        context.reportTrace(loadTrace)

        return recordObjectCache.getLoadedRecordObject(recordObjectKey, recordId)!!
    }

    private fun readSingleRecordMetadataFromStorage(
        recordId: Int,
        metaFields: Array<String>,
        context: QueryContext?
    ): Array<String> {
        val trace = EvaluationTrace(
            "Model [${this.parent.storageCacheName}]: Single Metadata Load"
        )

        val result = parent.storage.getMetaDataForRecord(recordId, metaFields)

        trace.setOutcome(recordId.toString())

        context?.reportTrace(trace)

        return result
    }

    /**
     * This method identifies whether the provided limited reference scope can be serviced
     * by a fixed set of meta data fields, rather than by expanding the subdocument
     * entirely.
     *
     * This method will update the cache with the result as well, so it is safe to call this method
     * multiple times against it without concern for recomputing the request.
     *
     * @return A list of meta data field ids which need to be loaded for this element to fulfill
     * the limited scope request. Null if this fixture can't guarantee that the limited request can
     * be fulfilled entirely with meta data fields.
     */
    private fun getScopeSufficientColumnListIfExists(cache: ScopeLimitedReferenceRequestCache): Array<String>? {
        val instName = this.getInstanceName() ?: return null
        val limitedScope = cache.getInternalScopedLimit(instName)
        if (limitedScope != null) {
            return limitedScope
        }

        //If we don't already have that list, build it (or detect that it's won't be possible and tell
        //the cache to not try.

        val referencesInScope = cache.getInScopeReferences(instName) ?: return null

        val model = parent.getModelTemplate()

        val baseRefForChildElement = this.getRef().genericize()

        val sufficientColumns = filterMetaDataForReferences(model, referencesInScope, baseRefForChildElement)

        if (sufficientColumns == null) {
            cache.setScopeLimitUnhelpful(instName)
            return null
        }

        val columnList = Array(sufficientColumns.size) { "" }
        var i = 0
        for (s in sufficientColumns) {
            columnList[i] = s
            i++
        }

        cache.setInternalScopeLimit(instName, columnList)

        return columnList
    }

    /**
     * Identifies whether the provided references all are references to metadata for the provided
     * model, and returns a list of the metadata fields which are being referenced if so. If the
     * list of references reference any data which isn't included in the model's metadata, null
     * is returned.
     */
    private fun filterMetaDataForReferences(
        model: StorageIndexedTreeElementModel,
        referencesInScope: Set<TreeReference>,
        baseRefForChildElement: TreeReference
    ): HashSet<String>? {
        val relativeSteps = model.getIndexedTreeReferenceSteps() ?: return null
        val stepToColumnName = HashMap<TreeReference, String>()

        for (relativeStep in relativeSteps) {
            stepToColumnName[XPathReference.getPathExpr(relativeStep).getReference()] =
                StorageIndexedTreeElementModel.getSqlColumnNameFromElementOrAttribute(relativeStep)
        }

        val columnNameCacheLoads = HashSet<String>()

        for (inScopeReference in referencesInScope) {
            //raw references to this node are expected if predicates exist, and don't require
            //specific reads
            if (inScopeReference == baseRefForChildElement) {
                continue
            }

            val subReference = inScopeReference.relativize(baseRefForChildElement)
            if (!stepToColumnName.containsKey(subReference)) {
                return null
            } else {
                columnNameCacheLoads.add(stepToColumnName[subReference]!!)
            }
        }
        return columnNameCacheLoads
    }

    private fun buildPartialElementFromMetadata(columnNames: Array<String>, metadataValues: Array<String>): TreeElement {
        val partial = TreeElement(parent.getChildHintName())
        partial.setMult(_mult)
        partial.setParent(this.parent)

        for (i in columnNames.indices) {
            val columnName = columnNames[i]
            val value = metadataValues[i]

            val metadataName = StorageIndexedTreeElementModel
                .getElementOrAttributeFromSqlColumnName(columnName)

            if (metadataName.startsWith("@")) {
                partial.setAttribute(null, metadataName.substring(1), value)
            } else {
                val child = TreeElement(metadataName)
                child.setValue(UncastData(value))
                partial.addChild(child)
            }
        }
        return partial
    }

    private fun buildElementFromModel(model: StorageIndexedTreeElementModel): TreeElement {
        val cacheBuilder = model.getRoot()!!
        entityId = model.getEntityId()
        cacheBuilder.setMult(_mult)
        cacheBuilder.setParent(this.parent)

        return cacheBuilder
    }

    override fun getName(): String? {
        return nameId
    }

    override fun prepareForUseInCurrentContext(queryContext: QueryContext) {
        cache(queryContext)
    }

    companion object {
        @JvmStatic
        fun buildFixtureChildTemplate(parent: IndexedFixtureInstanceTreeElement): IndexedFixtureChildElement {
            val template = IndexedFixtureChildElement(parent, TreeReference.INDEX_TEMPLATE, TreeReference.INDEX_TEMPLATE)

            val modelTemplate = parent.getModelTemplate()
            // NOTE PLM: do we need to do more to convert a regular TreeElement into a template?
            template.empty = modelTemplate.getRoot()
            template.empty!!.setMult(TreeReference.INDEX_TEMPLATE)
            return template
        }
    }
}
