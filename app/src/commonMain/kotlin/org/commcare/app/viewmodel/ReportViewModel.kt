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
 * Manages UCR report data — fetches from CommCare HQ, caches for offline use.
 */
class ReportViewModel(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    var reportState by mutableStateOf<ReportState>(ReportState.Idle)
        private set
    var reportTitle by mutableStateOf("")
        private set
    var reportData by mutableStateOf<ReportData?>(null)
        private set
    var filters by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var cachedReports = mutableMapOf<String, ReportData>()

    fun cancel() { scope.cancel() }

    /**
     * Fetch a UCR report from CommCare HQ.
     */
    fun fetchReport(reportId: String, reportName: String) {
        reportTitle = reportName
        reportState = ReportState.Loading
        errorMessage = null

        // Check cache first
        val cached = cachedReports[reportId]
        if (cached != null) {
            reportData = cached
            reportState = ReportState.Loaded
            return
        }

        scope.launch {
            try {
                val filterParams = filters.entries
                    .filter { it.value.isNotBlank() }
                    .joinToString("&") { "${it.key}=${it.value}" }
                val url = "${serverUrl.trimEnd('/')}/a/$domain/api/v0.5/ucr_data/$reportId/"
                val fullUrl = if (filterParams.isNotEmpty()) "$url?$filterParams" else url

                val response = httpClient.execute(
                    HttpRequest(
                        url = fullUrl,
                        method = "GET",
                        headers = mapOf("Authorization" to authHeader)
                    )
                )

                if (response.code in 200..299) {
                    val body = response.body?.decodeToString() ?: ""
                    val data = parseReportResponse(body)
                    cachedReports[reportId] = data
                    reportData = data
                    reportState = ReportState.Loaded
                } else {
                    errorMessage = "Report fetch failed: HTTP ${response.code}"
                    reportState = ReportState.Error
                }
            } catch (e: Exception) {
                errorMessage = "Report error: ${e.message}"
                // Try cache
                val cached2 = cachedReports[reportId]
                if (cached2 != null) {
                    reportData = cached2
                    reportState = ReportState.Loaded
                } else {
                    reportState = ReportState.Error
                }
            }
        }
    }

    fun updateFilter(key: String, value: String) {
        filters = filters + (key to value)
    }

    fun clearCache() {
        cachedReports.clear()
    }

    private fun parseReportResponse(body: String): ReportData {
        // Simple parsing — in production would parse JSON properly
        val columns = mutableListOf<String>()
        val rows = mutableListOf<List<String>>()

        // Try to extract column headers and rows from a simple format
        val lines = body.lines().filter { it.isNotBlank() }
        if (lines.isNotEmpty()) {
            // Treat first line as headers
            columns.addAll(lines.first().split(",", "\t").map { it.trim() })
            for (line in lines.drop(1)) {
                rows.add(line.split(",", "\t").map { it.trim() })
            }
        }

        return ReportData(columns = columns, rows = rows, totalRows = rows.size)
    }
}

data class ReportData(
    val columns: List<String>,
    val rows: List<List<String>>,
    val totalRows: Int
)

sealed class ReportState {
    data object Idle : ReportState()
    data object Loading : ReportState()
    data object Loaded : ReportState()
    data object Error : ReportState()
}
