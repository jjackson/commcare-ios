package org.javarosa.core.util

/**
 * A Local Cache Table is a cache of objects keyed by a dynamic type.
 *
 * In commonMain, this is simply a typealias for CacheTable, which uses
 * platform-specific memory management (WeakReference on JVM, plain HashMap on iOS).
 */
typealias LocalCacheTable<T, K> = CacheTable<T, K>
