package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.storage.AppRecordRepository

class AppManagerViewModel(private val appRepository: AppRecordRepository) {
    var apps by mutableStateOf<List<ApplicationRecord>>(emptyList())
        private set
    var seatedAppId by mutableStateOf<String?>(null)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    companion object {
        const val MAX_APPS = 4
    }

    fun refresh() {
        apps = appRepository.getAllApps()
        seatedAppId = appRepository.getSeatedApp()?.id
    }

    fun canInstallMore(): Boolean = apps.count { it.isUsable() } < MAX_APPS

    fun archiveApp(appId: String) {
        appRepository.archiveApp(appId)
        // If archived app was seated, seat the next usable one
        if (appId == seatedAppId) {
            val nextApp = appRepository.getAllApps().firstOrNull { it.isUsable() }
            if (nextApp != null) {
                appRepository.seatApp(nextApp.id)
            }
        }
        refresh()
    }

    fun uninstallApp(appId: String) {
        appRepository.deleteApp(appId)
        if (appId == seatedAppId) {
            val nextApp = appRepository.getAllApps().firstOrNull { it.isUsable() }
            if (nextApp != null) {
                appRepository.seatApp(nextApp.id)
            }
        }
        refresh()
    }

    fun seatApp(appId: String) {
        appRepository.seatApp(appId)
        seatedAppId = appId
    }
}
