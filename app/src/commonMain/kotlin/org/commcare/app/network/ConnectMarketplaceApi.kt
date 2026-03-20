package org.commcare.app.network

import org.commcare.app.model.DeliveryStatus
import org.commcare.app.model.LearnModule
import org.commcare.app.model.Message
import org.commcare.app.model.MessageThread
import org.commcare.app.model.Opportunity
import org.commcare.app.model.PaymentInfo
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient
import org.commcare.core.interfaces.createHttpClient

/**
 * HTTP client for the CommCare-Connect marketplace server.
 * This is a SEPARATE server from the ConnectID identity server (connectid.dimagi.com).
 * Handles opportunities, payments, and messaging.
 *
 * Uses Bearer token authentication with the ConnectID access token.
 * JSON parsing uses simple string extraction (no kotlinx.serialization dependency),
 * matching the pattern in ConnectIdApi.
 */
class ConnectMarketplaceApi(
    private val httpClient: PlatformHttpClient = createHttpClient()
) {
    // Base URL — the CommCare-Connect server (separate from ConnectID identity server)
    // TODO: Configure actual marketplace server URL
    var baseUrl: String = "https://connect.dimagi.com"

    /**
     * Fetch all available opportunities for this user.
     * GET /api/opportunity/
     * Authorization: Bearer <access_token>
     */
    fun getOpportunities(accessToken: String): Result<List<Opportunity>> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get opportunities failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            val objects = parseJsonArray(json)
            val opportunities = objects.map { parseOpportunity(it) }
            Result.success(opportunities)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get opportunities request failed: ${e.message}", e))
        }
    }

    /**
     * Fetch details of a single opportunity.
     * GET /api/opportunity/{id}/
     * Authorization: Bearer <access_token>
     */
    fun getOpportunityDetail(accessToken: String, id: String): Result<Opportunity> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/${escapeJson(id)}/",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get opportunity detail failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            Result.success(parseOpportunity(json))
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get opportunity detail request failed: ${e.message}", e))
        }
    }

    /**
     * Claim an opportunity for this user.
     * POST /api/opportunity/{id}/claim
     * Authorization: Bearer <access_token>
     */
    fun claimOpportunity(accessToken: String, id: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/api/opportunity/${escapeJson(id)}/claim",
            accessToken,
            body = "{}"
        )
    }

    /**
     * Start the learn app for an opportunity.
     * POST /users/start_learn_app
     * Authorization: Bearer <access_token>
     */
    fun startLearnApp(accessToken: String, opportunityId: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/users/start_learn_app",
            accessToken,
            body = """{"opportunity_id":"${escapeJson(opportunityId)}"}"""
        )
    }

    /**
     * Get learn progress (list of modules) for an opportunity.
     * GET /api/opportunity/{id}/learn_progress
     * Authorization: Bearer <access_token>
     */
    fun getLearnProgress(accessToken: String, opportunityId: String): Result<List<LearnModule>> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/${escapeJson(opportunityId)}/learn_progress",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get learn progress failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            val objects = parseJsonArray(json)
            val modules = objects.map { obj ->
                LearnModule(
                    id = extractJsonString(obj, "id") ?: "",
                    name = extractJsonString(obj, "name") ?: "",
                    description = extractJsonString(obj, "description") ?: "",
                    completionStatus = extractJsonString(obj, "completion_status") ?: "not_started"
                )
            }
            Result.success(modules)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get learn progress request failed: ${e.message}", e))
        }
    }

    /**
     * Get delivery progress for an opportunity.
     * GET /api/opportunity/{id}/delivery_progress
     * Authorization: Bearer <access_token>
     */
    fun getDeliveryProgress(accessToken: String, opportunityId: String): Result<DeliveryStatus> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/${escapeJson(opportunityId)}/delivery_progress",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get delivery progress failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            Result.success(
                DeliveryStatus(
                    totalDeliveries = (extractJsonNumber(json, "total_deliveries") ?: 0L).toInt(),
                    completedDeliveries = (extractJsonNumber(json, "completed_deliveries") ?: 0L).toInt(),
                    pendingDeliveries = (extractJsonNumber(json, "pending_deliveries") ?: 0L).toInt(),
                    approvedDeliveries = (extractJsonNumber(json, "approved_deliveries") ?: 0L).toInt()
                )
            )
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get delivery progress request failed: ${e.message}", e))
        }
    }

    /**
     * Get payment history for an opportunity.
     * GET /api/opportunity/{id}/payments (inferred from spec)
     * Authorization: Bearer <access_token>
     */
    fun getPayments(accessToken: String, opportunityId: String): Result<List<PaymentInfo>> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/${escapeJson(opportunityId)}/payments",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get payments failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            val objects = parseJsonArray(json)
            val payments = objects.map { obj ->
                PaymentInfo(
                    id = extractJsonString(obj, "id") ?: "",
                    amount = extractJsonString(obj, "amount") ?: "",
                    currency = extractJsonString(obj, "currency") ?: "",
                    status = extractJsonString(obj, "status") ?: "pending",
                    date = extractJsonString(obj, "date") ?: ""
                )
            }
            Result.success(payments)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get payments request failed: ${e.message}", e))
        }
    }

    /**
     * Confirm a payment.
     * POST /api/payment/confirm
     * Authorization: Bearer <access_token>
     */
    fun confirmPayment(accessToken: String, paymentId: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/api/payment/confirm",
            accessToken,
            body = """{"payment_id":"${escapeJson(paymentId)}"}"""
        )
    }

    /**
     * Update messaging consent for this user.
     * POST /messaging/update_consent/
     * Authorization: Bearer <access_token>
     */
    fun updateConsent(accessToken: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/messaging/update_consent/",
            accessToken,
            body = "{}"
        )
    }

    /**
     * Get all message threads for this user.
     * GET /messaging/retrieve_messages/
     * Authorization: Bearer <access_token>
     */
    fun getMessages(accessToken: String): Result<List<MessageThread>> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/messaging/retrieve_messages/",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get messages failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            val objects = parseJsonArray(json)
            val threads = objects.map { obj ->
                MessageThread(
                    id = extractJsonString(obj, "id") ?: "",
                    participantName = extractJsonString(obj, "participant_name") ?: "",
                    lastMessage = extractJsonString(obj, "last_message") ?: "",
                    lastMessageDate = extractJsonString(obj, "last_message_date") ?: "",
                    unreadCount = (extractJsonNumber(obj, "unread_count") ?: 0L).toInt()
                )
            }
            Result.success(threads)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get messages request failed: ${e.message}", e))
        }
    }

    /**
     * Get all messages in a specific thread.
     * GET /messaging/retrieve_messages/ (with thread filter — endpoint TBD)
     * Authorization: Bearer <access_token>
     */
    fun getThreadMessages(accessToken: String, threadId: String): Result<List<Message>> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/messaging/retrieve_messages/?thread_id=${escapeJson(threadId)}",
                    method = "GET",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    )
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException("Get thread messages failed: $errorBody"))
            }

            val json = response.body?.decodeToString()
                ?: return Result.failure(ConnectMarketplaceException("Empty response"))

            val objects = parseJsonArray(json)
            val messages = objects.map { obj ->
                Message(
                    id = extractJsonString(obj, "id") ?: "",
                    threadId = extractJsonString(obj, "thread_id") ?: threadId,
                    senderName = extractJsonString(obj, "sender_name") ?: "",
                    content = extractJsonString(obj, "content") ?: "",
                    timestamp = extractJsonString(obj, "timestamp") ?: "",
                    isFromMe = extractJsonBoolean(obj, "is_from_me") ?: false
                )
            }
            Result.success(messages)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get thread messages request failed: ${e.message}", e))
        }
    }

    /**
     * Send a message to a thread.
     * POST /messaging/send_message/
     * Authorization: Bearer <access_token>
     */
    fun sendMessage(accessToken: String, threadId: String, content: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/messaging/send_message/",
            accessToken,
            body = """{"thread_id":"${escapeJson(threadId)}","content":"${escapeJson(content)}"}"""
        )
    }

    /**
     * Mark a message as read.
     * POST /messaging/update_received/
     * Authorization: Bearer <access_token>
     */
    fun markAsRead(accessToken: String, messageId: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/messaging/update_received/",
            accessToken,
            body = """{"message_id":"${escapeJson(messageId)}"}"""
        )
    }

    // --- Internal helpers ---

    /**
     * Parse an Opportunity object from a JSON string.
     */
    private fun parseOpportunity(json: String): Opportunity {
        return Opportunity(
            id = extractJsonString(json, "id") ?: "",
            name = extractJsonString(json, "name") ?: "",
            organization = extractJsonString(json, "organization") ?: "",
            description = extractJsonString(json, "description") ?: "",
            shortDescription = extractJsonString(json, "short_description") ?: "",
            isActive = extractJsonBoolean(json, "is_active") ?: false,
            currency = extractJsonString(json, "currency") ?: "",
            maxPayPerVisit = extractJsonString(json, "max_pay_per_visit"),
            totalBudget = extractJsonString(json, "total_budget"),
            endDate = extractJsonString(json, "end_date"),
            learnAppId = extractJsonString(json, "learn_app_id"),
            deliverAppId = extractJsonString(json, "deliver_app_id"),
            claimed = extractJsonBoolean(json, "claimed") ?: false,
            learnProgress = (extractJsonNumber(json, "learn_progress") ?: 0L).toInt(),
            deliveryProgress = (extractJsonNumber(json, "delivery_progress") ?: 0L).toInt()
        )
    }

    /**
     * Parse a JSON array string into a list of individual JSON object strings.
     * Splits on },{  to extract individual objects.
     * Handles the outermost [ ] wrapper.
     */
    private fun parseJsonArray(json: String): List<String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) return emptyList()

        // Strip outer brackets
        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        // Split into individual objects by tracking brace depth
        val objects = mutableListOf<String>()
        var depth = 0
        var start = 0

        for (i in inner.indices) {
            when (inner[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        objects.add(inner.substring(start, i + 1).trim())
                        // Skip past the comma separator
                        start = i + 1
                        while (start < inner.length && (inner[start] == ',' || inner[start].isWhitespace())) {
                            start++
                        }
                    }
                }
            }
        }

        return objects
    }

    /**
     * Execute an authenticated POST using a Bearer token in the Authorization header.
     * Expects a 2xx response with no meaningful body.
     */
    private fun executeAuthenticatedPost(url: String, accessToken: String, body: String?): Result<Unit> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = url,
                    method = "POST",
                    headers = mapOf(
                        "Authorization" to "Bearer $accessToken",
                        "Content-Type" to "application/json"
                    ),
                    body = body?.encodeToByteArray(),
                    contentType = "application/json"
                )
            )

            if (response.code !in 200..299) {
                val errorBody = response.errorBody?.decodeToString()
                    ?: response.body?.decodeToString()
                    ?: "HTTP ${response.code}"
                return Result.failure(ConnectMarketplaceException(errorBody))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Request failed: ${e.message}", e))
        }
    }

    /**
     * Escape a string for safe inclusion in a JSON value.
     * Handles control characters, quotes, backslashes, and newlines.
     */
    private fun escapeJson(value: String): String {
        val sb = StringBuilder()
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u${ch.code.toString(16).padStart(4, '0')}")
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * Extract a string value from a JSON response using simple string operations.
     * Matches pattern: "key": "value" or "key":"value"
     */
    private fun extractJsonString(json: String, key: String): String? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        // Skip whitespace; check for null literal
        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return null
        if (json.startsWith("null", start)) return null

        val openQuote = json.indexOf('"', colonIdx + 1)
        if (openQuote == -1) return null

        var closeQuote = openQuote + 1
        while (closeQuote < json.length) {
            if (json[closeQuote] == '"' && json[closeQuote - 1] != '\\') break
            closeQuote++
        }
        if (closeQuote >= json.length) return null

        return json.substring(openQuote + 1, closeQuote)
    }

    /**
     * Extract a numeric value from a JSON response.
     * Matches pattern: "key": 1234 or "key":1234
     */
    private fun extractJsonNumber(json: String, key: String): Long? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return null

        val sb = StringBuilder()
        if (json[start] == '-') {
            sb.append('-')
            start++
        }
        while (start < json.length && json[start].isDigit()) {
            sb.append(json[start])
            start++
        }

        return sb.toString().toLongOrNull()
    }

    /**
     * Extract a boolean value from a JSON response.
     * Matches pattern: "key": true or "key": false
     */
    private fun extractJsonBoolean(json: String, key: String): Boolean? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return null

        return when {
            json.startsWith("true", start) -> true
            json.startsWith("false", start) -> false
            else -> null
        }
    }
}

class ConnectMarketplaceException(message: String, cause: Throwable? = null) : Exception(message, cause)
