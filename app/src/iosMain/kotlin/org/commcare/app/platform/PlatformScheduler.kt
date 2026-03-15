package org.commcare.app.platform

import platform.Foundation.NSTimer
import platform.Foundation.NSRunLoop
import platform.Foundation.NSDefaultRunLoopMode

/**
 * iOS scheduler using NSTimer for periodic tasks.
 * For true background execution, BGTaskScheduler should be registered in the app delegate.
 * This implementation handles foreground periodic tasks.
 */
actual class PlatformScheduler actual constructor() {
    private val timers = mutableMapOf<String, NSTimer>()

    actual fun schedulePeriodicTask(taskId: String, intervalMinutes: Int, task: () -> Unit) {
        cancelTask(taskId)
        val interval = intervalMinutes.toDouble() * 60.0
        val timer = NSTimer.scheduledTimerWithTimeInterval(
            interval = interval,
            repeats = true
        ) { _ ->
            try {
                task()
            } catch (_: Exception) {
                // Swallow task exceptions
            }
        }
        timers[taskId] = timer
    }

    actual fun cancelTask(taskId: String) {
        timers.remove(taskId)?.invalidate()
    }

    actual fun cancelAll() {
        timers.values.forEach { it.invalidate() }
        timers.clear()
    }
}
