package org.commcare.cases.entity

import org.javarosa.core.util.platformSynchronized
import org.commcare.cases.entity.EntityLoadingProgressListener.EntityLoadingProgressPhase.PHASE_UNCACHED_CALCULATION
import org.commcare.suite.model.Detail
import org.commcare.suite.model.DetailField
import org.javarosa.core.model.condition.EvaluationContext
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.utils.CacheHost
import org.javarosa.core.util.OrderedHashtable
import org.javarosa.xpath.expr.XPathExpression

/**
 * @author ctsims
 */
class AsyncNodeEntityFactory(
    d: Detail,
    ec: EvaluationContext,
    private val mEntityCache: EntityStorageCache?,
    private val inBackground: Boolean
) : NodeEntityFactory(d, ec) {

    private val mVariableDeclarations: OrderedHashtable<String, XPathExpression> = detail.variableDeclarations
    private val mEntitySet: HashMap<String, AsyncEntity> = HashMap()

    private var mCacheHost: CacheHost? = null
    private var mTemplateIsCachable: Boolean? = null
    private var mAsyncPrimingThread: Thread? = null

    // Don't show entity list until we primeCache and caches all fields
    private val isBlockingAsyncMode: Boolean = detail.hasSortField()

    override fun getEntity(data: TreeReference): Entity<TreeReference> {
        val nodeContext = EvaluationContext(ec, data)

        mCacheHost = nodeContext.getCacheHost(data)

        var cacheIndex: String? = null
        if (mTemplateIsCachable == null) {
            val host = mCacheHost
            mTemplateIsCachable = host != null && host.isReferencePatternCachable(data)
        }
        if (mTemplateIsCachable == true && mCacheHost != null) {
            cacheIndex = mCacheHost!!.getCacheIndex(data)
        }

        val entityKey = loadCalloutDataMapKey(nodeContext)
        val entity = AsyncEntity(detail, nodeContext, data, mVariableDeclarations,
            mEntityCache, cacheIndex, entityKey)

        if (cacheIndex != null) {
            mEntitySet[cacheIndex] = entity
        }
        return entity
    }

    override fun setEvaluationContextDefaultQuerySet(
        ec: EvaluationContext,
        result: List<TreeReference>
    ) {
        // Don't do anything for asynchronous lists. In theory the query set could help expand the
        // first cache more quickly, but otherwise it's just keeping around tons of cases in memory
        // that don't even need to be loaded.
    }

    /**
     * Bulk loads search field cache from db.
     * Note that the cache is lazily built upon first case list search.
     */
    protected fun primeCache() {
        if (isCancelled) return
        val templateCachable = mTemplateIsCachable
        if (mEntityCache == null || templateCachable == null || !templateCachable || mCacheHost == null) {
            return
        }

        val cachePrimeKeys = mCacheHost!!.getCachePrimeGuess() ?: return
        updateProgress(EntityLoadingProgressListener.EntityLoadingProgressPhase.PHASE_CACHING, 0, 100)
        mEntityCache.primeCache(mEntitySet, cachePrimeKeys, detail)
        updateProgress(EntityLoadingProgressListener.EntityLoadingProgressPhase.PHASE_CACHING, 100, 100)
    }

    private fun updateProgress(
        phase: EntityLoadingProgressListener.EntityLoadingProgressPhase,
        progress: Int,
        total: Int
    ) {
        progressListener?.publishEntityLoadingProgress(phase, progress, total)
    }

    override fun prepareEntitiesInternal(entities: List<Entity<TreeReference>>) {
        // Legacy cache and index code, only here to maintain backward compatibility
        // if blocking mode load cache on the same thread and set any data thats not cached
        if (isBlockingAsyncMode) {
            primeCache()
            setUnCachedDataOld(entities)
        } else {
            // otherwise we want to show the entity list asap and hence want to offload the loading cache part to a separate
            // thread while caching any uncached data later on UI thread during Adapter's getView
            platformSynchronized(mAsyncLock) {
                if (mAsyncPrimingThread == null) {
                    mAsyncPrimingThread = Thread { primeCache() }
                    mAsyncPrimingThread!!.start()
                }
            }
        }
    }

    override fun cacheEntities(entities: List<Entity<TreeReference>>) {
        if (detail.isCacheEnabled) {
            primeCache()
            setUnCachedData(entities)
        } else {
            primeCache()
            setUnCachedDataOld(entities)
        }
    }

    protected fun setUnCachedData(entities: List<Entity<TreeReference>>) {
        val foregroundWithLazyLoading = !inBackground && detail.isLazyLoading
        val foregroundWithoutLazyLoading = !inBackground && !detail.isLazyLoading
        for (i in entities.indices) {
            if (isCancelled) return
            val e = entities[i] as AsyncEntity
            for (col in 0 until e.getNumFields()) {
                val field: DetailField = detail.fields[col]
                /*
                 * 1. If we are in foreground with lazy loading turned on, the priority is to show
                 * the user screen asap. Therefore, we need to skip calculating lazy fields.
                 * 2. If we are in foreground with lazy loading turned off, we want to calculate all fields here.
                 * 3. If we are in background with lazy loading turned on or off, we want to calculate all fields
                 * backed by cache in order to keep them ready for when user loads the list.
                 */
                if (foregroundWithoutLazyLoading || (foregroundWithLazyLoading && !field.isLazyLoading) || (
                            inBackground && field.isCacheEnabled)
                ) {
                    e.getField(col)
                    if (field.sort != null) {
                        e.getSortField(col)
                    }
                }
            }
            if (i % 100 == 0) {
                updateProgress(PHASE_UNCACHED_CALCULATION, i, entities.size)
            }
        }
        updateProgress(PHASE_UNCACHED_CALCULATION, entities.size, entities.size)
    }

    // Old cache and index pathway where we only cache sort fields
    @Deprecated("")
    protected fun setUnCachedDataOld(entities: List<Entity<TreeReference>>) {
        for (i in entities.indices) {
            if (isCancelled) return
            val e = entities[i] as AsyncEntity
            for (col in 0 until e.getNumFields()) {
                e.getSortField(col)
            }
            updateProgress(PHASE_UNCACHED_CALCULATION, i, entities.size)
        }
    }

    override fun isEntitySetReadyInternal(): Boolean {
        platformSynchronized(mAsyncLock) {
            return mAsyncPrimingThread == null || !mAsyncPrimingThread!!.isAlive
        }
    }

    fun isBlockingAsyncMode(): Boolean {
        return isBlockingAsyncMode
    }

    companion object {
        private val mAsyncLock = Any()
    }
}
