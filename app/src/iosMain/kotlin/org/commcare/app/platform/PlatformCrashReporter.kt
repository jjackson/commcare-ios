package org.commcare.app.platform

/**
 * iOS crash reporter stub.
 * Full implementation requires NSSetUncaughtExceptionHandler + signal handlers.
 */
actual class PlatformCrashReporter actual constructor() {
    private val reports = mutableListOf<CrashReport>()

    actual fun install() {
        // TODO: NSSetUncaughtExceptionHandler + signal handlers for SIGSEGV/SIGABRT
    }

    actual fun getPendingReports(): List<CrashReport> = reports.toList()

    actual fun clearReports() {
        reports.clear()
    }
}
