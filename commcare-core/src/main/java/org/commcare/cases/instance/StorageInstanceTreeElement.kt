package org.commcare.cases.instance

import org.commcare.cases.query.QueryContext
import org.commcare.cases.util.StorageBackedTreeRoot
import org.commcare.modern.engine.cases.RecordObjectCache
import org.commcare.modern.engine.cases.RecordSetResultCache
import org.commcare.modern.util.Pair
import org.javarosa.core.model.data.IAnswerData
import org.javarosa.core.model.instance.AbstractTreeElement
import org.javarosa.core.model.instance.TreeElement
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.ITreeVisitor
import org.javarosa.core.model.trace.EvaluationTrace
import org.javarosa.core.services.storage.IStorageIterator
import org.javarosa.core.services.storage.IStorageUtilityIndexed
import org.javarosa.core.util.DataUtil
import org.javarosa.core.util.Interner
import org.javarosa.core.util.externalizable.Externalizable

/**
 * Instance root for storage-backed instances such as the case and ledger DBs
 *
 * @author Phillip Mates (pmates@dimagi.com)
 */
abstract class StorageInstanceTreeElement<Model : Externalizable, T : AbstractTreeElement>(
    private var instanceRoot: AbstractTreeElement?,
    internal val storage: IStorageUtilityIndexed<Model>,
    private val modelName: String,
    private val childName: String
) : StorageBackedTreeRoot<T>() {

    @JvmField
    internal var elements: ArrayList<T>? = null

    @JvmField
    internal val treeCache: Interner<TreeElement> = Interner()

    private var stringCache: Interner<String> = Interner()

    private var numRecords = -1
    private var cachedRef: TreeReference? = null

    /**
     * Rebase assigns this tree element to a new root instance node.
     *
     * Used to migrate the already created tree structure to a new instance connector.
     *
     * @param instanceRoot The root of the new tree that this element should be a part of
     */
    open fun rebase(instanceRoot: AbstractTreeElement?) {
        this.instanceRoot = instanceRoot
        expireCachedRef()
    }

    override val isLeaf: Boolean
        get() = false

    override val isChildable: Boolean
        get() = false

    override fun getInstanceName(): String? {
        return instanceRoot?.getInstanceName()
    }

    override fun getChild(name: String, multiplicity: Int): T? {
        if (multiplicity == TreeReference.INDEX_TEMPLATE && childName == name) {
            return getChildTemplate()
        }

        //name is always "case", so multiplicities are the only relevant component here
        if (childName == name) {
            loadElements()
            val elems = elements!!
            if (elems.isEmpty()) {
                //If we have no cases, we still need to be able to return a template element so as to not
                //break xpath evaluation
                return getChildTemplate()
            }
            return elems[multiplicity]
        }
        return null
    }

    @Synchronized
    protected open fun loadElements() {
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
            mult++
        }
    }

    override fun getChildrenWithName(name: String): ArrayList<AbstractTreeElement> {
        return if (name == childName) {
            loadElements()
            ArrayList<AbstractTreeElement>(elements)
        } else {
            ArrayList()
        }
    }

    override fun hasChildren(): Boolean {
        return getNumChildren() > 0
    }

    override fun getNumChildren(): Int {
        if (numRecords == -1) {
            numRecords = storage.getNumRecords()
        }
        return numRecords
    }

    override fun getChildAt(i: Int): T? {
        loadElements()
        return elements!![i]
    }

    override val isRepeatable: Boolean
        get() = false

    override val isAttribute: Boolean
        get() = false

    override fun getChildMultiplicity(name: String): Int {
        //All children have the same name;
        return if (name == childName) {
            this.getNumChildren()
        } else {
            0
        }
    }

    override val isRelevant: Boolean
        get() = true

    override fun accept(visitor: ITreeVisitor) {
        visitor.visit(this)
    }

    override fun getAttributeCount(): Int {
        return 0
    }

    override fun getAttributeNamespace(index: Int): String? {
        return null
    }

    override fun getAttributeName(index: Int): String? {
        return null
    }

    override fun getAttributeValue(index: Int): String? {
        return null
    }

    override fun getAttribute(namespace: String?, name: String): AbstractTreeElement? {
        return null
    }

    override fun getAttributeValue(namespace: String?, name: String): String? {
        return null
    }

    override fun getRef(): TreeReference {
        if (cachedRef == null) {
            cachedRef = TreeReference.buildRefFromTreeElement(this)
        }
        return cachedRef!!
    }

    override fun clearVolatiles() {
        cachedRef = null
        val elems = elements
        if (elems != null) {
            for (element in elems) {
                element.clearVolatiles()
            }
        }
    }

    private fun expireCachedRef() {
        cachedRef = null
    }

    override fun getName(): String? {
        return modelName
    }

    override fun getMult(): Int {
        return 0
    }

    override fun getParent(): AbstractTreeElement? {
        return instanceRoot
    }

    override fun getValue(): IAnswerData? {
        return null
    }

    override fun getDataType(): Int {
        return 0
    }

    override fun getNamespace(): String? {
        return null
    }

    public override fun getChildHintName(): String {
        return childName
    }

    public override fun getStorage(): IStorageUtilityIndexed<*> {
        return storage
    }

    override fun initStorageCache() {
        loadElements()
    }

    fun attachStringCache(stringCache: Interner<String>) {
        this.stringCache = stringCache
    }

    fun intern(s: String): String {
        return stringCache.intern(s) ?: s
    }

    protected abstract fun buildElement(
        storageInstance: StorageInstanceTreeElement<Model, T>,
        recordId: Int,
        id: String?,
        mult: Int
    ): T

    internal fun getElement(recordId: Int, context: QueryContext?): Model {
        if (context == null || storageCacheName == null) {
            return getElementSingular(recordId, context)
        }
        val recordSetCache = context.getQueryCacheOrNull(RecordSetResultCache::class)

        val storageCacheKey = storageCacheName!!

        val recordObjectCache: RecordObjectCache<Model>? = getRecordObjectCacheIfRelevant(context)

        if (recordObjectCache != null) {
            if (recordObjectCache.isLoaded(storageCacheKey, recordId)) {
                return recordObjectCache.getLoadedRecordObject(storageCacheKey, recordId)!!
            }

            if (canLoadRecordFromGroup(recordSetCache, storageCacheName, recordId)) {
                val tranche = recordSetCache!!.getRecordSetForRecordId(storageCacheKey, recordId)!!
                val loadTrace = EvaluationTrace(
                    "Model [${this.storageCacheName}]: Bulk Load [${tranche.first}}"
                )

                val body = tranche.second
                storage.bulkRead(body, recordObjectCache.getLoadedCaseMap(storageCacheKey))
                loadTrace.setOutcome("Loaded: " + body.size)
                context.reportTrace(loadTrace)

                return recordObjectCache.getLoadedRecordObject(storageCacheKey, recordId)!!
            }
        }

        return getElementSingular(recordId, context)
    }

    /**
     * Retrieves a model for the provided record ID using a guaranteed singular lookup from
     * storage. This is the "Safe" fallback behavior for lookups.
     */
    protected fun getElementSingular(recordId: Int, context: QueryContext?): Model {
        val trace = EvaluationTrace("Model [$storageCacheName]: Singular Load")

        val m = storage.read(recordId)

        trace.setOutcome(recordId.toString())
        context?.reportTrace(trace)
        return m
    }

    internal fun getModelTemplate(): Model {
        return storage.read(1)
    }

    protected abstract fun getChildTemplate(): T

    override fun toString(): String {
        return "$modelName - child: $childName - Children: ${getNumChildren()}"
    }

    companion object {
        @JvmStatic
        fun canLoadRecordFromGroup(
            recordSetCache: RecordSetResultCache?,
            cacheName: String?,
            recordId: Int
        ): Boolean {
            return recordSetCache != null && cacheName != null && recordSetCache.hasMatchingRecordSet(cacheName, recordId)
        }

        /**
         * Get a record object cache if it's appropriate in the current context.
         */
        @JvmStatic
        fun <T> getRecordObjectCacheIfRelevant(context: QueryContext): RecordObjectCache<T>? {
            // If the query isn't currently in a bulk mode, don't force an object cache to exist unless
            // it already does
            return if (context.getScope() < QueryContext.BULK_QUERY_THRESHOLD) {
                context.getQueryCacheOrNull(RecordObjectCache::class) as RecordObjectCache<T>?
            } else {
                context.getQueryCache(RecordObjectCache::class) { RecordObjectCache<T>() } as RecordObjectCache<T>?
            }
        }
    }
}
