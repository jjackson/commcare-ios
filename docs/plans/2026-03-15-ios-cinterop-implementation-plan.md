# iOS cinterop Implementation Plan

**Date:** 2026-03-15
**Status:** Plan only (requires macOS for implementation and testing)

## Overview

Four platform features are currently implemented as stubs in `app/src/iosMain/`. Each uses the expect/actual pattern with a commonMain expect declaration. The JVM actuals are also stubs (desktop JVM has no biometric, printing, etc.). The iOS actuals need real cinterop implementations using iOS frameworks.

## Current Stubs

| Feature | File | iOS Framework | Status |
|---|---|---|---|
| Biometric Auth | `PlatformBiometricAuth.kt` | LocalAuthentication | Stub (returns `Unavailable`) |
| Printing | `PlatformPrinting.kt` | UIKit (UIPrintInteractionController) | Stub (returns `false`) |
| Scheduler | `PlatformScheduler.kt` | BackgroundTasks (BGTaskScheduler) | Stub (no-op) |
| Crash Reporter | `PlatformCrashReporter.kt` | Foundation (NSException handler) | Stub (empty list) |

## Implementation Details

### 1. PlatformBiometricAuth — LocalAuthentication

**iOS Framework:** `LocalAuthentication`
**Key API:** `LAContext.evaluatePolicy(_:localizedReason:reply:)`

```kotlin
// iosMain actual implementation
import platform.LocalAuthentication.*

actual class PlatformBiometricAuth actual constructor() {
    actual fun canAuthenticate(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null
        )
    }

    actual fun authenticate(reason: String, onResult: (BiometricResult) -> Unit) {
        val context = LAContext()
        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            localizedReason = reason
        ) { success, error ->
            dispatch_async(dispatch_get_main_queue()) {
                when {
                    success -> onResult(BiometricResult.Success)
                    error?.code == LAErrorUserCancel -> onResult(BiometricResult.Cancelled)
                    error?.code == LAErrorBiometryNotAvailable -> onResult(BiometricResult.Unavailable)
                    else -> onResult(BiometricResult.Failure(error?.localizedDescription ?: "Unknown error"))
                }
            }
        }
    }
}
```

**cinterop def file:** Not needed — `LocalAuthentication` is available via Kotlin/Native's platform libraries.

**Permissions:** Add to Info.plist:
```xml
<key>NSFaceIDUsageDescription</key>
<string>CommCare uses Face ID to secure app access</string>
```

### 2. PlatformPrinting — UIKit

**iOS Framework:** `UIKit`
**Key API:** `UIPrintInteractionController`

```kotlin
// iosMain actual implementation
import platform.UIKit.*

actual class PlatformPrinting actual constructor() {
    actual fun canPrint(): Boolean {
        return UIPrintInteractionController.isPrintingAvailable()
    }

    actual fun printHtml(html: String, jobTitle: String, onComplete: (Boolean) -> Unit) {
        val controller = UIPrintInteractionController.sharedPrintController()
        val printInfo = UIPrintInfo.printInfo()
        printInfo.outputType = UIPrintInfoOutputGeneral
        printInfo.jobName = jobTitle
        controller.printInfo = printInfo

        val formatter = UIMarkupTextPrintFormatter(markupText = html)
        controller.printFormatter = formatter

        controller.presentAnimated(true) { _, completed, error ->
            onComplete(completed)
        }
    }
}
```

**Note:** `presentAnimated` needs the current view controller. In practice, use `UIApplication.sharedApplication().keyWindow?.rootViewController` to present from.

### 3. PlatformScheduler — BackgroundTasks

**iOS Framework:** `BackgroundTasks` (iOS 13+)
**Key API:** `BGTaskScheduler`

```kotlin
// iosMain actual implementation
import platform.BackgroundTasks.*
import platform.Foundation.*

actual class PlatformScheduler actual constructor() {
    actual fun schedulePeriodicTask(taskId: String, intervalMinutes: Int, task: () -> Unit) {
        val request = BGAppRefreshTaskRequest(identifier = taskId)
        request.earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(
            intervalMinutes.toDouble() * 60.0
        )
        BGTaskScheduler.sharedScheduler.submitTaskRequest(request, error = null)

        // Register handler
        BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(
            identifier = taskId,
            usingQueue = null
        ) { bgTask ->
            task()
            bgTask?.setTaskCompletedWithSuccess(true)
            // Re-schedule
            schedulePeriodicTask(taskId, intervalMinutes, task)
        }
    }

    actual fun cancelTask(taskId: String) {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(taskId)
    }

    actual fun cancelAll() {
        BGTaskScheduler.sharedScheduler.cancelAllTaskRequests()
    }
}
```

