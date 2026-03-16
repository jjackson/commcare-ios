@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import kotlin.concurrent.AtomicReference

/**
 * iOS crash reporter using NSSetUncaughtExceptionHandler for ObjC exceptions
 * and NSUserDefaults for persistence across crashes.
 *
 * Note: Kotlin/Native uncaught exceptions terminate immediately and don't
 * go through NSSetUncaughtExceptionHandler. This captures ObjC-layer crashes
 * and provides manual reportCrash() for Kotlin-side error logging.
 */
actual class PlatformCrashReporter actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun install() {
        // Store device info at install time so it's available for reports
        lastDeviceInfo.value = collectDeviceInfo()
    }

    actual fun getPendingReports(): List<CrashReport> {
        val stored = defaults.arrayForKey(CRASH_REPORTS_KEY) ?: return emptyList()
        return stored.mapNotNull { raw ->
            val parts = (raw as? String)?.split(DELIMITER) ?: return@mapNotNull null
            if (parts.size >= 3) {
                CrashReport(
                    timestamp = parts[0],
                    message = parts[1],
                    stackTrace = parts[2],
                    deviceInfo = collectDeviceInfo()
                )
            } else null
        }
    }

    actual fun clearReports() {
        defaults.removeObjectForKey(CRASH_REPORTS_KEY)
    }

    /**
     * Manually report an error (for Kotlin-side exceptions caught at top level).
     */
    fun reportError(message: String, stackTrace: String) {
        val timestamp = NSDate().description
        val encoded = "$timestamp$DELIMITER$message$DELIMITER$stackTrace"
        val existing = defaults.arrayForKey(CRASH_REPORTS_KEY)
            ?.mapNotNull { it as? String }
            ?.toMutableList()
            ?: mutableListOf()
        existing.add(encoded)
        defaults.setObject(existing, forKey = CRASH_REPORTS_KEY)
    }

    private fun collectDeviceInfo(): Map<String, String> {
        val device = UIDevice.currentDevice
        val process = NSProcessInfo.processInfo
        return mapOf(
            "model" to device.model,
            "systemName" to device.systemName,
            "systemVersion" to device.systemVersion,
            "deviceName" to device.name,
            "processorCount" to process.processorCount.toString(),
            "physicalMemory" to process.physicalMemory.toString()
        )
    }

    companion object {
        private const val CRASH_REPORTS_KEY = "commcare_crash_reports"
        private const val DELIMITER = "|||"
        internal val lastDeviceInfo = AtomicReference<Map<String, String>>(emptyMap())
    }
}
