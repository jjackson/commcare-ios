package org.commcare.app.storage

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver

    /**
     * Create a driver with an encryption key for data-at-rest protection.
     * On JVM: Uses PRAGMA key with SQLCipher-compatible JDBC driver (when available).
     * On iOS: Uses encrypted SQLite via sqliter-driver encryption support.
     * Falls back to unencrypted driver if encryption is not available.
     */
    fun createEncryptedDriver(encryptionKey: String): SqlDriver
}
