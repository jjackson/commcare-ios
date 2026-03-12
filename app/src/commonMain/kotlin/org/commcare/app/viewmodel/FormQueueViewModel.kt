package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient

/**
 * Manages the offline form submission queue.
 * Forms are queued locally and submitted during sync.
 */
class FormQueueViewModel(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String
) {
    var queuedForms by mutableStateOf<List<QueuedForm>>(emptyList())
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val formQueue = mutableListOf<QueuedForm>()

    fun enqueueForm(formXml: String, formName: String) {
        val form = QueuedForm(
            formId = generateId(),
            formName = formName,
            formXml = formXml,
            status = FormStatus.PENDING,
            retryCount = 0
        )
        formQueue.add(form)
        queuedForms = formQueue.toList()
    }

    fun submitAll(): Int {
        if (isSubmitting) return 0
        isSubmitting = true
        lastError = null
        var submitted = 0

        try {
            val submitUrl = "${serverUrl.trimEnd('/')}/a/$domain/receiver/"
            val pending = formQueue.filter { it.status == FormStatus.PENDING || it.status == FormStatus.FAILED }

            for (form in pending) {
                try {
                    updateFormStatus(form.formId, FormStatus.SUBMITTING)

                    val response = httpClient.execute(
                        HttpRequest(
                            url = submitUrl,
                            method = "POST",
                            headers = mapOf(
                                "Authorization" to authHeader,
                                "Content-Type" to "text/xml"
                            ),
                            body = form.formXml.encodeToByteArray()
                        )
                    )

                    if (response.code in 200..299) {
                        updateFormStatus(form.formId, FormStatus.SUBMITTED)
                        submitted++
                    } else if (response.code == 401) {
                        updateFormStatus(form.formId, FormStatus.FAILED)
                        lastError = "Authentication expired"
                        break
                    } else {
                        updateFormStatus(form.formId, FormStatus.FAILED, form.retryCount + 1)
                    }
                } catch (e: Exception) {
                    updateFormStatus(form.formId, FormStatus.FAILED, form.retryCount + 1)
                }
            }
        } finally {
            isSubmitting = false
            // Clean up submitted forms
            formQueue.removeAll { it.status == FormStatus.SUBMITTED }
            queuedForms = formQueue.toList()
        }
        return submitted
    }

    fun clearSubmitted() {
        formQueue.removeAll { it.status == FormStatus.SUBMITTED }
        queuedForms = formQueue.toList()
    }

    val pendingCount: Int
        get() = formQueue.count { it.status == FormStatus.PENDING || it.status == FormStatus.FAILED }

    private fun updateFormStatus(formId: String, status: FormStatus, retryCount: Int? = null) {
        val index = formQueue.indexOfFirst { it.formId == formId }
        if (index >= 0) {
            formQueue[index] = formQueue[index].copy(
                status = status,
                retryCount = retryCount ?: formQueue[index].retryCount
            )
            queuedForms = formQueue.toList()
        }
    }

    private var nextId = 1
    private fun generateId(): String = "form-${nextId++}"
}

data class QueuedForm(
    val formId: String,
    val formName: String,
    val formXml: String,
    val status: FormStatus,
    val retryCount: Int
)

enum class FormStatus {
    PENDING,
    SUBMITTING,
    SUBMITTED,
    FAILED
}
