package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.commcare.app.model.Message
import org.commcare.app.model.MessageThread
import org.commcare.app.network.ConnectMarketplaceApi

/**
 * ViewModel for the Connect messaging hub.
 *
 * Manages thread list state, per-thread message state, consent, and message
 * composition. All API calls run on a background coroutine and update
 * mutableStateOf fields so the Compose UI reacts automatically.
 *
 * Features:
 * - 30-second polling when a thread is open (startPolling/stopPolling)
 * - Unsent message retry: failed sends are queued and retried via retrySending()
 * - Consent flow with loading state and error reporting
 */
class MessagingViewModel(
    private val api: ConnectMarketplaceApi,
    private val tokenManager: ConnectIdTokenManager
) {
    var threads by mutableStateOf<List<MessageThread>>(emptyList())
    var currentThreadMessages by mutableStateOf<List<Message>>(emptyList())
    var selectedThread by mutableStateOf<MessageThread?>(null)
    var isLoading by mutableStateOf(false)
    var isConsentLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var hasConsented by mutableStateOf(false)
    var messageText by mutableStateOf("")
    var unreadCount by mutableStateOf(0)
    var unsentCount by mutableStateOf(0)

    private val scope = CoroutineScope(Dispatchers.Default)

    // Polling
    private var pollingJob: Job? = null

    // Unsent message queue: pairs of (threadId, content)
    private val unsentMessages = mutableListOf<Pair<String, String>>()

    fun clearError() { errorMessage = null }

    /**
     * Fetch the list of message threads. Updates [threads] and [unreadCount].
     */
    fun loadThreads() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    isLoading = false
                    return@launch
                }
                val result = api.getMessages(token)
                result.fold(
                    onSuccess = { list ->
                        threads = list
                        unreadCount = list.sumOf { it.unreadCount }
                    },
                    onFailure = { errorMessage = "Failed to load messages: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Load error: ${e::class.simpleName}: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Select a thread to view and load its messages.
     */
    fun selectThread(thread: MessageThread) {
        selectedThread = thread
        loadMessages(thread.id)
    }

    /**
     * Clear the selected thread and return to the thread list.
     */
    fun clearThread() {
        stopPolling()
        selectedThread = null
        currentThreadMessages = emptyList()
    }

    /**
     * Fetch all messages in [threadId] and populate [currentThreadMessages].
     */
    fun loadMessages(threadId: String) {
        isLoading = true
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    isLoading = false
                    return@launch
                }
                val result = api.getThreadMessages(token, threadId)
                result.fold(
                    onSuccess = { messages -> currentThreadMessages = messages },
                    onFailure = { errorMessage = "Failed to load messages: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Load messages error: ${e::class.simpleName}: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Start polling for new messages every 30 seconds.
     * Cancels any existing polling job before starting a new one.
     * Call this when entering a thread view.
     */
    fun startPolling(threadId: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (true) {
                delay(30_000L)
                loadMessages(threadId)
            }
        }
    }

    /**
     * Stop the background polling job.
     * Call this when leaving a thread view.
     */
    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /**
     * Send [messageText] to the selected thread, then reload the thread messages.
     * On failure the message is queued in [unsentMessages] for retry.
     */
    fun sendMessage() {
        val thread = selectedThread ?: return
        val text = messageText.trim()
        if (text.isBlank()) return
        messageText = ""
        sendMessageInternal(thread.id, text)
    }

    private fun sendMessageInternal(threadId: String, content: String) {
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    unsentMessages.add(Pair(threadId, content))
                    unsentCount = unsentMessages.size
                    return@launch
                }
                val result = api.sendMessage(token, threadId, content)
                result.fold(
                    onSuccess = {
                        loadMessages(threadId)
                    },
                    onFailure = {
                        errorMessage = "Failed to send message: ${it.message}"
                        unsentMessages.add(Pair(threadId, content))
                        unsentCount = unsentMessages.size
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Send error: ${e::class.simpleName}: ${e.message}"
                unsentMessages.add(Pair(threadId, content))
                unsentCount = unsentMessages.size
            }
        }
    }

    /**
     * Retry all queued unsent messages.
     * Should be called when the user returns to a thread or when connectivity is restored.
     */
    fun retrySending() {
        val toRetry = unsentMessages.toList()
        unsentMessages.clear()
        unsentCount = 0
        for ((threadId, content) in toRetry) {
            sendMessageInternal(threadId, content)
        }
    }

    /**
     * POST consent for messaging, then refresh threads on success.
     * Sets [isConsentLoading] during the API call so the UI can show a spinner.
     */
    fun updateConsent() {
        isConsentLoading = true
        errorMessage = null
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    isConsentLoading = false
                    return@launch
                }
                val result = api.updateConsent(token)
                result.fold(
                    onSuccess = {
                        hasConsented = true
                        isConsentLoading = false
                        loadThreads()
                    },
                    onFailure = {
                        errorMessage = "Failed to enable messaging: ${it.message}"
                        isConsentLoading = false
                    }
                )
            } catch (e: Exception) {
                errorMessage = "Consent error: ${e::class.simpleName}: ${e.message}"
                isConsentLoading = false
            }
        }
    }

    /**
     * Mark a single message as read, then reload the thread to reflect the update.
     */
    fun markAsRead(messageId: String) {
        val thread = selectedThread ?: return
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken() ?: return@launch
                val result = api.markAsRead(token, messageId)
                result.fold(
                    onSuccess = { loadMessages(thread.id) },
                    onFailure = { /* non-fatal — ignore silently */ }
                )
            } catch (e: Exception) {
                // Non-fatal; swallow
            }
        }
    }
}
