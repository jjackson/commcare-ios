package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 */
class MessagingViewModel(
    private val api: ConnectMarketplaceApi,
    private val tokenManager: ConnectIdTokenManager
) {
    var threads by mutableStateOf<List<MessageThread>>(emptyList())
    var currentThreadMessages by mutableStateOf<List<Message>>(emptyList())
    var selectedThread by mutableStateOf<MessageThread?>(null)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var hasConsented by mutableStateOf(false)
    var messageText by mutableStateOf("")
    var unreadCount by mutableStateOf(0)

    private val scope = CoroutineScope(Dispatchers.Default)

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
     * Send [messageText] to the selected thread, then reload the thread messages.
     */
    fun sendMessage() {
        val thread = selectedThread ?: return
        val text = messageText.trim()
        if (text.isBlank()) return
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    return@launch
                }
                val result = api.sendMessage(token, thread.id, text)
                result.fold(
                    onSuccess = {
                        messageText = ""
                        loadMessages(thread.id)
                    },
                    onFailure = { errorMessage = "Failed to send message: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Send error: ${e::class.simpleName}: ${e.message}"
            }
        }
    }

    /**
     * POST consent for messaging, then refresh threads on success.
     */
    fun updateConsent() {
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    return@launch
                }
                val result = api.updateConsent(token)
                result.fold(
                    onSuccess = {
                        hasConsented = true
                        loadThreads()
                    },
                    onFailure = { errorMessage = "Failed to enable messaging: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Consent error: ${e::class.simpleName}: ${e.message}"
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
