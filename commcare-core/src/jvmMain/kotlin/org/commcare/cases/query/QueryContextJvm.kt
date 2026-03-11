package org.commcare.cases.query

/**
 * Java-compatible overload that accepts Class<T> and uses reflection to create instances.
 * Prefer the KClass version with factory lambda for new Kotlin code.
 */
fun <T : QueryCache> QueryContext.getQueryCache(cacheType: Class<T>): T {
    return getQueryCache(cacheType.kotlin) {
        try {
            cacheType.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            throw RuntimeException("Couldn't create cache $cacheType", e)
        }
    }
}
