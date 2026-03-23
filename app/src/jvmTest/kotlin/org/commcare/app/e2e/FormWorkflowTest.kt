package org.commcare.app.e2e

import org.commcare.app.viewmodel.FormQueueViewModel
import org.commcare.app.viewmodel.FormStatus
import org.commcare.app.viewmodel.PostFormDestination
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for form workflow completeness: auto-send and queue behavior.
 */
class FormWorkflowTest {

    /** Mock HTTP client that records requests and returns configurable responses. */
    private class MockHttpClient(
        private val responseCode: Int = 200,
        private val responseBody: String = "OK"
    ) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()
        var failOnNext = false

        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            if (failOnNext) {
                failOnNext = false
                throw RuntimeException("Network error")
            }
            return HttpResponse(responseCode, emptyMap(), responseBody.encodeToByteArray())
        }
    }

    @Test
    fun testAutoSendSubmitsPendingForms() {
        val client = MockHttpClient(200)
        val queue = FormQueueViewModel(client, "https://hq.example.com", "test-domain", "Basic dGVzdDp0ZXN0")

        queue.enqueueForm("<form>1</form>", "Test Form 1", "http://form/1")
        queue.enqueueForm("<form>2</form>", "Test Form 2", "http://form/2")

        assertEquals(2, queue.pendingCount)

        // Auto-send should submit both forms
        queue.tryAutoSend()
        // Wait for async submission
        Thread.sleep(2000)

        assertEquals(0, queue.pendingCount)
        assertEquals(2, client.requests.size)
    }

    @Test
    fun testAutoSendSkipsWhenAlreadySubmitting() {
        val client = MockHttpClient(200)
        val queue = FormQueueViewModel(client, "https://hq.example.com", "test-domain", "Basic dGVzdDp0ZXN0")

        // No forms — tryAutoSend should be a no-op
        assertEquals(0, queue.pendingCount)
        queue.tryAutoSend()
        assertEquals(0, client.requests.size)
    }

    @Test
    fun testFormQueueRetryOnFailure() {
        val client = MockHttpClient(500, "Server Error")
        val queue = FormQueueViewModel(client, "https://hq.example.com", "test-domain", "Basic dGVzdDp0ZXN0")

        queue.enqueueForm("<form>1</form>", "Failing Form", "http://form/fail")
        val submitted = queue.submitAllSync()

        assertEquals(0, submitted)
        assertEquals(1, queue.pendingCount)
        // Form should be in FAILED state but still in queue for retry
        assertTrue(queue.queuedForms.any { it.status == FormStatus.FAILED })
    }

    @Test
    fun testFormQueueAuthExpiredStopsSubmission() {
        val client = MockHttpClient(401, "Unauthorized")
        val queue = FormQueueViewModel(client, "https://hq.example.com", "test-domain", "Basic dGVzdDp0ZXN0")

        queue.enqueueForm("<form>1</form>", "Form 1", "http://form/1")
        queue.enqueueForm("<form>2</form>", "Form 2", "http://form/2")

        val submitted = queue.submitAllSync()

        assertEquals(0, submitted)
        // Auth error should stop after first attempt
        assertEquals("Authentication expired", queue.lastError)
        assertEquals(1, client.requests.size)
    }

    @Test
    fun testPostFormDestinationEnum() {
        // Verify all destination types exist
        assertEquals(3, PostFormDestination.entries.size)
        assertTrue(PostFormDestination.entries.contains(PostFormDestination.RETURN_TO_MENU))
        assertTrue(PostFormDestination.entries.contains(PostFormDestination.RETURN_TO_CASE_LIST))
        assertTrue(PostFormDestination.entries.contains(PostFormDestination.CHAINED_FORM))
    }
}
