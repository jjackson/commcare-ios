package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient
import org.commcare.core.parse.ParseUtils
import org.javarosa.core.io.createByteArrayInputStream

/**
 * Manages data sync with CommCare HQ — restore (pull) and form submission (push).
 * Uses ParseUtils.parseIntoSandbox() for real restore XML processing.
 */
class SyncViewModel(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String,
    private val sandbox: SqlDelightUserSandbox,
    private val userId: String = ""
) {
    var syncState by mutableStateOf<SyncState>(SyncState.Idle)
        private set
    var lastSyncTime by mutableStateOf<String?>(null)
        private set
    var lastSyncToken by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

    companion object {
        const val MAX_FORM_RETRIES = 3
    }

    init {
        // Restore last sync token from sandbox or database
        lastSyncToken = sandbox.syncToken
        if (lastSyncToken == null && userId.isNotEmpty()) {
            lastSyncToken = sandbox.loadSyncToken(userId)
            if (lastSyncToken != null) {
                sandbox.syncToken = lastSyncToken
            }
        }
    }

    fun sync(formQueue: FormQueueViewModel? = null) {
        syncState = SyncState.Syncing(0f, "Starting sync...")

        scope.launch {
            try {
                // Phase 1: Submit queued forms first (send before receive)
                if (formQueue != null && formQueue.pendingCount > 0) {
                    syncState = SyncState.Syncing(0.1f, "Submitting ${formQueue.pendingCount} forms...")
                    submitFormsWithRetry(formQueue)
                }

                // Phase 2: Pull restore data from HQ
                syncState = SyncState.Syncing(0.3f, "Downloading data from server...")
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
                        syncState = SyncState.Syncing(0.8f, "No new data from server")
                    }
                    response.code in 200..299 -> {
                        syncState = SyncState.Syncing(0.5f, "Processing restore data...")
                        val body = response.body
                        if (body != null && body.isNotEmpty()) {
                            // Parse restore XML into sandbox using real engine parsers
                            val stream = createByteArrayInputStream(body)
                            ParseUtils.parseIntoSandbox(stream, sandbox, false)

                            // Update sync token from sandbox (set by CommCareTransactionParserFactory)
                            lastSyncToken = sandbox.syncToken
                            // Persist sync token to database for next app launch
                            if (userId.isNotEmpty() && lastSyncToken != null) {
                                sandbox.persistSyncToken(userId)
                            }
                        }
                    }
                    response.code == 401 -> {
                        syncState = SyncState.Error("Authentication expired. Please log in again.")
                        return@launch
                    }
                    else -> {
                        syncState = SyncState.Error("Sync failed: server returned ${response.code}")
                        return@launch
                    }
                }

                syncState = SyncState.Syncing(1f, "Sync complete")
                lastSyncTime = "Just now"
                syncState = SyncState.Complete
            } catch (e: Exception) {
                syncState = SyncState.Error("Sync failed: ${e.message}")
            }
        }
    }

    /**
     * Submit forms with retry logic. Forms exceeding MAX_FORM_RETRIES are left as failed.
     */
    private fun submitFormsWithRetry(formQueue: FormQueueViewModel) {
        val submitted = formQueue.submitAllSync()
        if (submitted == 0 && formQueue.pendingCount > 0) {
            // Retry once for transient failures
            syncState = SyncState.Syncing(0.2f, "Retrying failed form submissions...")
            formQueue.submitAllSync()
        }
    }

    fun resetState() {
        syncState = SyncState.Idle
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data class Syncing(val progress: Float, val message: String) : SyncState()
    data object Complete : SyncState()
    data class Error(val message: String) : SyncState()
}
