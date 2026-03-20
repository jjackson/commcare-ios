package org.commcare.app.viewmodel

import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.platform.currentEpochSeconds
import org.commcare.app.storage.CommCareDatabase

/**
 * Manages encrypted password storage and PIN verification for quick login.
 *
 * After a user's first successful password login, their password is encrypted
 * and stored locally. On subsequent logins, a PIN or biometric unlock decrypts
 * the password so it can be sent to the server for authentication.
 *
 * Encryption model: XOR with a randomly generated device key stored in the
 * platform keychain store. In production this should be replaced with AES-GCM
 * via iOS CommonCrypto / JVM javax.crypto -- the XOR cipher here is a dev-phase
 * placeholder that preserves the integration shape without adding crypto deps.
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
        val encrypted = xorEncrypt(password, key)
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
     * The PIN is stored as a hash -- not the plain PIN.
     */
    fun setPin(username: String, domain: String, pin: String) {
        val hash = hashPin(pin)
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
        if (hashPin(pin) != storedHash) return null
        val key = getOrCreateDeviceKey()
        return xorDecrypt(record.encrypted_password ?: return null, key)
    }

    /**
     * Return the stored decrypted password for biometric login (no PIN needed).
     * Returns null if the record is not primed or lacks an encrypted password.
     */
    fun getPasswordForBiometric(username: String, domain: String): String? {
        val record = getRecord(username, domain) ?: return null
        if (record.is_primed == 0L || record.encrypted_password == null) return null
        val key = getOrCreateDeviceKey()
        return xorDecrypt(record.encrypted_password, key)
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
     * Retrieve the device encryption key from the keychain, creating it if absent.
     * The key is a 32-character random lowercase ASCII string.
     */
    private fun getOrCreateDeviceKey(): String {
        val existing = keychainStore.retrieve(DEVICE_KEY_ALIAS)
        if (existing != null) return existing
        val key = buildString(32) {
            val alphabet = ('a'..'z').toList()
            repeat(32) { append(alphabet.random()) }
        }
        keychainStore.store(DEVICE_KEY_ALIAS, key)
        return key
    }

    /**
     * XOR-based symmetric encryption.
     * Encrypts [data] by XOR-ing each character with the repeating [key],
     * then hex-encodes the result so it is safe to store as TEXT.
     *
     * NOTE: This is a dev-phase placeholder. Replace with AES-GCM before
     * shipping to production.
     */
    private fun xorEncrypt(data: String, key: String): String {
        val xored = data.mapIndexed { i, c ->
            (c.code xor key[i % key.length].code).toChar()
        }.joinToString("")
        return xored.encodeToByteArray().joinToString("") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
    }

    /**
     * Reverse of [xorEncrypt]: hex-decodes then XORs back with [key].
     */
    private fun xorDecrypt(hex: String, key: String): String {
        val bytes = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val xored = bytes.decodeToString()
        return xored.mapIndexed { i, c ->
            (c.code xor key[i % key.length].code).toChar()
        }.joinToString("")
    }

    /**
     * Simple hash for PIN storage.
     *
     * NOTE: A production implementation should use PBKDF2 or bcrypt so that
     * a stolen database cannot be brute-forced quickly. The polynomial hash
     * here is intentionally lightweight for the dev phase.
     */
    private fun hashPin(pin: String): String {
        var hash = 7L
        for (c in pin) {
            hash = hash * 31 + c.code
        }
        return hash.toString(16)
    }

    companion object {
        private const val DEVICE_KEY_ALIAS = "device_encryption_key"
    }
}
