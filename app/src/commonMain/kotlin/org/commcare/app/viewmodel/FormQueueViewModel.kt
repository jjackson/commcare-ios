package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.platform.currentEpochSeconds
import org.commcare.app.storage.CommCareDatabase
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient
import org.javarosa.core.util.platformSynchronized

/**
 * Manages the offline form submission queue.
 * Forms are queued locally in SQLDelight and submitted during sync.
 */
class FormQueueViewModel(
    private val httpClient: PlatformHttpClient,
    private val serverUrl: String,
    private val domain: String,
    private val authHeader: String,
    private val db: CommCareDatabase? = null
) {
    var queuedForms by mutableStateOf<List<QueuedForm>>(emptyList())
        private set
    var isSubmitting by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    private val formQueue = mutableListOf<QueuedForm>()
    private val queueLock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

    /**
     * Load pending forms from SQLDelight on startup.
     */
    fun loadFromDatabase() {
        if (db == null) return
        try {
            val pending = db.commCareQueries.selectPendingForms().executeAsList()
            for (row in pending) {
                formQueue.add(QueuedForm(
                    formId = row.form_id,
                    formName = row.xmlns,
                    formXml = row.xml_content,
                    status = FormStatus.PENDING,
                    retryCount = 0
                ))
            }
            queuedForms = formQueue.toList()
        } catch (_: Exception) {
            // DB not yet initialized
        }
    }

    fun enqueueForm(formXml: String, formName: String, xmlns: String = "") {
        val formId = generateId()
        val form = QueuedForm(
            formId = formId,
            formName = formName,
            formXml = formXml,
            status = FormStatus.PENDING,
            retryCount = 0
        )
        platformSynchronized(queueLock) {
            formQueue.add(form)
            queuedForms = formQueue.toList()
        }

        // Persist to SQLDelight
        db?.commCareQueries?.insertFormQueue(
            form_id = formId,
            xmlns = xmlns.ifEmpty { formName },
            xml_content = formXml,
            status = "pending",
            created_at = currentTimestamp(),
            submitted_at = null
        )
    }

    /**
     * Submit all pending forms asynchronously. For UI button clicks.
     */
    fun submitAll() {
        scope.launch {
            submitAllSync()
        }
    }

    /**
     * Submit all pending forms synchronously. Returns count of successfully submitted forms.
     * Called directly from SyncViewModel (already on background thread).
     */
    fun submitAllSync(): Int {
        if (isSubmitting) return 0
        isSubmitting = true
        lastError = null
        var submitted = 0

        try {
            val submitUrl = "${serverUrl.trimEnd('/')}/a/$domain/receiver/"
            val maxRetries = 3
            val pending = platformSynchronized(queueLock) {
                formQueue.filter {
                    (it.status == FormStatus.PENDING || it.status == FormStatus.FAILED) && it.retryCount < maxRetries
                }
            }

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
                        db?.commCareQueries?.updateFormStatus("submitted", currentTimestamp(), form.formId)
                        submitted++
                    } else if (response.code == 401) {
                        updateFormStatus(form.formId, FormStatus.FAILED)
                        db?.commCareQueries?.updateFormStatus("failed", null, form.formId)
                        lastError = "Authentication expired"
                        break
                    } else {
                        updateFormStatus(form.formId, FormStatus.FAILED, form.retryCount + 1)
                        db?.commCareQueries?.updateFormStatus("failed", null, form.formId)
                    }
                } catch (e: Exception) {
                    updateFormStatus(form.formId, FormStatus.FAILED, form.retryCount + 1)
                }
            }
        } finally {
            isSubmitting = false
            // Clean up submitted forms
            platformSynchronized(queueLock) {
                formQueue.removeAll { it.status == FormStatus.SUBMITTED }
                queuedForms = formQueue.toList()
            }
        }
        return submitted
    }

    fun clearSubmitted() {
        platformSynchronized(queueLock) {
            formQueue.removeAll { it.status == FormStatus.SUBMITTED }
            queuedForms = formQueue.toList()
        }
        db?.commCareQueries?.deleteSubmittedForms()
    }

    val pendingCount: Int
        get() = platformSynchronized(queueLock) {
            formQueue.count { it.status == FormStatus.PENDING || it.status == FormStatus.FAILED }
        }

    private fun updateFormStatus(formId: String, status: FormStatus, retryCount: Int? = null) {
        platformSynchronized(queueLock) {
            val index = formQueue.indexOfFirst { it.formId == formId }
            if (index >= 0) {
                formQueue[index] = formQueue[index].copy(
                    status = status,
                    retryCount = retryCount ?: formQueue[index].retryCount
                )
                queuedForms = formQueue.toList()
            }
        }
    }

    private var nextId = 1
    private fun generateId(): String = "form-${nextId++}"

    /**
     * Returns the current time as an ISO 8601 UTC string (e.g. "2026-03-21T14:30:00Z").
     * Uses the platform-specific [currentEpochSeconds] expect/actual.
     */
    private fun currentTimestamp(): String {
        val epochSeconds = currentEpochSeconds()
        // Manual ISO 8601 formatting without kotlinx-datetime dependency
        val s = epochSeconds % 60
        val totalMinutes = epochSeconds / 60
        val m = totalMinutes % 60
        val totalHours = totalMinutes / 60
        val h = totalHours % 24
        var totalDays = totalHours / 24

        // Convert days since epoch (1970-01-01) to year/month/day
        var year = 1970
        while (true) {
            val daysInYear = if (isLeapYear(year)) 366L else 365L
            if (totalDays < daysInYear) break
            totalDays -= daysInYear
            year++
        }
        val monthDays = if (isLeapYear(year))
            intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        else
            intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var month = 1
        for (md in monthDays) {
            if (totalDays < md) break
            totalDays -= md
            month++
        }
        val day = totalDays + 1

        return "${year.pad4()}-${month.pad2()}-${day.toLong().pad2()}T${h.pad2()}:${m.pad2()}:${s.pad2()}Z"
    }

    private fun isLeapYear(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    private fun Int.pad4(): String = toString().padStart(4, '0')
    private fun Int.pad2(): String = toString().padStart(2, '0')
    private fun Long.pad2(): String = toString().padStart(2, '0')
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
