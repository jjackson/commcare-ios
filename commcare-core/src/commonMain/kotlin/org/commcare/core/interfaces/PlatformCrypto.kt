package org.commcare.core.interfaces

/**
 * Cross-platform cryptography interface.
 * Replaces javax.crypto and java.security for KMP compatibility.
 */
expect object PlatformCrypto {
    /**
     * Generate a SHA-256 hash of the input bytes.
     */
    fun sha256(data: ByteArray): ByteArray

    /**
     * Generate an MD5 hash of the input bytes.
     */
    fun md5(data: ByteArray): ByteArray

    /**
     * Generate a random byte array of the given size.
     */
    fun randomBytes(size: Int): ByteArray

    /**
     * AES encrypt data with the given key.
     * Returns IV prepended to ciphertext.
     */
    fun aesEncrypt(data: ByteArray, key: ByteArray): ByteArray

    /**
     * AES decrypt data with the given key.
     * Expects IV prepended to ciphertext (as produced by aesEncrypt).
     */
    fun aesDecrypt(data: ByteArray, key: ByteArray): ByteArray

    /**
     * Generate an AES key of the given bit length (128, 192, or 256).
     */
    fun generateAesKey(bits: Int = 256): ByteArray

    /**
     * Derive a key from a password and salt using PBKDF2-HMAC-SHA256.
     * @param password the password to derive from
     * @param salt random salt bytes (should be at least 16 bytes)
     * @param iterations number of PBKDF2 iterations (recommend ≥ 100,000)
     * @param keyLengthBytes desired key length in bytes (e.g. 32 for AES-256)
     * @return derived key bytes
     */
    fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyLengthBytes: Int): ByteArray
}
