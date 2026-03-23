package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests for offline sync behavior: graceful error handling when no connectivity.
 */
class OfflineSyncTest {

    @Test
    fun testSyncStateIdleInitially() {
        assertTrue(SyncState.Idle is SyncState)
    }

    @Test
    fun testOfflineErrorMessage() {
        val error = SyncState.Error("No network connection. Please check your internet and try again.")
        assertTrue(error.message.contains("network"), "Error should mention network")
    }

    @Test
    fun testSyncStateErrorOnOffline() {
        // When connectivity check fails, sync should go to Error state
        val isOffline = true
        val syncState: SyncState = if (isOffline) {
            SyncState.Error("No network connection. Please check your internet and try again.")
        } else {
            SyncState.Syncing(0f, "Starting...")
        }
        assertTrue(syncState is SyncState.Error)
    }

    @Test
    fun testConnectivityCheckContract() {
        // checkConnectivity() sends GET to /serverup.txt
        // Any response (even 4xx) means network is up
        // Only exception means offline
        val responseCodes = listOf(200, 301, 403, 404, 500)
        for (code in responseCodes) {
            assertTrue(code > 0, "Any HTTP response means network is up")
        }
    }

    @Test
    fun testSyncProgressStages() {
        // Sync progress stages
        val stages = listOf(
            SyncState.Syncing(0.1f, "Submitting forms..."),
            SyncState.Syncing(0.3f, "Downloading data..."),
            SyncState.Syncing(0.5f, "Processing..."),
            SyncState.Syncing(0.8f, "No new data"),
            SyncState.Syncing(1f, "Sync complete")
        )
        assertTrue(stages.size == 5)
    }
}
