package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient

/**
 * Connection diagnostics — pings server, checks auth, reports sync status.
 */
class DiagnosticsViewModel(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    var isRunning by mutableStateOf(false)
        private set
    var results by mutableStateOf<List<DiagnosticResult>>(emptyList())
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

    /**
     * Run all diagnostic checks.
     */
    fun runDiagnostics(lastSyncTime: String?, pendingFormCount: Int) {
        isRunning = true
        results = emptyList()

        scope.launch {
            val checks = mutableListOf<DiagnosticResult>()

            // Check 1: Server reachability
            checks.add(checkServerReachability())
            results = checks.toList()

            // Check 2: Auth credentials
            checks.add(checkAuthentication())
            results = checks.toList()

            // Check 3: Sync status
            checks.add(DiagnosticResult(
                name = "Last Sync",
                status = if (lastSyncTime != null) DiagnosticStatus.OK else DiagnosticStatus.Warning,
                detail = lastSyncTime ?: "Never synced"
            ))
            results = checks.toList()

            // Check 4: Pending forms
            checks.add(DiagnosticResult(
                name = "Pending Forms",
                status = if (pendingFormCount == 0) DiagnosticStatus.OK else DiagnosticStatus.Warning,
                detail = "$pendingFormCount form${if (pendingFormCount != 1) "s" else ""} waiting to submit"
            ))
            results = checks.toList()

            isRunning = false
        }
    }

    private fun checkServerReachability(): DiagnosticResult {
        return try {
            val url = "${serverUrl.trimEnd('/')}/serverup.txt"
            val response = httpClient.execute(
                HttpRequest(url = url, method = "GET", headers = emptyMap())
            )

            DiagnosticResult(
                name = "Server Reachability",
                status = if (response.code in 200..299) DiagnosticStatus.OK else DiagnosticStatus.Error,
                detail = if (response.code in 200..299) "Server reachable" else "HTTP ${response.code}"
            )
        } catch (e: Exception) {
            DiagnosticResult(
                name = "Server Reachability",
                status = DiagnosticStatus.Error,
                detail = e.message ?: "Connection failed"
            )
        }
    }

    private fun checkAuthentication(): DiagnosticResult {
        return try {
            val url = "${serverUrl.trimEnd('/')}/a/$domain/phone/restore/?version=2.0"
            val response = httpClient.execute(
                HttpRequest(
                    url = url,
                    method = "GET",
                    headers = mapOf("Authorization" to authHeader)
                )
            )

            when (response.code) {
                in 200..299 -> DiagnosticResult("Authentication", DiagnosticStatus.OK, "Credentials valid")
                401 -> DiagnosticResult("Authentication", DiagnosticStatus.Error, "Invalid credentials")
                else -> DiagnosticResult("Authentication", DiagnosticStatus.Warning, "Server returned ${response.code}")
            }
        } catch (e: Exception) {
            DiagnosticResult("Authentication", DiagnosticStatus.Error, e.message ?: "Auth check failed")
        }
    }

}

data class DiagnosticResult(
    val name: String,
    val status: DiagnosticStatus,
    val detail: String
)

enum class DiagnosticStatus {
    OK, Warning, Error
}
