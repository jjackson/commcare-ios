package org.commcare.modern.engine.cases

import org.commcare.cases.query.QueryCache

import java.util.HashMap

/**
 * A straightforward cache object query cache. Stores objects by their record ID.
 *
 * Used by other optimizations to isolate doing bulk loads and ensure that they are relevant
 * when they occur
 *
 * Created by ctsims on 6/22/2017.
 */
class RecordObjectCache<T> : QueryCache {

    private val caches = HashMap<String, HashMap<Int, T>>()

    fun isLoaded(storageSetID: String, recordId: Int): Boolean {
        return getCache(storageSetID).containsKey(recordId)
    }

    fun getLoadedCaseMap(storageSetID: String): HashMap<Int, T> {
        return getCache(storageSetID)
    }

    fun getLoadedRecordObject(storageSetID: String, recordId: Int): T? {
        return getCache(storageSetID)[recordId]
    }

    private fun getCache(storageSetID: String): HashMap<Int, T> {
        var cache: HashMap<Int, T>?
        if (!caches.containsKey(storageSetID)) {
            cache = HashMap()
            caches[storageSetID] = cache
        } else {
            cache = caches[storageSetID]
        }
        return cache!!
    }
}
