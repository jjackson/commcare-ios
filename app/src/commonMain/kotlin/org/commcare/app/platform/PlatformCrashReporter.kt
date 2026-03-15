package org.commcare.app.platform

/**
 * Platform-specific crash reporting — captures uncaught exceptions and device info.
 */
expect class PlatformCrashReporter() {
    /**
     * Install the crash handler to capture uncaught exceptions.
     */
    fun install()

    /**
     * Get pending crash reports as structured text.
     */
    fun getPendingReports(): List<CrashReport>

    /**
     * Clear all pending crash reports (after successful upload).
     */
    fun clearReports()
}

data class CrashReport(
    val timestamp: String,
    val message: String,
    val stackTrace: String,
    val deviceInfo: Map<String, String>
)
