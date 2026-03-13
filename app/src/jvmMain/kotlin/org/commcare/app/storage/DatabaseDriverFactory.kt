package org.commcare.app.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DatabaseDriverFactory(private val dbPath: String? = null) {
    actual fun createDriver(): SqlDriver {
        val url = if (dbPath != null) "jdbc:sqlite:$dbPath" else JdbcSqliteDriver.IN_MEMORY
        val driver = JdbcSqliteDriver(url)
        CommCareDatabase.Schema.create(driver)
        return driver
    }

    actual fun createEncryptedDriver(encryptionKey: String): SqlDriver {
        val url = if (dbPath != null) "jdbc:sqlite:$dbPath" else JdbcSqliteDriver.IN_MEMORY
        val driver = JdbcSqliteDriver(url)
        CommCareDatabase.Schema.create(driver)
        // Apply PRAGMA key for SQLCipher-compatible drivers.
        // With standard sqlite-jdbc this is a no-op; with SQLCipher JDBC it enables encryption.
        if (encryptionKey.isNotEmpty()) {
            driver.execute(null, "PRAGMA key = '$encryptionKey'", 0)
        }
        return driver
    }
}
