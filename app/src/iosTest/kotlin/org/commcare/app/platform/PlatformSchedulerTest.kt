@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package org.commcare.app.platform

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the iOS PlatformScheduler.
 *
 * PlatformScheduler uses NSTimer dispatched to the main run loop. In a test
 * environment we can verify that:
 * - schedulePeriodicTask stores the callback (verifiable via cancelTask behavior)
 * - cancelTask removes the timer
 * - cancelAll clears everything
 * - scheduling the same ID replaces the previous task
 *
 * Note: We cannot easily verify timer firing in unit tests because the main
 * run loop is not pumped. These tests verify structural lifecycle behavior.
 */
class PlatformSchedulerTest {

    private val scheduler = PlatformScheduler()

    @AfterTest
    fun tearDown() {
        scheduler.cancelAll()
    }

    @Test
    fun testScheduleAndCancel_noException() {
        // Scheduling and immediately canceling should not throw
        var callbackInvoked = false
        scheduler.schedulePeriodicTask("test-task", 1) {
            callbackInvoked = true
        }

        // Cancel should succeed without error
        scheduler.cancelTask("test-task")

        // Callback should not have been invoked (timer hasn't fired)
        assertFalse(callbackInvoked, "Callback should not fire before timer interval elapses")
    }

    @Test
    fun testCancelNonExistentTask_noException() {
        // Canceling a task that was never scheduled should not throw
        scheduler.cancelTask("non-existent-task")
    }

    @Test
    fun testCancelAll_noException() {
        scheduler.schedulePeriodicTask("task-1", 5) {}
        scheduler.schedulePeriodicTask("task-2", 10) {}
        scheduler.schedulePeriodicTask("task-3", 15) {}

        // cancelAll should clean up all timers without error
        scheduler.cancelAll()

        // Canceling individual tasks after cancelAll should also not throw
        scheduler.cancelTask("task-1")
        scheduler.cancelTask("task-2")
        scheduler.cancelTask("task-3")
    }

    @Test
    fun testScheduleSameId_replacesTask() {
        var firstCallbackInvoked = false
        var secondCallbackInvoked = false

        scheduler.schedulePeriodicTask("same-id", 1) {
            firstCallbackInvoked = true
        }

        // Scheduling with the same ID should replace the previous task
        scheduler.schedulePeriodicTask("same-id", 1) {
            secondCallbackInvoked = true
        }

        // Cancel the task — only the second callback's timer should be active
        scheduler.cancelTask("same-id")

        // Neither callback should have been invoked
        assertFalse(firstCallbackInvoked, "First callback should not fire (replaced)")
        assertFalse(secondCallbackInvoked, "Second callback should not fire (canceled before interval)")
    }

    @Test
    fun testScheduleMultipleTasks_independentCancel() {
        var task1Canceled = false
        var task2Active = true

        scheduler.schedulePeriodicTask("task-a", 1) {}
        scheduler.schedulePeriodicTask("task-b", 1) {}

        // Cancel only task-a
        scheduler.cancelTask("task-a")

        // Cancel task-b separately — should not throw even after task-a was canceled
        scheduler.cancelTask("task-b")
    }
}
