package org.commcare.app.platform

/**
 * JVM crash reporter — captures uncaught exceptions to in-memory list.
 */
actual class PlatformCrashReporter actual constructor() {
    private val reports = mutableListOf<CrashReport>()

    actual fun install() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            reports.add(CrashReport(
                timestamp = System.currentTimeMillis().toString(),
                message = throwable.message ?: "Unknown error",
                stackTrace = throwable.stackTraceToString().take(2000),
                deviceInfo = mapOf(
                    "platform" to "JVM",
                    "os" to System.getProperty("os.name", "unknown"),
                    "java_version" to System.getProperty("java.version", "unknown")
                )
            ))
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    actual fun getPendingReports(): List<CrashReport> = reports.toList()

    actual fun clearReports() {
        reports.clear()
    }
}
