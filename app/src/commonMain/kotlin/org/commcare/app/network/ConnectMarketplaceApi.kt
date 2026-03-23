package org.commcare.app.network

import org.commcare.app.model.Assessment
import org.commcare.app.model.CatchmentArea
import org.commcare.app.model.ClaimPaymentUnit
import org.commcare.app.model.CommCareAppInfo
import org.commcare.app.model.CompletedModule
import org.commcare.app.model.DeliveryProgressDetail
import org.commcare.app.model.DeliveryRecord
import org.commcare.app.model.LearnModuleInfo
import org.commcare.app.model.LearnProgressDetail
import org.commcare.app.model.LearnProgressSummary
import org.commcare.app.model.Message
import org.commcare.app.model.MessageThread
import org.commcare.app.model.Opportunity
import org.commcare.app.model.OpportunityClaim
import org.commcare.app.model.PaymentRecord
import org.commcare.app.model.PaymentUnit
import org.commcare.app.model.VerificationFlags
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.PlatformHttpClient
import org.commcare.core.interfaces.createHttpClient

/**
 * HTTP client for the Connect marketplace server.
 * This is a SEPARATE server from the ConnectID identity server (connectid.dimagi.com).
 * Handles opportunities, payments, and messaging.
 *
 * Uses Bearer token authentication with the ConnectID access token.
 * JSON parsing uses a lightweight recursive-descent approach (no kotlinx.serialization
 * dependency), supporting nested objects and arrays that the server responses contain.
 */
