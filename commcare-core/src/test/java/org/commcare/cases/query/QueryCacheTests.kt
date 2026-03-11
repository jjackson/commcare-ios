package org.commcare.cases.query

import org.junit.Assert
import org.junit.Before
import org.junit.Test

/**
 * Created by ctsims on 12/14/2016.
 */
class QueryCacheTests {
    lateinit var grandparent: QueryCacheHost
    lateinit var parent: QueryCacheHost
    lateinit var child: QueryCacheHost

    val key = "key"
    val value = "value"

    @Before
    fun setup() {
        grandparent = QueryCacheHost()
        parent = QueryCacheHost(grandparent)
        child = QueryCacheHost(parent)
    }

    @Test
    fun testGrandparentFetch() {
        val cache = grandparent.getQueryCache(TestCache::class) { TestCache() }
        init(cache)

        Assert.assertEquals("Basic cache fetch", value, cache.hashmap[key])
        Assert.assertEquals("parent fetch", value, parent.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
        Assert.assertEquals("child fetch", value, child.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
    }

    @Test
    fun testParentFetch() {
        val cache = parent.getQueryCache(TestCache::class) { TestCache() }
        init(cache)

        Assert.assertEquals("grandparent fetch", null, grandparent.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
        Assert.assertEquals("parent fetch", value, parent.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
        Assert.assertEquals("child fetch", value, child.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
    }

    @Test
    fun testChildFetch() {
        val cache = child.getQueryCache(TestCache::class) { TestCache() }
        init(cache)

        Assert.assertEquals("grandparent fetch", null, grandparent.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
        Assert.assertEquals("parent fetch", null, parent.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
        Assert.assertEquals("child fetch", value, child.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
    }

    @Test
    fun testMultipleCaches() {
        val cache = parent.getQueryCache(TestCache::class) { TestCache() }
        init(cache)

        val cacheTwo = parent.getQueryCache(TestCacheTwo::class) { TestCacheTwo() }
        cacheTwo.hashmap[key] = "value2"

        Assert.assertEquals("grandparent fetch", null, grandparent.getQueryCache(TestCache::class) { TestCache() }.hashmap[key])
        Assert.assertEquals("parent fetch", "value2", parent.getQueryCache(TestCacheTwo::class) { TestCacheTwo() }.hashmap[key])
        Assert.assertEquals("child fetch", "value2", child.getQueryCache(TestCacheTwo::class) { TestCacheTwo() }.hashmap[key])
    }

    private fun init(cache: TestCache) {
        cache.hashmap[key] = value
    }

    class TestCache : QueryCache {
        val hashmap = HashMap<String, String>()
    }

    class TestCacheTwo : QueryCache {
        val hashmap = HashMap<String, String>()
    }
}
