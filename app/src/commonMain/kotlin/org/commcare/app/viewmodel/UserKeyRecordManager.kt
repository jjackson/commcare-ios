package org.commcare.app.viewmodel

import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.platform.currentEpochSeconds
import org.commcare.app.storage.CommCareDatabase
import org.commcare.core.interfaces.PlatformCrypto

/**
 * Manages encrypted password storage and PIN verification for quick login.
 *
 * After a user's first successful password login, their password is encrypted
 * and stored locally. On subsequent logins, a PIN or biometric unlock decrypts
 * the password so it can be sent to the server for authentication.
 *
 * Encryption model: AES (GCM on JVM, CBC+HMAC on iOS) with a 256-bit device
 * key stored in the platform keychain. PIN hashes use PBKDF2-HMAC-SHA256 with
 * per-user salt.
 */
class UserKeyRecordManager(
    private val db: CommCareDatabase,
    private val keychainStore: PlatformKeychainStore
) {
    /** Login modes: PASSWORD (first time), PIN (has PIN set), BIOMETRIC (primed, no PIN). */
    enum class LoginMode { PASSWORD, PIN, BIOMETRIC }

    /**
     * Determine the appropriate login mode for a returning user.
     * Returns PASSWORD if no quick-login record exists.
     */
    fun getLoginMode(username: String, domain: String): LoginMode {
        val record = getRecord(username, domain) ?: return LoginMode.PASSWORD
        if (record.is_primed != 0L && record.encrypted_password != null) {
            return if (record.pin_hash != null) LoginMode.PIN else LoginMode.BIOMETRIC
        }
        return LoginMode.PASSWORD
    }

    /**
     * After a successful password login, encrypt and store the password
     * so that future logins can use PIN or biometric.
     */
    fun primeForQuickLogin(username: String, domain: String, password: String) {
        val key = getOrCreateDeviceKey()
        val encrypted = aesEncryptToHex(password, key)
        val now = currentEpochSeconds()
        db.commCareQueries.insertUserKeyRecord(
            username = username,
            domain = domain,
            encrypted_password = encrypted,
            pin_hash = null,
            is_primed = 1L,
            record_type = "normal",
            valid_from = now,
            valid_to = null,
            last_login = now
        )
    }

    /**
     * Set or update the PIN for quick login.
     * The PIN is stored as a PBKDF2-HMAC-SHA256 hash with per-user salt.
     */
    fun setPin(username: String, domain: String, pin: String) {
        val hash = hashPin(pin, username, domain)
        db.commCareQueries.updatePinHash(hash, username, domain)
    }

    /**
     * Verify a PIN and, if correct, return the stored decrypted password.
     * Returns null if the record is missing, the PIN hash doesn't match, or
     * the encrypted password cannot be decrypted.
     */
    fun verifyPinAndGetPassword(username: String, domain: String, pin: String): String? {
        val record = getRecord(username, domain) ?: return null
        val storedHash = record.pin_hash ?: return null
        if (hashPin(pin, username, domain) != storedHash) return null
        val key = getOrCreateDeviceKey()
        return aesDecryptFromHex(record.encrypted_password ?: return null, key)
    }

    /**
     * Return the stored decrypted password for biometric login (no PIN needed).
     * Returns null if the record is not primed or lacks an encrypted password.
     */
    fun getPasswordForBiometric(username: String, domain: String): String? {
        val record = getRecord(username, domain) ?: return null
        if (record.is_primed == 0L || record.encrypted_password == null) return null
        val key = getOrCreateDeviceKey()
        return aesDecryptFromHex(record.encrypted_password, key)
    }

    /**
     * Returns true if the user has a PIN set for quick login.
     */
    fun hasPinSet(username: String, domain: String): Boolean {
        val record = getRecord(username, domain) ?: return false
        return record.pin_hash != null
    }

    /**
     * Clear the PIN (e.g. user forgot PIN -> fall back to password login).
     * The encrypted password and primed flag are preserved so biometric still works.
     */
    fun clearPin(username: String, domain: String) {
        db.commCareQueries.updatePinHash(null, username, domain)
    }

    /**
     * Update the last-login timestamp for a user (call after any successful login).
     */
    fun updateLastLogin(username: String, domain: String) {
        db.commCareQueries.updateLastLogin(currentEpochSeconds(), username, domain)
    }

    /**
     * Delete the quick-login record for a user (full logout / account removal).
     */
    fun deleteRecord(username: String, domain: String) {
        db.commCareQueries.deleteUserKeyRecord(username, domain)
    }

    // -- Internal helpers --

    private fun getRecord(username: String, domain: String) =
        db.commCareQueries.getUserKeyRecord(username, domain).executeAsOneOrNull()

    /**
     * Retrieve the device AES-256 key from the keychain, creating it if absent.
     * The key is stored as a hex-encoded 32-byte (256-bit) random value.
     *
     * Migration: if an old-format key exists (32-char lowercase ASCII from
     * the XOR cipher era), we save the old key under a legacy alias before
     * generating a proper AES key, so tryLegacyXorDecrypt can still use it.
     */
    private fun getOrCreateDeviceKey(): ByteArray {
        val existing = keychainStore.retrieve(DEVICE_KEY_ALIAS)
        if (existing != null) {
            try {
                val bytes = hexToBytes(existing)
                if (bytes.size == 32) return bytes
            } catch (_: Exception) {
                // Not valid hex — old-format ASCII key
            }
            // Old-format key: save it under legacy alias before overwriting
            keychainStore.store(LEGACY_KEY_ALIAS, existing)
        }
        val key = PlatformCrypto.generateAesKey(256)
        keychainStore.store(DEVICE_KEY_ALIAS, bytesToHex(key))
        return key
    }

    /**
     * AES encrypt a string and return hex-encoded ciphertext.
     */
    private fun aesEncryptToHex(data: String, key: ByteArray): String {
        val encrypted = PlatformCrypto.aesEncrypt(data.encodeToByteArray(), key)
        return bytesToHex(encrypted)
    }

    /**
     * Decrypt hex-encoded AES ciphertext back to a string.
     * Handles migration from legacy XOR-encrypted data.
     */
    private fun aesDecryptFromHex(hex: String, key: ByteArray): String? {
        return try {
            val bytes = hexToBytes(hex)
            PlatformCrypto.aesDecrypt(bytes, key).decodeToString()
        } catch (_: Exception) {
            // Could be legacy XOR-encrypted data -- attempt migration
            tryLegacyXorDecrypt(hex, key)
        }
    }

    /**
     * Attempt to decrypt legacy XOR-encrypted data.
     * Reads the old XOR key from the legacy alias (saved during key migration).
     * Returns null if no legacy key exists or XOR decryption fails.
     */
    private fun tryLegacyXorDecrypt(hex: String, aesKey: ByteArray): String? {
        return try {
            val legacyKey = keychainStore.retrieve(LEGACY_KEY_ALIAS) ?: return null
            val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val xored = bytes.decodeToString()
            xored.mapIndexed { i, c ->
                (c.code xor legacyKey[i % legacyKey.length].code).toChar()
            }.joinToString("")
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Hash a PIN using PBKDF2-HMAC-SHA256 with a deterministic per-user salt.
     * Returns hex-encoded derived key.
     */
    private fun hashPin(pin: String, username: String, domain: String): String {
        val salt = "$username@$domain".encodeToByteArray()
        val derived = PlatformCrypto.pbkdf2(pin, salt, PBKDF2_ITERATIONS, 32)
        return bytesToHex(derived)
    }

    companion object {
        private const val DEVICE_KEY_ALIAS = "device_encryption_key"
        private const val LEGACY_KEY_ALIAS = "device_encryption_key_legacy"
        private const val PBKDF2_ITERATIONS = 100_000

        private fun bytesToHex(bytes: ByteArray): String =
            bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

        private fun hexToBytes(hex: String): ByteArray =
            hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
