package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository
import org.commcare.app.storage.ConnectIdRepository

class DrawerViewModel(
    private val appRepository: AppRecordRepository,
    private val connectIdRepository: ConnectIdRepository? = null
) {
    var apps by mutableStateOf<List<ApplicationRecord>>(emptyList())
        private set
    var seatedAppId by mutableStateOf<String?>(null)
        private set
    // Connect ID profile — populated from ConnectIdRepository
    var profileName by mutableStateOf<String?>(null)
        private set
    var profilePhone by mutableStateOf<String?>(null)
        private set
    var hasConnectAccess by mutableStateOf(false)
        private set
    /** Unread message count — set by the caller (e.g. MessagingViewModel) after load. */
    var unreadMessageCount by mutableStateOf(0)

    fun refresh() {
        apps = appRepository.getAllApps()
        seatedAppId = appRepository.getSeatedApp()?.id

        // Populate Connect ID profile if repository is wired
        val connectUser = connectIdRepository?.getUser()
        profileName = connectUser?.name
        profilePhone = connectUser?.phone
        hasConnectAccess = connectUser?.hasConnectAccess ?: false
    }

    fun switchApp(appId: String) {
        appRepository.seatApp(appId)
        seatedAppId = appId
    }
}
