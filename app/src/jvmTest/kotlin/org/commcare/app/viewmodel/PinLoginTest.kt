package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.CommCareDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for PIN login flow via UserKeyRecordManager.
 */
class PinLoginTest {

    private fun createTestDatabase(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    @Test
    fun testCreatePinAndVerify() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "password123")
        manager.setPin("user1", "domain.com", "1234")

        val result = manager.verifyPinAndGetPassword("user1", "domain.com", "1234")
        assertEquals("password123", result)
    }

    @Test
    fun testWrongPinRejects() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "password123")
        manager.setPin("user1", "domain.com", "1234")

        val result = manager.verifyPinAndGetPassword("user1", "domain.com", "0000")
        assertNull(result, "Wrong PIN should return null")
    }

    @Test
    fun testPinUnlocksCredentials() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        val password = "s3cure!P@ss"
        manager.primeForQuickLogin("alice", "test.org", password)
        manager.setPin("alice", "test.org", "999999")

        val decrypted = manager.verifyPinAndGetPassword("alice", "test.org", "999999")
        assertEquals(password, decrypted, "PIN should unlock the original password")
    }

    @Test
    fun testNoPinSetReturnsNull() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        manager.primeForQuickLogin("user1", "domain.com", "pass")
        // Don't set a PIN

        val result = manager.verifyPinAndGetPassword("user1", "domain.com", "1234")
        assertNull(result, "No PIN set should return null")
    }

    @Test
    fun testPinModeDetection() {
        val db = createTestDatabase()
        val keychain = PlatformKeychainStore()
        val manager = UserKeyRecordManager(db, keychain)

        assertEquals(UserKeyRecordManager.LoginMode.PASSWORD, manager.getLoginMode("user1", "d.com"))

        manager.primeForQuickLogin("user1", "d.com", "pass")
        assertEquals(UserKeyRecordManager.LoginMode.BIOMETRIC, manager.getLoginMode("user1", "d.com"))

        manager.setPin("user1", "d.com", "1234")
        assertEquals(UserKeyRecordManager.LoginMode.PIN, manager.getLoginMode("user1", "d.com"))
    }
}
