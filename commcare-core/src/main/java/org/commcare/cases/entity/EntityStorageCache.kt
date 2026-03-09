package org.commcare.cases.entity

import org.commcare.suite.model.Detail
import java.io.Closeable
import java.util.Hashtable

/**
 * Interface for evaluated entity fields cache
 */
interface EntityStorageCache {
    enum class ValueType {
        TYPE_NORMAL_FIELD,
        TYPE_SORT_FIELD
    }

    fun lockCache(): Closeable

    fun getCacheKey(detailId: String, detailFieldIndex: String, valueType: ValueType): String

    fun retrieveCacheValue(cacheIndex: String, cacheKey: String): String?

    fun cache(cacheIndex: String, cacheKey: String?, data: String?)

    fun getFieldIdFromCacheKey(detailId: String, cacheKey: String): Int

    fun primeCache(entitySet: Hashtable<String, AsyncEntity>, cachePrimeKeys: Array<Array<String>>, detail: Detail)
}
