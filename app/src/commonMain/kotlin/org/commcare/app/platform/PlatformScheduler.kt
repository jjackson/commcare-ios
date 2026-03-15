package org.commcare.app.platform

/**
 * Platform-specific scheduler for periodic background tasks (sync, form submission).
 * iOS: BGTaskScheduler, JVM: java.util.Timer
 */
expect class PlatformScheduler() {
    /**
     * Schedule a periodic task.
     * @param taskId Unique identifier for the task
     * @param intervalMinutes Interval in minutes between executions
     * @param task The task to execute
     */
    fun schedulePeriodicTask(taskId: String, intervalMinutes: Int, task: () -> Unit)

    /**
     * Cancel a scheduled task.
     */
    fun cancelTask(taskId: String)

    /**
     * Cancel all scheduled tasks.
     */
    fun cancelAll()
}
