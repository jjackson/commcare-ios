package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient

/**
 * Manages data sync with CommCare HQ — restore (pull) and form submission (push).
 */
class SyncViewModel(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    var syncState by mutableStateOf<SyncState>(SyncState.Idle)
        private set
    var lastSyncTime by mutableStateOf<String?>(null)
        private set
    var lastSyncToken by mutableStateOf<String?>(null)
        private set

    fun sync() {
        syncState = SyncState.Syncing(0f, "Starting sync...")
        try {
            // Phase 1: Pull restore data from HQ
            syncState = SyncState.Syncing(0.2f, "Downloading data from server...")
            val restoreUrl = "${serverUrl.trimEnd('/')}/a/$domain/phone/restore/"
            val headers = mutableMapOf(
                "Authorization" to authHeader
            )
            if (lastSyncToken != null) {
                headers["X-CommCareHQ-LastSyncToken"] = lastSyncToken!!
            }

            val response = httpClient.execute(
                HttpRequest(
                    url = restoreUrl,
                    method = "GET",
                    headers = headers
                )
            )

            when {
                response.code == 412 -> {
                    // No new data since last sync
                    syncState = SyncState.Syncing(0.5f, "No new data from server")
                }
                response.code in 200..299 -> {
                    syncState = SyncState.Syncing(0.5f, "Processing restore data...")
                    // In full implementation: parse restore XML, update case/fixture storage
                    // Extract sync token from response for incremental sync
                    val bodyText = response.body?.decodeToString()
                    lastSyncToken = extractSyncToken(bodyText)
                }
                response.code == 401 -> {
                    syncState = SyncState.Error("Authentication expired. Please log in again.")
                    return
                }
                else -> {
                    syncState = SyncState.Error("Sync failed: server returned ${response.code}")
                    return
                }
            }

            // Phase 2: Submit queued forms
            syncState = SyncState.Syncing(0.7f, "Submitting forms...")
            // In full implementation: POST each queued form XML to receiver endpoint

            syncState = SyncState.Syncing(1f, "Sync complete")
            lastSyncTime = currentTimestamp()
            syncState = SyncState.Complete
        } catch (e: Exception) {
            syncState = SyncState.Error("Sync failed: ${e.message}")
        }
    }

    fun resetState() {
        syncState = SyncState.Idle
    }

    private fun extractSyncToken(body: String?): String? {
        if (body == null) return null
        // CommCare restore XML contains <Sync><restore_id>TOKEN</restore_id></Sync>
        val start = body.indexOf("<restore_id>")
        val end = body.indexOf("</restore_id>")
        if (start >= 0 && end > start) {
            return body.substring(start + "<restore_id>".length, end)
        }
        return null
    }

    private fun currentTimestamp(): String {
        // Simple timestamp — no platform date API needed for display
        return "Just now"
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data class Syncing(val progress: Float, val message: String) : SyncState()
    data object Complete : SyncState()
    data class Error(val message: String) : SyncState()
}
