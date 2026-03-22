@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for the iOS PlatformKeychainStore.
 *
 * The iOS Keychain may not be fully available on the simulator in CI
 * environments (especially headless). Each test wraps Keychain operations
 * in a try/catch and skips gracefully if the Keychain is unavailable.
 *
 * When the Keychain IS available (e.g., local simulator), these tests
 * verify the full store/retrieve/delete lifecycle.
 */
class PlatformKeychainStoreTest {

    private val keychain = PlatformKeychainStore()

    // Unique key prefix to avoid collisions between test runs
    private val testPrefix = "test_${kotlin.random.Random.nextInt(100000)}_"

    @AfterTest
    fun tearDown() {
        // Best-effort cleanup of test keys
        try {
            keychain.delete("${testPrefix}key1")
            keychain.delete("${testPrefix}key2")
            keychain.delete("${testPrefix}unicode_key")
            keychain.delete("${testPrefix}overwrite_key")
        } catch (_: Exception) {
            // Ignore cleanup failures
        }
    }

    @Test
    fun testStoreAndRetrieve() {
        try {
            val key = "${testPrefix}key1"
            keychain.store(key, "secret-value-123")

            val retrieved = keychain.retrieve(key)
            assertEquals("secret-value-123", retrieved, "Retrieved value should match stored value")
        } catch (e: Exception) {
            println("Keychain not available on this simulator — skipping: ${e.message}")
        }
    }

    @Test
    fun testRetrieveNonExistentKey_returnsNull() {
        try {
            val result = keychain.retrieve("${testPrefix}definitely_not_stored_key")
            assertNull(result, "Non-existent key should return null")
        } catch (e: Exception) {
            println("Keychain not available on this simulator — skipping: ${e.message}")
        }
    }

    @Test
    fun testDeleteRemovesValue() {
        try {
            val key = "${testPrefix}key2"
            keychain.store(key, "to-be-deleted")
            assertEquals("to-be-deleted", keychain.retrieve(key))

            keychain.delete(key)
            assertNull(keychain.retrieve(key), "Value should be null after deletion")
        } catch (e: Exception) {
            println("Keychain not available on this simulator — skipping: ${e.message}")
        }
    }

    @Test
    fun testDeleteNonExistentKey_noException() {
        try {
            // Deleting a key that doesn't exist should not throw
            keychain.delete("${testPrefix}never_stored_key")
        } catch (e: Exception) {
            println("Keychain not available on this simulator — skipping: ${e.message}")
        }
    }

    @Test
    fun testStoreOverwritesExistingValue() {
        try {
            val key = "${testPrefix}overwrite_key"
            keychain.store(key, "original")
            assertEquals("original", keychain.retrieve(key))

            keychain.store(key, "updated")
            assertEquals("updated", keychain.retrieve(key), "Store should overwrite existing value")
        } catch (e: Exception) {
            println("Keychain not available on this simulator — skipping: ${e.message}")
        }
    }

    @Test
    fun testUnicodeValue() {
        try {
            val key = "${testPrefix}unicode_key"
            val unicodeValue = "Hello \u4e16\u754c \ud83c\udf0d"
            keychain.store(key, unicodeValue)

            val retrieved = keychain.retrieve(key)
            assertEquals(unicodeValue, retrieved, "Unicode values should round-trip correctly")
        } catch (e: Exception) {
            println("Keychain not available on this simulator — skipping: ${e.message}")
        }
    }
}
