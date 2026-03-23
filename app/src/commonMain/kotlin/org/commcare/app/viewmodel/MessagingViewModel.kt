package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
 *
 * TODO: Add end-to-end message encryption using AES-256-GCM.
 *   Requires server-side key exchange API (not yet available). When the HQ
 *   endpoint for per-thread symmetric key negotiation is ready, encrypt
 *   outgoing message content before sending and decrypt incoming messages
 *   after receipt. The [isEncryptionEnabled] flag should be flipped to true
 *   once the implementation is in place.
 */
class MessagingViewModel(
    private val api: ConnectMarketplaceApi,
    private val tokenManager: ConnectIdTokenManager
) {
    /**
     * Whether end-to-end encryption is active for messages.
     * Currently false — encryption is deferred until the server-side
     * key exchange API is available.
     */
    val isEncryptionEnabled: Boolean = false
    var threads by mutableStateOf<List<MessageThread>>(emptyList())
        private set
    var currentThreadMessages by mutableStateOf<List<Message>>(emptyList())
        private set
    var selectedThread by mutableStateOf<MessageThread?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var isConsentLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var hasConsented by mutableStateOf(false)
        private set
    var messageText by mutableStateOf("")
    var unreadCount by mutableStateOf(0)
        private set
    var unsentCount by mutableStateOf(0)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

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
     * POST global consent for messaging, then refresh threads on success.
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
     * Toggle consent for a specific channel/thread.
     * Updates the thread's isConsented flag optimistically, then calls the API.
     * On failure, reverts the optimistic update.
     */
    fun toggleChannelConsent(threadId: String) {
        val threadIndex = threads.indexOfFirst { it.id == threadId }
        if (threadIndex < 0) return

        val thread = threads[threadIndex]
        val newConsent = !thread.isConsented

        // Optimistic update
        threads = threads.toMutableList().also {
            it[threadIndex] = thread.copy(isConsented = newConsent)
        }

        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken() ?: return@launch
                val result = api.updateChannelConsent(token, threadId, newConsent)
                result.fold(
                    onSuccess = { /* optimistic update already applied */ },
                    onFailure = {
                        // Revert optimistic update
                        threads = threads.toMutableList().also {
                            val idx = it.indexOfFirst { t -> t.id == threadId }
                            if (idx >= 0) it[idx] = thread
                        }
                        errorMessage = "Failed to update consent: ${it.message}"
                    }
                )
            } catch (e: Exception) {
                // Revert on exception
                threads = threads.toMutableList().also {
                    val idx = it.indexOfFirst { t -> t.id == threadId }
                    if (idx >= 0) it[idx] = thread
                }
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
