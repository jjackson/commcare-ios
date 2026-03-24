package org.commcare.app.perf

import org.commcare.core.interfaces.PlatformCrypto
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Performance benchmarks for platform cryptography operations.
 *
 * These tests establish baseline timing for AES-256-GCM encryption/decryption,
 * PBKDF2 key derivation, and SHA-256 hashing — the operations used for user
 * key records, encrypted storage, and password verification in CommCare.
 *
 * Each test prints a BENCHMARK line for CI log parsing and asserts a generous
 * upper bound so the test only fails on severe regressions.
 */
class CryptoBenchmark {

    @Test
    fun benchmarkAesEncrypt1KB() {
        val key = PlatformCrypto.generateAesKey(256)
        val data = ByteArray(1024) { it.toByte() }

        // Warm up JIT
        repeat(10) { PlatformCrypto.aesEncrypt(data, key) }

        val ms = measureTimeMillis {
            repeat(1000) { PlatformCrypto.aesEncrypt(data, key) }
        }
        println("BENCHMARK: 1000x AES-256-GCM encrypt 1KB in ${ms}ms (${ms / 1000.0}ms/op)")
        assertTrue(ms < 30000, "1000 AES encryptions should complete within 30s, took ${ms}ms")
    }

    @Test
    fun benchmarkAesDecrypt1KB() {
        val key = PlatformCrypto.generateAesKey(256)
        val data = ByteArray(1024) { it.toByte() }
        val encrypted = PlatformCrypto.aesEncrypt(data, key)

        // Warm up JIT
        repeat(10) { PlatformCrypto.aesDecrypt(encrypted, key) }

        val ms = measureTimeMillis {
            repeat(1000) { PlatformCrypto.aesDecrypt(encrypted, key) }
        }
        println("BENCHMARK: 1000x AES-256-GCM decrypt 1KB in ${ms}ms (${ms / 1000.0}ms/op)")
        assertTrue(ms < 30000, "1000 AES decryptions should complete within 30s, took ${ms}ms")
    }

    @Test
    fun benchmarkAesRoundTrip10KB() {
        val key = PlatformCrypto.generateAesKey(256)
        val data = ByteArray(10_240) { it.toByte() }

        // Warm up
        repeat(5) {
            val enc = PlatformCrypto.aesEncrypt(data, key)
            PlatformCrypto.aesDecrypt(enc, key)
        }

        val ms = measureTimeMillis {
            repeat(500) {
                val enc = PlatformCrypto.aesEncrypt(data, key)
                PlatformCrypto.aesDecrypt(enc, key)
            }
        }
        println("BENCHMARK: 500x AES-256-GCM roundtrip 10KB in ${ms}ms (${ms / 500.0}ms/op)")
        assertTrue(ms < 30000, "500 AES roundtrips of 10KB should complete within 30s, took ${ms}ms")
    }

    @Test
    fun benchmarkPbkdf2_100k() {
        val salt = "user@domain.com".encodeToByteArray()

        val ms = measureTimeMillis {
            PlatformCrypto.pbkdf2("testpassword", salt, 100_000, 32)
        }
        println("BENCHMARK: PBKDF2 100K iterations in ${ms}ms")
        assertTrue(ms < 10000, "PBKDF2 100K iterations should complete within 10s, took ${ms}ms")
    }

    @Test
    fun benchmarkSha256() {
        val data = ByteArray(10_000) { it.toByte() }

        // Warm up
        repeat(100) { PlatformCrypto.sha256(data) }

        val ms = measureTimeMillis {
            repeat(10_000) { PlatformCrypto.sha256(data) }
        }
        println("BENCHMARK: 10000x SHA-256 of 10KB in ${ms}ms (${ms / 10000.0}ms/op)")
        assertTrue(ms < 30000, "10000 SHA-256 hashes should complete within 30s, took ${ms}ms")
    }

    @Test
    fun benchmarkMd5() {
        val data = ByteArray(10_000) { it.toByte() }

        // Warm up
        repeat(100) { PlatformCrypto.md5(data) }

        val ms = measureTimeMillis {
            repeat(10_000) { PlatformCrypto.md5(data) }
        }
        println("BENCHMARK: 10000x MD5 of 10KB in ${ms}ms (${ms / 10000.0}ms/op)")
        assertTrue(ms < 30000, "10000 MD5 hashes should complete within 30s, took ${ms}ms")
    }
}
