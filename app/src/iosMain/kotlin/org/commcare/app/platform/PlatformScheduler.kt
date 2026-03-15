package org.commcare.app.platform

/**
 * iOS scheduler stub.
 * Full implementation requires BGTaskScheduler for true background execution,
 * or NSTimer for foreground periodic tasks.
 */
actual class PlatformScheduler actual constructor() {
    private val taskCallbacks = mutableMapOf<String, () -> Unit>()

    actual fun schedulePeriodicTask(taskId: String, intervalMinutes: Int, task: () -> Unit) {
        cancelTask(taskId)
        taskCallbacks[taskId] = task
        // TODO: Register with BGTaskScheduler or use NSTimer
    }

    actual fun cancelTask(taskId: String) {
        taskCallbacks.remove(taskId)
    }

    actual fun cancelAll() {
        taskCallbacks.clear()
    }
}
