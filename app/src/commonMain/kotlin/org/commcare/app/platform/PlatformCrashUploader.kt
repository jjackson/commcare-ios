package org.commcare.app.platform

import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient

/**
 * Uploads pending crash reports to CommCare HQ.
 * Intended to be called during sync so that crash data reaches the server
 * without requiring a separate network pass.
 */
class PlatformCrashUploader(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    /**
     * Uploads all pending reports from [reporter].
     * If every report uploads successfully the pending list is cleared.
     * Returns the number of reports that were uploaded.
     */
    fun uploadPendingReports(reporter: PlatformCrashReporter): Int {
        val reports = reporter.getPendingReports()
        if (reports.isEmpty()) return 0

        var uploaded = 0
        for (report in reports) {
            if (uploadReport(report)) uploaded++
        }
        // Only clear if ALL reports succeeded so we don't lose any
        if (uploaded == reports.size) {
            reporter.clearReports()
        }
        return uploaded
    }

    private fun uploadReport(report: CrashReport): Boolean {
        return try {
            val url = "${serverUrl.trimEnd('/')}/a/$domain/phone/post_crash/"
            val body = buildReportJson(report)
            val response = httpClient.execute(
                HttpRequest(
                    url = url,
                    method = "POST",
                    headers = mapOf(
                        "Authorization" to authHeader,
                        "Content-Type" to "application/json"
                    ),
                    body = body.encodeToByteArray()
                )
            )
            response.code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun buildReportJson(report: CrashReport): String {
        return buildString {
            append("{")
            append("\"timestamp\":\"${escapeJson(report.timestamp)}\",")
            append("\"message\":\"${escapeJson(report.message)}\",")
            append("\"stack_trace\":\"${escapeJson(report.stackTrace)}\",")
            append("\"device_info\":{")
            val entries = report.deviceInfo.entries.toList()
            for ((i, entry) in entries.withIndex()) {
                append("\"${escapeJson(entry.key)}\":\"${escapeJson(entry.value)}\"")
                if (i < entries.size - 1) append(",")
            }
            append("}}")
        }
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