**Required setup:**
1. Enable "Background Modes" capability in Xcode
2. Check "Background fetch" and "Background processing"
3. Add to Info.plist:
```xml
<key>BGTaskSchedulerPermittedIdentifiers</key>
<array>
    <string>org.commcare.sync</string>
    <string>org.commcare.heartbeat</string>
</array>
```

### 4. PlatformCrashReporter — NSException

**iOS Framework:** `Foundation`
**Key API:** `NSSetUncaughtExceptionHandler`, `NSUserDefaults` for persistence

```kotlin
// iosMain actual implementation
import platform.Foundation.*
import kotlinx.cinterop.*

actual class PlatformCrashReporter actual constructor() {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun install() {
        NSSetUncaughtExceptionHandler(staticCFunction { exception ->
            val report = CrashReport(
                timestamp = NSDate().description,
                message = exception?.name ?: "Unknown",
                stackTrace = exception?.callStackSymbols?.joinToString("\n") ?: "",
                deviceInfo = "${UIDevice.currentDevice.model} ${UIDevice.currentDevice.systemVersion}"
            )
            // Persist to UserDefaults (synchronous, safe in crash handler)
            val encoded = "${report.timestamp}|||${report.message}|||${report.stackTrace}"
            val existing = NSUserDefaults.standardUserDefaults
                .stringArrayForKey("crash_reports")?.toMutableList() ?: mutableListOf()
            existing.add(encoded)
            NSUserDefaults.standardUserDefaults.setObject(existing, forKey = "crash_reports")
            NSUserDefaults.standardUserDefaults.synchronize()
        })
    }

    actual fun getPendingReports(): List<CrashReport> {
        val stored = defaults.stringArrayForKey("crash_reports") ?: return emptyList()
        return stored.mapNotNull { raw ->
            val parts = (raw as? String)?.split("|||") ?: return@mapNotNull null
            if (parts.size >= 3) {
                CrashReport(parts[0], parts[1], parts[2], "")
            } else null
        }
    }

    actual fun clearReports() {
        defaults.removeObjectForKey("crash_reports")
    }
}
```

**Note:** The `staticCFunction` for exception handler has limitations — it can't capture Kotlin objects. The actual implementation may need a C helper or global state.

## Implementation Order

1. **PlatformBiometricAuth** — Most straightforward, uses well-documented LAContext API
2. **PlatformPrinting** — Standard UIKit printing, requires view controller context
3. **PlatformCrashReporter** — Useful for debugging, but `staticCFunction` constraint is tricky
4. **PlatformScheduler** — Most complex, requires Xcode capability setup and background mode entitlements

## Testing Approach

### Unit Tests
Each implementation should have tests in `app/src/iosTest/`:
- `canAuthenticate()` returns `true` on simulator with enrolled biometrics
- `canPrint()` returns `true` on simulator
- Scheduler registration doesn't crash
- Crash reporter install/clear cycle works

### Integration Tests
- Full biometric flow with simulator dialog (manual)
- Print preview with HTML content (manual)
- Background task scheduling and firing (requires running on device)

### CI Considerations
- CI runs on macOS-14, so simulator-based tests work
- Biometric tests may need `simctl` setup to enroll Touch ID
- Background tasks can't be fully tested in CI (requires real background execution)

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| `staticCFunction` can't capture Kotlin state | Use global `AtomicReference` or C helper file |
| `UIPrintInteractionController` needs UIViewController | Pass root view controller from SwiftUI bridge |
| `BGTaskScheduler` minimum interval is 15 minutes | Document limitation, don't promise exact timing |
| Simulator vs device behavior differences | Tag tests that only work on device |

## Prerequisites

- macOS development machine with Xcode 15+
- iOS 15+ deployment target
- Working Xcode project (see `2026-03-15-xcode-project-setup.md`)
