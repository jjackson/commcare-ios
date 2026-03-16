@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import platform.Foundation.NSTimer
import platform.Foundation.NSRunLoop
import platform.Foundation.NSDefaultRunLoopMode
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS scheduler using NSTimer for foreground periodic tasks.
 *
 * True background execution requires BGTaskScheduler which needs
 * Xcode entitlements and Info.plist configuration. NSTimer works
 * while the app is in the foreground, which covers the primary
 * use case of periodic sync during active use.
 *
 * For background sync, BGTaskScheduler integration should be added
 * when the Xcode project has the Background Modes capability enabled.
 */
actual class PlatformScheduler actual constructor() {
    private val timers = mutableMapOf<String, NSTimer>()
    private val taskCallbacks = mutableMapOf<String, () -> Unit>()

    actual fun schedulePeriodicTask(taskId: String, intervalMinutes: Int, task: () -> Unit) {
        cancelTask(taskId)
        taskCallbacks[taskId] = task

        dispatch_async(dispatch_get_main_queue()) {
            val timer = NSTimer.scheduledTimerWithTimeInterval(
                interval = intervalMinutes.toDouble() * 60.0,
                repeats = true
            ) { _ ->
                taskCallbacks[taskId]?.invoke()
            }
            NSRunLoop.mainRunLoop.addTimer(timer, NSDefaultRunLoopMode)
            timers[taskId] = timer
        }
    }

    actual fun cancelTask(taskId: String) {
        timers.remove(taskId)?.invalidate()
        taskCallbacks.remove(taskId)
    }

    actual fun cancelAll() {
        timers.values.forEach { it.invalidate() }
        timers.clear()
        taskCallbacks.clear()
    }
}
