package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository

class DrawerViewModel(private val appRepository: AppRecordRepository) {
    var apps by mutableStateOf<List<ApplicationRecord>>(emptyList())
        private set
    var seatedAppId by mutableStateOf<String?>(null)
        private set
    // Connect ID profile — placeholder for Wave 5
    var profileName by mutableStateOf<String?>(null)
        private set
    var profilePhone by mutableStateOf<String?>(null)
        private set
    var hasConnectAccess by mutableStateOf(false)
        private set

    fun refresh() {
        apps = appRepository.getAllApps()
        seatedAppId = appRepository.getSeatedApp()?.id
    }

    fun switchApp(appId: String) {
        appRepository.seatApp(appId)
        seatedAppId = appId
    }
}
