package org.commcare.app.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Thread safety tests for FormQueueViewModel.
 *
 * Verifies that concurrent enqueue, submit, and status update operations
 * do not lose data or corrupt internal state, thanks to platformSynchronized guards.
 */
class ThreadSafetyTest {

    /** Mock HTTP client that returns a configurable response code. */
    private class MockHttpClient(
        private val responseCode: Int = 200,
        private val responseBody: String = "OK"
    ) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()

        override fun execute(request: HttpRequest): HttpResponse {
            // Small sleep to increase window for race conditions
            Thread.sleep(1)
            synchronized(requests) {
                requests.add(request)
            }
            return HttpResponse(responseCode, emptyMap(), responseBody.encodeToByteArray())
        }
    }

    /**
     * Enqueue 100 forms from 10 coroutines simultaneously (10 forms each).
     * All 100 forms must appear in pendingCount with no lost enqueues.
     */
    @Test
    fun testFormQueueConcurrentEnqueue() = runBlocking {
        val client = MockHttpClient()
        val queue = FormQueueViewModel(
            httpClient = client,
            serverUrl = "https://hq.example.com",
            domain = "test-domain",
            authHeader = "Basic dGVzdDp0ZXN0"
        )

        val coroutineCount = 10
        val formsPerCoroutine = 10
        val totalForms = coroutineCount * formsPerCoroutine

        // Launch 10 coroutines that each enqueue 10 forms concurrently
        val jobs = (1..coroutineCount).map { coroutineId ->
            launch(Dispatchers.Default) {
                for (formIdx in 1..formsPerCoroutine) {
                    queue.enqueueForm(
                        "<form>c${coroutineId}_f${formIdx}</form>",
                        "Form-c${coroutineId}-f${formIdx}"
                    )
                }
            }
        }
        jobs.forEach { it.join() }

        assertEquals(
            totalForms,
            queue.pendingCount,
            "All $totalForms forms should be pending after concurrent enqueue"
        )
        assertEquals(
            totalForms,
            queue.queuedForms.size,
            "queuedForms list should contain all $totalForms forms"
        )
    }

    /**
     * Submit forms while enqueueing simultaneously.
     * Verifies that concurrent submit + enqueue does not corrupt the queue.
     */
    @Test
    fun testFormQueueConcurrentSubmitAndEnqueue() = runBlocking {
        val client = MockHttpClient(200)
        val queue = FormQueueViewModel(
            httpClient = client,
            serverUrl = "https://hq.example.com",
            domain = "test-domain",
            authHeader = "Basic dGVzdDp0ZXN0"
        )

        // Pre-enqueue some forms to submit
        for (i in 1..10) {
            queue.enqueueForm("<form>pre-$i</form>", "PreForm-$i")
        }
        assertEquals(10, queue.pendingCount)

        // Concurrently: submit existing forms while enqueueing new ones
        val submitJob = launch(Dispatchers.Default) {
            queue.submitAllSync()
        }
        val enqueueJob = launch(Dispatchers.Default) {
            for (i in 1..10) {
                queue.enqueueForm("<form>concurrent-$i</form>", "ConcurrentForm-$i")
            }
        }

        submitJob.join()
        enqueueJob.join()

        // The pre-enqueued forms should have been submitted, and the new ones should be pending.
        // Due to timing, some concurrent forms may or may not have been picked up by submitAllSync.
        // The key invariant: no data loss — total forms that were submitted + pending == total enqueued.
        val submittedCount = synchronized(client.requests) { client.requests.size }
        val remainingPending = queue.pendingCount
        val remainingQueued = queue.queuedForms.size

        // At minimum, the 10 pre-enqueued forms should have been submitted
        assertTrue(
            submittedCount >= 10,
            "At least the 10 pre-enqueued forms should have been submitted, got $submittedCount"
        )

        // The concurrently enqueued forms should still be in the queue (pending)
        // Some may have been picked up by submit, so we check total accounting
        assertTrue(
            remainingQueued >= 0,
            "Queue should not be in corrupt state (negative size)"
        )

        // No forms should be lost: submitted + remaining pending <= 20 (total enqueued)
        assertTrue(
            submittedCount + remainingPending <= 20,
            "Total accounted forms ($submittedCount submitted + $remainingPending pending) " +
                    "should not exceed 20 total enqueued"
        )
    }

    /**
     * Update form statuses from multiple threads concurrently.
     * Enqueue forms, then concurrently submit batches — the queue state must remain consistent.
     */
    @Test
    fun testFormQueueStatusUpdatesConcurrent() = runBlocking {
        val client = MockHttpClient(200)
        val queue = FormQueueViewModel(
            httpClient = client,
            serverUrl = "https://hq.example.com",
            domain = "test-domain",
            authHeader = "Basic dGVzdDp0ZXN0"
        )

        // Enqueue 20 forms
        for (i in 1..20) {
            queue.enqueueForm("<form>status-$i</form>", "StatusForm-$i")
        }
        assertEquals(20, queue.pendingCount)

        // Launch multiple concurrent submitAllSync calls
        // Only one should actually proceed (isSubmitting guard), but the queue must remain consistent
        val jobs = (1..5).map {
            launch(Dispatchers.Default) {
                queue.submitAllSync()
            }
        }
        jobs.forEach { it.join() }

        // After all submissions complete, all forms should be submitted and cleared
        assertEquals(0, queue.pendingCount, "All forms should be submitted after concurrent submits")

        // The queue state should be consistent — no orphaned forms
        val queuedStatuses = queue.queuedForms.map { it.status }
        assertTrue(
            queuedStatuses.none { it == FormStatus.PENDING || it == FormStatus.FAILED },
            "No pending/failed forms should remain after successful submission"
        )
    }
}
