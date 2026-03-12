package org.javarosa.xpath.expr

/**
 * @author Aliza Stone
 */
class ExpressionCacher {

    private val cache: MutableMap<ExpressionCacheKey, Any> = HashMap()

    fun cache(cacheKey: ExpressionCacheKey, value: Any) {
        cache[cacheKey] = value
    }

    fun getCachedValue(cacheKey: ExpressionCacheKey): Any? {
        return cache[cacheKey]
    }
}
