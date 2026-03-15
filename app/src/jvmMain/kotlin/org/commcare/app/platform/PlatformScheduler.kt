package org.commcare.app.platform

import java.util.Timer
import java.util.TimerTask

/**
 * JVM scheduler using java.util.Timer for periodic tasks.
 */
actual class PlatformScheduler actual constructor() {
    private val timers = mutableMapOf<String, Timer>()

    actual fun schedulePeriodicTask(taskId: String, intervalMinutes: Int, task: () -> Unit) {
        cancelTask(taskId) // Cancel existing if any
        val timer = Timer("scheduler-$taskId", true)
        val intervalMs = intervalMinutes.toLong() * 60 * 1000
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                try {
                    task()
                } catch (_: Exception) {
                    // Swallow task exceptions to keep timer alive
                }
            }
        }, intervalMs, intervalMs)
        timers[taskId] = timer
    }

    actual fun cancelTask(taskId: String) {
        timers.remove(taskId)?.cancel()
    }

    actual fun cancelAll() {
        timers.values.forEach { it.cancel() }
        timers.clear()
    }
}
