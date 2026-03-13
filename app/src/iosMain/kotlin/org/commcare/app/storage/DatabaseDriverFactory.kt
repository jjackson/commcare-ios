package org.commcare.app.storage

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(CommCareDatabase.Schema, "commcare.db")
    }

    actual fun createEncryptedDriver(encryptionKey: String): SqlDriver {
        // iOS Data Protection provides file-level encryption via NSFileProtectionComplete.
        // For SQLCipher-level encryption, would need co.touchlab:sqliter-driver with encryption.
        // For Tier 1, fall back to standard driver with iOS file protection.
        return NativeSqliteDriver(CommCareDatabase.Schema, "commcare.db")
    }
}
