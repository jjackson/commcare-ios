package org.commcare.cases.query

/**
 * A QueryCacheHost is a lifecycle object associated with a particular QueryContext.
 *
 * The goal of a query cache is to establish a lifecycle for data used in potentially very-intense
 * queries which can be terminated once those intense queries are over.
 *
 * Whenever a QueryContext "escalates" to a new context (which denotes that the new context will be
 * doing a lot of work) a new cache is created for the child context. When optimizations look
 * for cached data they'll find it in the "earliest" context that it was originally requested.
 *
 * This lets nested queries which execute over large datasets maintain their own query caches
 * around the large dataset without injecting that cache data into the original (small N) parent
 * context so that once the large dataset query is over, the small N dataset doesn't keep the
 * memory reserved as it proceeds.
 *
 * Created by ctsims on 1/26/2017.
 */
open class QueryCacheHost @JvmOverloads constructor(
    internal var parent: QueryCacheHost? = null
) {

    internal var cacheEntries: HashMap<Class<*>, QueryCache> = HashMap()

    /**
     * Gets a usable copy of the query cache type provided, and if one does not exist,
     * create one at the current cache level.
     */
    fun <T : QueryCache> getQueryCache(cacheType: Class<T>): T {
        val t = getQueryCacheOrNull(cacheType)
        if (t != null) {
            return t
        }
        try {
            val newCache = cacheType.newInstance()
            cacheEntries[cacheType] = newCache
            return newCache
        } catch (e: InstantiationException) {
            throw RuntimeException("Couldn't create cache $cacheType", e)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Couldn't create cache $cacheType", e)
        }
    }

    /**
     * Get the query cache object provided if one has been created in the current context, or any
     * parent contexts. If not, return null and do not instantiate a new query cache.
     */
    fun <T : QueryCache> getQueryCacheOrNull(cacheType: Class<T>): T? {
        return if (cacheEntries.containsKey(cacheType)) {
            @Suppress("UNCHECKED_CAST")
            cacheEntries[cacheType] as T
        } else {
            parent?.getQueryCacheOrNull(cacheType)
        }
    }
}
