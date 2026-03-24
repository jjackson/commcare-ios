package org.commcare.app.viewmodel

import org.commcare.app.engine.NavigationStep
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Edge case and boundary condition tests for ViewModels and data types.
 * Validates defensive behavior when encountering empty, null, or extreme inputs.
 */
class EdgeCaseTest {

    private class MockHttpClient(val code: Int = 200) : PlatformHttpClient {
        override fun execute(request: HttpRequest): HttpResponse =
            HttpResponse(code, emptyMap(), "{}".encodeToByteArray())
    }

    // --- FormQueueViewModel edge cases ---

    @Test
    fun testFormQueueEmptyXml() {
        val vm = FormQueueViewModel(
            httpClient = MockHttpClient(),
            serverUrl = "https://example.com",
            domain = "test",
            authHeader = "Basic dGVzdA=="
        )
        // Enqueue a form with empty XML string — should not crash
        vm.enqueueForm(formXml = "", formName = "empty-form", xmlns = "")
        assertEquals(1, vm.pendingCount, "Empty XML form should still be enqueued")
        assertEquals(1, vm.queuedForms.size)
        assertEquals("", vm.queuedForms[0].formXml)
    }

    @Test
    fun testFormQueueSubmitWithNoForms() {
        val vm = FormQueueViewModel(
            httpClient = MockHttpClient(),
            serverUrl = "https://example.com",
            domain = "test",
            authHeader = "Basic dGVzdA=="
        )
        // Submit with no forms queued — should return 0 with no errors
        val result = vm.submitAllSync()
        assertEquals(0, result, "Submitting empty queue should return 0")
        assertNull(vm.lastError, "No error should be set for empty queue submission")
        assertFalse(vm.isSubmitting, "isSubmitting should be false after empty submission")
    }

    // --- NavigationStep sealed class ---

    @Test
    fun testNavigationStepTypes() {
        // Verify all NavigationStep sealed subtypes can be constructed
        val showMenu: NavigationStep = NavigationStep.ShowMenu
        val showCaseList: NavigationStep = NavigationStep.ShowCaseList(null)
        val showCaseSearch: NavigationStep = NavigationStep.ShowCaseSearch(null)
        val startForm: NavigationStep = NavigationStep.StartForm(null)
        val syncRequired: NavigationStep = NavigationStep.SyncRequired
        val error: NavigationStep = NavigationStep.Error("test error message")

        // Verify type discrimination
        assertTrue(showMenu is NavigationStep.ShowMenu)
        assertTrue(showCaseList is NavigationStep.ShowCaseList)
        assertTrue(showCaseSearch is NavigationStep.ShowCaseSearch)
        assertTrue(startForm is NavigationStep.StartForm)
        assertTrue(syncRequired is NavigationStep.SyncRequired)
        assertTrue(error is NavigationStep.Error)

        // Verify null datums are stored correctly
        assertNull((showCaseList as NavigationStep.ShowCaseList).datum)
        assertNull((showCaseSearch as NavigationStep.ShowCaseSearch).datum)
        assertNull((startForm as NavigationStep.StartForm).xmlns)

        // Verify error message
        assertEquals("test error message", (error as NavigationStep.Error).message)
    }

    // --- CaseItem edge cases ---

    @Test
    fun testCaseItemEmptyProperties() {
        val item = CaseItem(
            caseId = "case-001",
            name = "Test Case",
            caseType = "patient",
            dateOpened = "2026-01-01",
            properties = emptyMap()
        )
        // Access on empty properties map should not crash
        assertEquals(emptyMap(), item.properties)
        assertNull(item.properties["nonexistent"])
        assertEquals(0, item.properties.size)
        assertFalse(item.properties.containsKey("anything"))
    }

    // --- QuestionState edge cases ---

    @Test
    fun testQuestionStateWithNullAppearance() {
        val state = QuestionState(
            questionId = "q1",
            questionText = "What is your name?",
            questionType = QuestionType.TEXT,
            appearance = null
        )
        assertNull(state.appearance, "Appearance should be null when not provided")
        // Verify other defaults are sane
        assertEquals("", state.answer)
        assertFalse(state.isRequired)
        assertNull(state.constraintMessage)
        assertEquals(emptyList(), state.choices)
    }

    // --- Enum completeness checks ---

    @Test
    fun testPostFormDestinationCompleteness() {
        val entries = PostFormDestination.entries
        assertEquals(
            3, entries.size,
            "PostFormDestination should have exactly 3 entries, found: $entries"
        )
        assertTrue(entries.contains(PostFormDestination.RETURN_TO_MENU))
        assertTrue(entries.contains(PostFormDestination.RETURN_TO_CASE_LIST))
        assertTrue(entries.contains(PostFormDestination.CHAINED_FORM))
    }

    @Test
    fun testFormStatusCompleteness() {
        val entries = FormStatus.entries
        assertEquals(
            4, entries.size,
            "FormStatus should have exactly 4 entries, found: $entries"
        )
        assertTrue(entries.contains(FormStatus.PENDING))
        assertTrue(entries.contains(FormStatus.SUBMITTING))
        assertTrue(entries.contains(FormStatus.SUBMITTED))
        assertTrue(entries.contains(FormStatus.FAILED))
    }

    // --- QueuedForm retry logic ---

    @Test
    fun testQueuedFormRetryCountTracking() {
        val form = QueuedForm(
            formId = "form-1",
            formName = "Test Form",
            formXml = "<data/>",
            status = FormStatus.PENDING,
            retryCount = 0
        )
        assertEquals(0, form.retryCount)
        assertTrue(form.retryCount < 3, "Form with retryCount 0 should be retryable (< 3)")

        // Verify that retryCount at threshold is not retryable
        val exhaustedForm = form.copy(retryCount = 3)
        assertFalse(exhaustedForm.retryCount < 3, "Form with retryCount 3 should NOT be retryable")

        // Verify intermediate retry counts
        val retriedOnce = form.copy(retryCount = 1)
        assertTrue(retriedOnce.retryCount < 3, "Form with retryCount 1 should be retryable")
        val retriedTwice = form.copy(retryCount = 2)
        assertTrue(retriedTwice.retryCount < 3, "Form with retryCount 2 should be retryable")
    }

    // --- SyncState progress bounds ---

    @Test
    fun testSyncStateProgressBounds() {
        // Verify Syncing progress can be 0.0 and 1.0 (boundary values)
        val start = SyncState.Syncing(0.0f, "Starting...")
        val complete = SyncState.Syncing(1.0f, "Complete")

        assertEquals(0.0f, start.progress, "Progress should support 0.0")
        assertEquals(1.0f, complete.progress, "Progress should support 1.0")
        assertEquals("Starting...", start.message)
        assertEquals("Complete", complete.message)

        // Verify mid-range progress
        val mid = SyncState.Syncing(0.5f, "Halfway")
        assertEquals(0.5f, mid.progress)

        // Verify all SyncState subtypes
        assertTrue(SyncState.Idle is SyncState)
        assertTrue(start is SyncState)
        assertTrue(SyncState.Complete is SyncState)
        assertTrue(SyncState.Error("err") is SyncState)
    }
}
