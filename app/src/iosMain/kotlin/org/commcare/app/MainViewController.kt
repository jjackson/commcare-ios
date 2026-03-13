package org.commcare.app

import androidx.compose.ui.window.ComposeUIViewController
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.DatabaseDriverFactory

fun MainViewController() = ComposeUIViewController {
    val db = CommCareDatabase(DatabaseDriverFactory().createDriver())
    App(db)
}
