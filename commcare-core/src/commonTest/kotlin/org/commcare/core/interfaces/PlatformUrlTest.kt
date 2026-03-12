package org.commcare.core.interfaces

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformUrlTest {

    @Test
    fun testBasicHttpUrl() {
        val url = PlatformUrl("https://www.commcarehq.org/a/demo/apps")
        assertEquals("https", url.scheme)
        assertEquals("www.commcarehq.org", url.host)
        assertEquals("/a/demo/apps", url.path)
        assertEquals(-1, url.port)
    }

    @Test
    fun testUrlWithPort() {
        val url = PlatformUrl("http://localhost:8000/api/v1")
        assertEquals("http", url.scheme)
        assertEquals("localhost", url.host)
        assertEquals(8000, url.port)
        assertEquals("/api/v1", url.path)
    }

    @Test
    fun testUrlWithQuery() {
        val url = PlatformUrl("https://example.com/search?q=test&page=1")
        assertEquals("https", url.scheme)
        assertEquals("example.com", url.host)
        assertEquals("/search", url.path)
        assertEquals("q=test&page=1", url.query)
    }

    @Test
    fun testIsValidUrl() {
        assertTrue(isValidUrl("https://example.com"))
        assertTrue(isValidUrl("http://localhost:8000/path"))
        assertFalse(isValidUrl("not a url"))
    }
}
