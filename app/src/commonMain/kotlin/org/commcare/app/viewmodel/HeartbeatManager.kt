package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient

/**
 * Manages periodic heartbeat check-ins with CommCare HQ.
 * Reports device status and receives app update notifications.
 */
class HeartbeatManager(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    var heartbeatState by mutableStateOf<HeartbeatState>(HeartbeatState.Idle)
        private set
    var appUpdateStatus by mutableStateOf<AppUpdateStatus>(AppUpdateStatus.UpToDate)
        private set
    var lastHeartbeatTime by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(Dispatchers.Default)

    /**
     * Send a heartbeat to CommCare HQ with device info.
     */
    fun sendHeartbeat(
        appVersion: String,
        lastSyncTime: String?,
        pendingFormCount: Int
    ) {
        heartbeatState = HeartbeatState.Sending
        scope.launch {
            try {
                val url = "${serverUrl.trimEnd('/')}/a/$domain/phone/heartbeat/"
                val params = buildString {
                    append("app_version=$appVersion")
                    if (lastSyncTime != null) append("&last_sync=$lastSyncTime")
                    append("&pending_forms=$pendingFormCount")
                }
                val fullUrl = "$url?$params"

                val response = httpClient.execute(
                    HttpRequest(
                        url = fullUrl,
                        method = "GET",
                        headers = mapOf("Authorization" to authHeader)
                    )
                )

                if (response.code in 200..299) {
                    val body = response.body?.decodeToString() ?: ""
                    parseHeartbeatResponse(body)
                    heartbeatState = HeartbeatState.Success
                } else {
                    heartbeatState = HeartbeatState.Error("Server returned ${response.code}")
                }
            } catch (e: Exception) {
                heartbeatState = HeartbeatState.Error(e.message ?: "Heartbeat failed")
            }
        }
    }

    private fun parseHeartbeatResponse(body: String) {
        // Parse JSON-like response for update status
        when {
            body.contains("\"force_update\"") || body.contains("force_update") ->
                appUpdateStatus = AppUpdateStatus.ForceUpdate
            body.contains("\"update_available\"") || body.contains("update_available") ->
                appUpdateStatus = AppUpdateStatus.UpdateAvailable
            else ->
                appUpdateStatus = AppUpdateStatus.UpToDate
        }
    }

    /**
     * Whether the app should block usage until updated.
     */
    fun isForceUpdateRequired(): Boolean = appUpdateStatus is AppUpdateStatus.ForceUpdate
}

sealed class HeartbeatState {
    data object Idle : HeartbeatState()
    data object Sending : HeartbeatState()
    data object Success : HeartbeatState()
    data class Error(val message: String) : HeartbeatState()
}

sealed class AppUpdateStatus {
    data object UpToDate : AppUpdateStatus()
    data object UpdateAvailable : AppUpdateStatus()
    data object ForceUpdate : AppUpdateStatus()
}
