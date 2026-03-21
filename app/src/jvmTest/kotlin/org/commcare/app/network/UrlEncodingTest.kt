package org.commcare.app.network

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for the formUrlEncode utility function.
 *
 * Verifies RFC 3986 percent-encoding behavior: unreserved characters pass through,
 * spaces become '+', and everything else is percent-encoded with uppercase hex digits.
 */
class UrlEncodingTest {

    @Test
    fun testUnreservedCharacters_notEncoded() {
        // Letters, digits, and the four unreserved characters: - _ . ~
        assertEquals("abcXYZ", formUrlEncode("abcXYZ"))
        assertEquals("0123456789", formUrlEncode("0123456789"))
        assertEquals("-_.~", formUrlEncode("-_.~"))
    }

    @Test
    fun testEmptyString() {
        assertEquals("", formUrlEncode(""))
    }

    @Test
    fun testSpaces_encodedAsPlus() {
        assertEquals("hello+world", formUrlEncode("hello world"))
        assertEquals("+++", formUrlEncode("   "))
    }

    @Test
    fun testAtSign_percentEncoded() {
        assertEquals("user%40example.com", formUrlEncode("user@example.com"))
    }

    @Test
    fun testSpecialCharacters_percentEncoded() {
        assertEquals("%21", formUrlEncode("!"))
        assertEquals("%23", formUrlEncode("#"))
        assertEquals("%24", formUrlEncode("$"))
        assertEquals("%25", formUrlEncode("%"))
        assertEquals("%26", formUrlEncode("&"))
        assertEquals("%27", formUrlEncode("'"))
        assertEquals("%2B", formUrlEncode("+"))
        assertEquals("%2F", formUrlEncode("/"))
        assertEquals("%3A", formUrlEncode(":"))
        assertEquals("%3D", formUrlEncode("="))
        assertEquals("%3F", formUrlEncode("?"))
    }

    @Test
    fun testMixedContent() {
        assertEquals("grant_type%3Dpassword", formUrlEncode("grant_type=password"))
        assertEquals("p%40ss+w0rd%21", formUrlEncode("p@ss w0rd!"))
    }

    @Test
    fun testOAuthClientId_passesThrough() {
        // The client ID contains only unreserved characters
        val clientId = "zqFUtAAMrxmjnC1Ji74KAa6ZpY1mZly0J0PlalIa"
        assertEquals(clientId, formUrlEncode(clientId))
    }

    @Test
    fun testUnicodeCharacters_percentEncodedAsUtf8() {
        // e-acute (U+00E9) encodes to UTF-8 bytes C3 A9
        assertEquals("%C3%A9", formUrlEncode("\u00e9"))
        // CJK character (U+4E16) encodes to UTF-8 bytes E4 B8 96
        assertEquals("%E4%B8%96", formUrlEncode("\u4e16"))
    }

    @Test
    fun testHexDigitsAreUppercase() {
        // Verify that percent-encoding uses uppercase hex (A-F not a-f)
        val encoded = formUrlEncode("@")  // @ = 0x40
        assertEquals("%40", encoded)
        // Check a byte that has a-f in hex: [ = 0x5B
        val encoded2 = formUrlEncode("[")
        assertEquals("%5B", encoded2)
    }

    @Test
    fun testSlashAndQueryString() {
        assertEquals("https%3A%2F%2Fexample.com%2Fpath%3Fkey%3Dvalue", formUrlEncode("https://example.com/path?key=value"))
    }

    @Test
    fun testFormBodySegment() {
        // Simulate encoding a typical form body value with special chars
        val username = "user+name@test.com"
        val encoded = formUrlEncode(username)
        assertEquals("user%2Bname%40test.com", encoded)
    }
}
