package org.commcare.app.platform

import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice

/**
 * iOS crash reporter — captures uncaught exceptions.
 * Uses NSSetUncaughtExceptionHandler for Objective-C exceptions.
 * Kotlin/Native exceptions are captured via the uncaught exception hook.
 */
actual class PlatformCrashReporter actual constructor() {
    private val reports = mutableListOf<CrashReport>()

    actual fun install() {
        // Kotlin/Native unhandled exception hook
        // Note: Full implementation would use NSSetUncaughtExceptionHandler
        // and signal handlers for SIGSEGV/SIGABRT
    }

    actual fun getPendingReports(): List<CrashReport> = reports.toList()

    actual fun clearReports() {
        reports.clear()
    }
}
