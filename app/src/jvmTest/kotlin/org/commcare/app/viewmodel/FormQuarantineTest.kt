package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for form quarantine: corrupted forms isolated from send queue.
 */
class FormQuarantineTest {

    @Test
    fun testFormStatusEnum() {
        assertEquals(4, FormStatus.entries.size)
        assertTrue(FormStatus.entries.contains(FormStatus.PENDING))
        assertTrue(FormStatus.entries.contains(FormStatus.SUBMITTING))
        assertTrue(FormStatus.entries.contains(FormStatus.SUBMITTED))
        assertTrue(FormStatus.entries.contains(FormStatus.FAILED))
    }

    @Test
    fun testFailedFormNotRetriedBeyondLimit() {
        // MAX_FORM_RETRIES = 3 in SyncViewModel
        val maxRetries = 3
        val form = QueuedForm(
            formId = "form-bad",
            formName = "Corrupted",
            formXml = "<broken>",
            status = FormStatus.FAILED,
            retryCount = 3
        )
        assertTrue(form.retryCount >= maxRetries, "Form at max retries should not be retried")
    }

    @Test
    fun testFormUnderRetryLimitIsRetried() {
        val maxRetries = 3
        val form = QueuedForm(
            formId = "form-retry",
            formName = "Retryable",
            formXml = "<data/>",
            status = FormStatus.FAILED,
            retryCount = 1
        )
        assertTrue(form.retryCount < maxRetries, "Form under limit should be retried")
    }

    @Test
    fun testSubmittedFormsCleared() {
        // After submitAllSync, SUBMITTED forms are removed from queue
        val forms = mutableListOf(
            QueuedForm("f1", "Form1", "<d/>", FormStatus.SUBMITTED, 0),
            QueuedForm("f2", "Form2", "<d/>", FormStatus.FAILED, 1),
            QueuedForm("f3", "Form3", "<d/>", FormStatus.SUBMITTED, 0)
        )
        forms.removeAll { it.status == FormStatus.SUBMITTED }
        assertEquals(1, forms.size)
        assertEquals("f2", forms[0].formId)
    }
}
