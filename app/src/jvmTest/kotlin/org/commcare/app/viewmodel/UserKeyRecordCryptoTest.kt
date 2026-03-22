package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.CommCareDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for UserKeyRecordManager's AES encryption and PBKDF2 PIN hashing.
 */
class UserKeyRecordCryptoTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    @Test
    fun testPrimeAndDecryptRoundTrip() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "s3cretP@ss!")
        val password = manager.getPasswordForBiometric("user1", "domain.com")

        assertEquals("s3cretP@ss!", password)
    }

    @Test
    fun testPinSetAndVerify() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "mypassword")
        manager.setPin("user1", "domain.com", "123456")

        val password = manager.verifyPinAndGetPassword("user1", "domain.com", "123456")
        assertEquals("mypassword", password)
    }

    @Test
    fun testWrongPinReturnsNull() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "mypassword")
        manager.setPin("user1", "domain.com", "123456")

        val password = manager.verifyPinAndGetPassword("user1", "domain.com", "000000")
        assertNull(password)
    }

    @Test
    fun testLoginModeDetection() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        // No record -> PASSWORD
        assertEquals(UserKeyRecordManager.LoginMode.PASSWORD, manager.getLoginMode("user1", "domain.com"))

        // Primed but no PIN -> BIOMETRIC
        manager.primeForQuickLogin("user1", "domain.com", "pass")
        assertEquals(UserKeyRecordManager.LoginMode.BIOMETRIC, manager.getLoginMode("user1", "domain.com"))

        // PIN set -> PIN
        manager.setPin("user1", "domain.com", "1234")
        assertEquals(UserKeyRecordManager.LoginMode.PIN, manager.getLoginMode("user1", "domain.com"))
    }

    @Test
    fun testEncryptedPasswordIsNotPlaintext() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "mypassword")

        val record = db.commCareQueries.getUserKeyRecord("user1", "domain.com").executeAsOneOrNull()
        assertNotNull(record)
        // The stored value should be hex-encoded AES ciphertext, not the plaintext
        assertNotEquals("mypassword", record.encrypted_password)
        assertTrue(record.encrypted_password!!.length > 20, "Encrypted data should be substantial")
    }

    @Test
    fun testDifferentUsersGetDifferentEncryptions() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "samepassword")
        manager.primeForQuickLogin("user2", "domain.com", "samepassword")

        val r1 = db.commCareQueries.getUserKeyRecord("user1", "domain.com").executeAsOneOrNull()
        val r2 = db.commCareQueries.getUserKeyRecord("user2", "domain.com").executeAsOneOrNull()

        // Same password but different IVs -> different ciphertexts
        assertNotEquals(r1?.encrypted_password, r2?.encrypted_password)
    }

    @Test
    fun testPinHashIsDeterministic() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "pass")
        manager.setPin("user1", "domain.com", "9999")

        // Verify with same PIN should succeed (hash must be deterministic)
        val password = manager.verifyPinAndGetPassword("user1", "domain.com", "9999")
        assertEquals("pass", password)
    }

    @Test
    fun testPinHashUsesPerUserSalt() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("alice", "a.com", "pass1")
        manager.setPin("alice", "a.com", "1111")
        manager.primeForQuickLogin("bob", "b.com", "pass2")
        manager.setPin("bob", "b.com", "1111")

        val r1 = db.commCareQueries.getUserKeyRecord("alice", "a.com").executeAsOneOrNull()
        val r2 = db.commCareQueries.getUserKeyRecord("bob", "b.com").executeAsOneOrNull()

        // Same PIN but different users -> different hashes (different salt)
        assertNotEquals(r1?.pin_hash, r2?.pin_hash)
    }

    @Test
    fun testDeleteRecordClearsEverything() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "pass")
        manager.setPin("user1", "domain.com", "1234")

        manager.deleteRecord("user1", "domain.com")

        assertEquals(UserKeyRecordManager.LoginMode.PASSWORD, manager.getLoginMode("user1", "domain.com"))
        assertNull(manager.verifyPinAndGetPassword("user1", "domain.com", "1234"))
        assertNull(manager.getPasswordForBiometric("user1", "domain.com"))
    }

    @Test
    fun testSpecialCharactersInPassword() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        val password = "p@\$\$w0rd!#%^&*()_+{}|:<>?~`-=[]\\;',./\""
        manager.primeForQuickLogin("user1", "domain.com", password)
        val decrypted = manager.getPasswordForBiometric("user1", "domain.com")

        assertEquals(password, decrypted)
    }

    @Test
    fun testUnicodePassword() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        val password = "密码パスワード🔐"
        manager.primeForQuickLogin("user1", "domain.com", password)
        val decrypted = manager.getPasswordForBiometric("user1", "domain.com")

        assertEquals(password, decrypted)
    }

    @Test
    fun testClearPinPreservesPassword() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "pass")
        manager.setPin("user1", "domain.com", "1234")

        manager.clearPin("user1", "domain.com")

        // PIN login should fail
        assertNull(manager.verifyPinAndGetPassword("user1", "domain.com", "1234"))
        // Biometric should still work
        assertEquals("pass", manager.getPasswordForBiometric("user1", "domain.com"))
        // Mode should be BIOMETRIC (primed, no PIN)
        assertEquals(UserKeyRecordManager.LoginMode.BIOMETRIC, manager.getLoginMode("user1", "domain.com"))
    }
}