class ConnectMarketplaceApi(
    private val httpClient: PlatformHttpClient = createHttpClient()
) {
    // Base URL — the Connect server (separate from ConnectID identity server)
    var baseUrl: String = "https://connect.dimagi.com"
    // Messaging endpoints live on the ConnectID server, not the marketplace server
    var messagingBaseUrl: String = "https://connectid.dimagi.com"

    private val apiHeaders: (String) -> Map<String, String> = { token ->
        mapOf(
            "Authorization" to "Bearer $token",
            "Content-Type" to "application/json",
            "Accept" to "application/json; version=1.0"
        )
    }

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
                    headers = apiHeaders(accessToken)
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

            val objects = splitJsonArray(json)
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
    fun getOpportunityDetail(accessToken: String, id: Int): Result<Opportunity> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/$id/",
                    method = "GET",
                    headers = apiHeaders(accessToken)
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
     * Expects 201 Created on success.
     */
    fun claimOpportunity(accessToken: String, id: Int): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/api/opportunity/$id/claim",
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
            body = """{"opportunity":"${escapeJson(opportunityId)}"}"""
        )
    }

    /**
     * Get learn progress for an opportunity.
     * GET /api/opportunity/{id}/learn_progress
     * Returns: {completed_modules: [...], assessments: [...]}
     */
    fun getLearnProgress(accessToken: String, opportunityId: Int): Result<LearnProgressDetail> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/$opportunityId/learn_progress",
                    method = "GET",
                    headers = apiHeaders(accessToken)
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

            val completedModulesJson = extractJsonArrayByKey(json, "completed_modules")
            val completedModules = splitJsonArray(completedModulesJson).map { obj ->
                CompletedModule(
                    id = extractJsonInt(obj, "id") ?: 0,
                    module = extractJsonInt(obj, "module") ?: 0,
                    date = extractJsonString(obj, "date") ?: "",
                    duration = extractJsonString(obj, "duration")
                )
            }

            val assessmentsJson = extractJsonArrayByKey(json, "assessments")
            val assessments = splitJsonArray(assessmentsJson).map { obj ->
                Assessment(
                    id = extractJsonInt(obj, "id") ?: 0,
                    date = extractJsonString(obj, "date") ?: "",
                    score = extractJsonInt(obj, "score") ?: 0,
                    passingScore = extractJsonInt(obj, "passing_score") ?: 0,
                    passed = extractJsonBoolean(obj, "passed") ?: false
                )
            }

            Result.success(LearnProgressDetail(completedModules = completedModules, assessments = assessments))
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get learn progress request failed: ${e.message}", e))
        }
    }

    /**
     * Get delivery progress for an opportunity.
     * GET /api/opportunity/{id}/delivery_progress
     * Returns: {deliveries: [...], payments: [...], max_payments: N, payment_accrued: N, end_date: "..."}
     */
    fun getDeliveryProgress(accessToken: String, opportunityId: Int): Result<DeliveryProgressDetail> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$baseUrl/api/opportunity/$opportunityId/delivery_progress",
                    method = "GET",
                    headers = apiHeaders(accessToken)
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

            val deliveriesJson = extractJsonArrayByKey(json, "deliveries")
            val deliveries = splitJsonArray(deliveriesJson).map { obj ->
                DeliveryRecord(
                    id = extractJsonInt(obj, "id") ?: 0,
                    status = extractJsonString(obj, "status") ?: "pending",
                    visitDate = extractJsonString(obj, "visit_date"),
                    deliverUnitName = extractJsonString(obj, "deliver_unit_name"),
                    deliverUnitSlug = extractJsonString(obj, "deliver_unit_slug"),
                    deliverUnitSlugId = extractJsonString(obj, "deliver_unit_slug_id"),
                    entityId = extractJsonString(obj, "entity_id"),
                    entityName = extractJsonString(obj, "entity_name"),
                    reason = extractJsonString(obj, "reason"),
                    flags = parseStringMap(extractJsonObjectByKey(obj, "flags")),
                    lastModified = extractJsonString(obj, "last_modified")
                )
            }

            val paymentsJson = extractJsonArrayByKey(json, "payments")
            val payments = splitJsonArray(paymentsJson).map { obj ->
                parsePaymentRecord(obj)
            }

            Result.success(
                DeliveryProgressDetail(
                    deliveries = deliveries,
                    payments = payments,
                    maxPayments = extractJsonInt(json, "max_payments") ?: -1,
                    paymentAccrued = extractJsonInt(json, "payment_accrued") ?: 0,
                    endDate = extractJsonString(json, "end_date")
                )
            )
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get delivery progress request failed: ${e.message}", e))
        }
    }

    /**
     * Confirm one or more payments.
     * POST /api/payment/confirm
     * Body: {"payments": [{"id": N, "confirmed": "true"}]}
     * Note: "confirmed" is sent as a string, not a boolean.
     */
    fun confirmPayment(accessToken: String, paymentId: Int): Result<Unit> {
        return executeAuthenticatedPost(
            "$baseUrl/api/payment/confirm",
            accessToken,
            body = """{"payments":[{"id":$paymentId,"confirmed":"true"}]}"""
        )
    }

    /**
     * Update messaging consent for this user.
     * POST /messaging/update_consent/
     * Authorization: Bearer <access_token>
     */
    fun updateConsent(accessToken: String): Result<Unit> {
        return executeAuthenticatedPost(
            "$messagingBaseUrl/messaging/update_consent/",
            accessToken,
            body = "{}"
        )
    }

    /**
     * Update consent for a specific messaging channel.
     * POST /messaging/update_channel_consent/
     */
    fun updateChannelConsent(accessToken: String, channelId: String, consented: Boolean): Result<Unit> {
        return executeAuthenticatedPost(
            "$messagingBaseUrl/messaging/update_channel_consent/",
            accessToken,
            body = """{"channel_id": "$channelId", "consented": $consented}"""
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
                    url = "$messagingBaseUrl/messaging/retrieve_messages/",
                    method = "GET",
                    headers = apiHeaders(accessToken)
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

            val objects = splitJsonArray(json)
            val threads = objects.map { obj ->
                MessageThread(
                    id = extractJsonString(obj, "id") ?: "",
                    participantName = extractJsonString(obj, "participant_name") ?: "",
                    lastMessage = extractJsonString(obj, "last_message") ?: "",
                    lastMessageDate = extractJsonString(obj, "last_message_date") ?: "",
                    unreadCount = extractJsonInt(obj, "unread_count") ?: 0
                )
            }
            Result.success(threads)
        } catch (e: Exception) {
            Result.failure(ConnectMarketplaceException("Get messages request failed: ${e.message}", e))
        }
    }

    /**
     * Get all messages in a specific thread.
     * GET /messaging/retrieve_messages/ (with thread filter)
     * Authorization: Bearer <access_token>
     */
    fun getThreadMessages(accessToken: String, threadId: String): Result<List<Message>> {
        return try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$messagingBaseUrl/messaging/retrieve_messages/?thread_id=${escapeJson(threadId)}",
                    method = "GET",
                    headers = apiHeaders(accessToken)
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

            val objects = splitJsonArray(json)
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
            "$messagingBaseUrl/messaging/send_message/",
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
            "$messagingBaseUrl/messaging/update_received/",
            accessToken,
            body = """{"message_id":"${escapeJson(messageId)}"}"""
        )
    }

    // ========================================================================
    // Opportunity parsing
    // ========================================================================

    private fun parseOpportunity(json: String): Opportunity {
        // Parse nested learn_app object
        val learnAppJson = extractJsonObjectByKey(json, "learn_app")
        val learnApp = if (learnAppJson.isNotEmpty()) parseCommCareAppInfo(learnAppJson) else null

        // Parse nested deliver_app object
        val deliverAppJson = extractJsonObjectByKey(json, "deliver_app")
        val deliverApp = if (deliverAppJson.isNotEmpty()) parseCommCareAppInfo(deliverAppJson) else null

        // Parse nested claim object
        val claimJson = extractJsonObjectByKey(json, "claim")
        val claim = if (claimJson.isNotEmpty()) parseClaim(claimJson) else null

        // Parse nested learn_progress object (summary, inline on opportunity)
        val learnProgressJson = extractJsonObjectByKey(json, "learn_progress")
        val learnProgress = if (learnProgressJson.isNotEmpty()) {
            LearnProgressSummary(
                totalModules = extractJsonInt(learnProgressJson, "total_modules") ?: 0,
                completedModules = extractJsonInt(learnProgressJson, "completed_modules") ?: 0
            )
        } else null

        // Parse payment_units array
        val paymentUnitsJson = extractJsonArrayByKey(json, "payment_units")
        val paymentUnits = splitJsonArray(paymentUnitsJson).map { parsePaymentUnit(it) }

        // Parse verification_flags object
        val vfJson = extractJsonObjectByKey(json, "verification_flags")
        val verificationFlags = if (vfJson.isNotEmpty()) {
            VerificationFlags(
                formSubmissionStart = extractJsonString(vfJson, "form_submission_start"),
                formSubmissionEnd = extractJsonString(vfJson, "form_submission_end")
            )
        } else null

        // Parse catchment_areas array
        val catchmentJson = extractJsonArrayByKey(json, "catchment_areas")
        val catchmentAreas = splitJsonArray(catchmentJson).map { obj ->
            CatchmentArea(
                id = extractJsonInt(obj, "id") ?: 0,
                name = extractJsonString(obj, "name") ?: "",
                latitude = extractJsonString(obj, "latitude") ?: "0",
                longitude = extractJsonString(obj, "longitude") ?: "0",
                radius = extractJsonInt(obj, "radius") ?: 0,
                active = extractJsonBoolean(obj, "active") ?: true
            )
        }

        // Parse inline learnings (completed_modules) array if present on opportunity detail
        val learningsJson = extractJsonArrayByKey(json, "completed_modules")
        val learnings = splitJsonArray(learningsJson).map { obj ->
            CompletedModule(
                id = extractJsonInt(obj, "id") ?: 0,
                module = extractJsonInt(obj, "module") ?: 0,
                date = extractJsonString(obj, "date") ?: "",
                duration = extractJsonString(obj, "duration")
            )
        }

        // Parse inline assessments array if present on opportunity detail
        val inlineAssessmentsJson = extractJsonArrayByKey(json, "assessments")
        val inlineAssessments = splitJsonArray(inlineAssessmentsJson).map { obj ->
            Assessment(
                id = extractJsonInt(obj, "id") ?: 0,
                date = extractJsonString(obj, "date") ?: "",
                score = extractJsonInt(obj, "score") ?: 0,
                passingScore = extractJsonInt(obj, "passing_score") ?: 0,
                passed = extractJsonBoolean(obj, "passed") ?: false
            )
        }

        // Parse inline deliveries array if present on opportunity detail
        val inlineDeliveriesJson = extractJsonArrayByKey(json, "deliveries")
        val inlineDeliveries = splitJsonArray(inlineDeliveriesJson).map { obj ->
            DeliveryRecord(
                id = extractJsonInt(obj, "id") ?: 0,
                status = extractJsonString(obj, "status") ?: "pending",
                visitDate = extractJsonString(obj, "visit_date"),
                deliverUnitName = extractJsonString(obj, "deliver_unit_name"),
                deliverUnitSlug = extractJsonString(obj, "deliver_unit_slug"),
                deliverUnitSlugId = extractJsonString(obj, "deliver_unit_slug_id"),
                entityId = extractJsonString(obj, "entity_id"),
                entityName = extractJsonString(obj, "entity_name"),
                reason = extractJsonString(obj, "reason"),
                flags = parseStringMap(extractJsonObjectByKey(obj, "flags")),
                lastModified = extractJsonString(obj, "last_modified")
            )
        }

        // Parse inline payments array if present on opportunity detail
        val inlinePaymentsJson = extractJsonArrayByKey(json, "payments")
        val inlinePayments = splitJsonArray(inlinePaymentsJson).map { obj ->
            parsePaymentRecord(obj)
        }

        return Opportunity(
            id = extractJsonInt(json, "id") ?: 0,
            opportunityId = extractJsonString(json, "opportunity_id") ?: "",
            name = extractJsonString(json, "name") ?: "",
            description = extractJsonString(json, "description") ?: "",
            shortDescription = extractJsonString(json, "short_description"),
            organization = extractJsonString(json, "organization") ?: "",
            learnApp = learnApp,
            deliverApp = deliverApp,
            startDate = extractJsonString(json, "start_date"),
            endDate = extractJsonString(json, "end_date"),
            maxVisitsPerUser = extractJsonInt(json, "max_visits_per_user") ?: -1,
            dailyMaxVisitsPerUser = extractJsonInt(json, "daily_max_visits_per_user") ?: -1,
            budgetPerVisit = extractJsonInt(json, "budget_per_visit") ?: -1,
            totalBudget = extractJsonNumber(json, "total_budget"),
            claim = claim,
            learnProgress = learnProgress,
            deliverProgress = extractJsonInt(json, "deliver_progress") ?: 0,
            currency = extractJsonString(json, "currency"),
            isActive = extractJsonBoolean(json, "is_active") ?: false,
            budgetPerUser = extractJsonInt(json, "budget_per_user") ?: 0,
            paymentUnits = paymentUnits,
            isUserSuspended = extractJsonBoolean(json, "is_user_suspended") ?: false,
            verificationFlags = verificationFlags,
            catchmentAreas = catchmentAreas,
            learnings = learnings,
            assessments = inlineAssessments,
            deliveries = inlineDeliveries,
            payments = inlinePayments,
            paymentAccrued = extractJsonInt(json, "payment_accrued") ?: 0,
            dailyStartTime = extractJsonString(json, "daily_start_time"),
            dailyFinishTime = extractJsonString(json, "daily_finish_time")
        )
    }

    private fun parseCommCareAppInfo(json: String): CommCareAppInfo {
        val modulesJson = extractJsonArrayByKey(json, "learn_modules")
        val modules = splitJsonArray(modulesJson).map { obj ->
            LearnModuleInfo(
                id = extractJsonInt(obj, "id") ?: 0,
                slug = extractJsonString(obj, "slug") ?: "",
                name = extractJsonString(obj, "name") ?: "",
                description = extractJsonString(obj, "description") ?: "",
                timeEstimate = extractJsonInt(obj, "time_estimate") ?: 0,
                completed = extractJsonBoolean(obj, "completed") ?: false
            )
        }

        return CommCareAppInfo(
            id = extractJsonInt(json, "id") ?: 0,
            ccDomain = extractJsonString(json, "cc_domain") ?: "",
            ccAppId = extractJsonString(json, "cc_app_id") ?: "",
            name = extractJsonString(json, "name") ?: "",
            description = extractJsonString(json, "description") ?: "",
            organization = extractJsonString(json, "organization") ?: "",
            learnModules = modules,
            passingScore = extractJsonInt(json, "passing_score") ?: -1,
            installUrl = extractJsonString(json, "install_url")
        )
    }

    private fun parseClaim(json: String): OpportunityClaim {
        val claimUnitsJson = extractJsonArrayByKey(json, "payment_units")
        val claimUnits = splitJsonArray(claimUnitsJson).map { obj ->
            ClaimPaymentUnit(
                maxVisits = extractJsonInt(obj, "max_visits") ?: 0,
                paymentUnit = extractJsonInt(obj, "payment_unit") ?: 0,
                paymentUnitId = extractJsonString(obj, "payment_unit_id") ?: ""
            )
        }

        return OpportunityClaim(
            id = extractJsonInt(json, "id") ?: 0,
            maxPayments = extractJsonInt(json, "max_payments") ?: -1,
            endDate = extractJsonString(json, "end_date"),
            dateClaimed = extractJsonString(json, "date_claimed"),
            paymentUnits = claimUnits
        )
    }

    private fun parsePaymentUnit(json: String): PaymentUnit {
        return PaymentUnit(
            id = extractJsonInt(json, "id") ?: 0,
            paymentUnitId = extractJsonString(json, "payment_unit_id") ?: "",
            name = extractJsonString(json, "name") ?: "",
            maxTotal = extractJsonIntOrNull(json, "max_total"),
            maxDaily = extractJsonIntOrNull(json, "max_daily"),
            amount = extractJsonInt(json, "amount") ?: 0,
            endDate = extractJsonString(json, "end_date")
        )
    }

    private fun parsePaymentRecord(json: String): PaymentRecord {
        return PaymentRecord(
            id = extractJsonInt(json, "id") ?: 0,
            paymentId = extractJsonString(json, "payment_id") ?: "",
            amount = extractJsonString(json, "amount") ?: "0",
            datePaid = extractJsonString(json, "date_paid"),
            confirmed = extractJsonBoolean(json, "confirmed") ?: false,
            confirmationDate = extractJsonString(json, "confirmation_date")
        )
    }

    // ========================================================================
    // HTTP helpers
    // ========================================================================

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
                    headers = apiHeaders(accessToken),
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

    // ========================================================================
    // JSON parsing helpers — lightweight recursive-descent for nested structures
    // ========================================================================

    /**
     * Escape a string for safe inclusion in a JSON value.
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
     * Extract a nested JSON object by key name. Returns the object substring
     * including braces, or empty string if not found or if the value is null.
     *
     * Handles: "key": {...} by tracking brace depth, and skips over strings
     * so that braces inside string values don't confuse the parser.
     */
    private fun extractJsonObjectByKey(json: String, key: String): String {
        val searchKey = "\"$key\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIdx = json.indexOf(searchKey, searchFrom)
            if (keyIdx == -1) return ""

            val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
            if (colonIdx == -1) return ""

            // Make sure nothing but whitespace between key end and colon
            val between = json.substring(keyIdx + searchKey.length, colonIdx).trim()
            if (between.isNotEmpty()) {
                searchFrom = keyIdx + searchKey.length
                continue
            }

            // Skip whitespace after colon
            var start = colonIdx + 1
            while (start < json.length && json[start].isWhitespace()) start++
            if (start >= json.length) return ""

            // Check for null literal
            if (json.startsWith("null", start)) return ""

            if (json[start] != '{') {
                searchFrom = start
                continue
            }

            // Track braces and skip over strings
            var depth = 0
            var i = start
            var inString = false
            while (i < json.length) {
                val c = json[i]
                if (inString) {
                    if (c == '\\') {
                        i += 2; continue
                    }
                    if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '{' -> depth++
                        '}' -> {
                            depth--
                            if (depth == 0) return json.substring(start, i + 1)
                        }
                    }
                }
                i++
            }
            return ""
        }
        return ""
    }

    /**
     * Extract a JSON array by key name. Returns the array substring
     * including brackets, or "[]" if not found.
     */
    private fun extractJsonArrayByKey(json: String, key: String): String {
        val searchKey = "\"$key\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIdx = json.indexOf(searchKey, searchFrom)
            if (keyIdx == -1) return "[]"

            val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
            if (colonIdx == -1) return "[]"

            val between = json.substring(keyIdx + searchKey.length, colonIdx).trim()
            if (between.isNotEmpty()) {
                searchFrom = keyIdx + searchKey.length
                continue
            }

            var start = colonIdx + 1
            while (start < json.length && json[start].isWhitespace()) start++
            if (start >= json.length) return "[]"

            if (json.startsWith("null", start)) return "[]"

            if (json[start] != '[') {
                searchFrom = start
                continue
            }

            // Track brackets and skip over strings
            var depth = 0
            var i = start
            var inString = false
            while (i < json.length) {
                val c = json[i]
                if (inString) {
                    if (c == '\\') {
                        i += 2; continue
                    }
                    if (c == '"') inString = false
                } else {
                    when (c) {
                        '"' -> inString = true
                        '[' -> depth++
                        ']' -> {
                            depth--
                            if (depth == 0) return json.substring(start, i + 1)
                        }
                    }
                }
                i++
            }
            return "[]"
        }
        return "[]"
    }

    /**
     * Split a JSON array string into individual element strings.
     * Handles nested objects/arrays and strings correctly by tracking depth.
     */
    private fun splitJsonArray(json: String): List<String> {
        val trimmed = json.trim()
        if (!trimmed.startsWith("[")) return emptyList()

        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyList()

        val objects = mutableListOf<String>()
        var depth = 0
        var start = 0
        var inString = false

        for (i in inner.indices) {
            val c = inner[i]
            if (inString) {
                if (c == '\\' && i + 1 < inner.length) {
                    // skip escaped character (handled by not changing state)
                    continue
                }
                // But we need to handle the backslash check properly:
                // Check if previous char was backslash
                if (c == '"' && (i == 0 || inner[i - 1] != '\\')) {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                    ',' -> {
                        if (depth == 0) {
                            objects.add(inner.substring(start, i).trim())
                            start = i + 1
                        }
                    }
                }
            }
        }

        // Add the last element
        val last = inner.substring(start).trim()
        if (last.isNotEmpty()) {
            objects.add(last)
        }

        return objects
    }

    /**
     * Parse a flat JSON object into a string-to-string map.
     * Used for the flags field on DeliveryRecord.
     */
    private fun parseStringMap(json: String): Map<String, String> {
        if (json.isEmpty()) return emptyMap()
        val trimmed = json.trim()
        if (!trimmed.startsWith("{")) return emptyMap()

        val inner = trimmed.substring(1, trimmed.length - 1).trim()
        if (inner.isEmpty()) return emptyMap()

        val result = mutableMapOf<String, String>()
        // Split on commas at depth 0
        val pairs = mutableListOf<String>()
        var depth = 0
        var start = 0
        var inStr = false

        for (i in inner.indices) {
            val c = inner[i]
            if (inStr) {
                if (c == '"' && (i == 0 || inner[i - 1] != '\\')) inStr = false
            } else {
                when (c) {
                    '"' -> inStr = true
                    '{', '[' -> depth++
                    '}', ']' -> depth--
                    ',' -> if (depth == 0) {
                        pairs.add(inner.substring(start, i).trim())
                        start = i + 1
                    }
                }
            }
        }
        val last = inner.substring(start).trim()
        if (last.isNotEmpty()) pairs.add(last)

        for (pair in pairs) {
            // Each pair is "key": "value"
            val colonIdx = pair.indexOf(':')
            if (colonIdx == -1) continue
            val k = pair.substring(0, colonIdx).trim().removeSurrounding("\"")
            val v = pair.substring(colonIdx + 1).trim().removeSurrounding("\"")
            result[k] = v
        }

        return result
    }

    /**
     * Extract a string value from a JSON string.
     * Matches pattern: "key": "value" or "key":null
     */
    private fun extractJsonString(json: String, key: String): String? {
        val searchKey = "\"$key\""
        var searchFrom = 0
        while (searchFrom < json.length) {
            val keyIdx = json.indexOf(searchKey, searchFrom)
            if (keyIdx == -1) return null

            val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
            if (colonIdx == -1) return null

            // Verify nothing but whitespace between key-close-quote and colon
            val between = json.substring(keyIdx + searchKey.length, colonIdx).trim()
            if (between.isNotEmpty()) {
                searchFrom = keyIdx + searchKey.length
                continue
            }

            // Skip whitespace after colon
            var start = colonIdx + 1
            while (start < json.length && json[start].isWhitespace()) start++
            if (start >= json.length) return null

            // Check for null
            if (json.startsWith("null", start)) return null

            // Must be a string value
            if (json[start] != '"') {
                searchFrom = start
                continue
            }

            val openQuote = start
            var closeQuote = openQuote + 1
            while (closeQuote < json.length) {
                if (json[closeQuote] == '\\') {
                    closeQuote += 2
                    continue
                }
                if (json[closeQuote] == '"') break
                closeQuote++
            }
            if (closeQuote >= json.length) return null

            return json.substring(openQuote + 1, closeQuote)
        }
        return null
    }

    /**
     * Extract a numeric value from a JSON string. Returns Long?.
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

        // Check for null
        if (json.startsWith("null", start)) return null

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
     * Extract an integer value. Convenience wrapper.
     */
    private fun extractJsonInt(json: String, key: String): Int? {
        return extractJsonNumber(json, key)?.toInt()
    }

    /**
     * Extract an integer value, returning null if the JSON value is null.
     */
    private fun extractJsonIntOrNull(json: String, key: String): Int? {
        val searchKey = "\"$key\""
        val keyIdx = json.indexOf(searchKey)
        if (keyIdx == -1) return null

        val colonIdx = json.indexOf(':', keyIdx + searchKey.length)
        if (colonIdx == -1) return null

        var start = colonIdx + 1
        while (start < json.length && json[start].isWhitespace()) start++
        if (start >= json.length) return null

        if (json.startsWith("null", start)) return null

        return extractJsonNumber(json, key)?.toInt()
    }

    /**
     * Extract a boolean value from a JSON string.
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
