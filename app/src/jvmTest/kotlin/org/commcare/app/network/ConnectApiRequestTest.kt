package org.commcare.app.network

import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Connect API request formatting — URL patterns, HTTP methods, headers,
 * and request bodies. Uses a RecordingHttpClient mock that captures the HttpRequest
 * for inspection and returns a configurable HttpResponse.
 *
 * Complements ConnectMarketplaceApiJsonTest (which focuses on response parsing)
 * by verifying that outgoing requests are correctly formed.
 */
class ConnectApiRequestTest {

    /**
     * A mock HTTP client that records every request and returns a pre-configured response.
     * Supports returning different responses per call via the responseQueue.
     */
    private class RecordingHttpClient(
        private val defaultCode: Int = 200,
        private val defaultBody: String? = "{}",
        private val defaultErrorBody: String? = null
    ) : PlatformHttpClient {
        val requests = mutableListOf<HttpRequest>()

        /** Optional queue: if non-empty, the next execute() pops from here instead of using defaults. */
        private val responseQueue = mutableListOf<HttpResponse>()

        fun enqueueResponse(code: Int, body: String? = null, errorBody: String? = null) {
            responseQueue.add(
                HttpResponse(
                    code = code,
                    headers = emptyMap(),
                    body = body?.encodeToByteArray(),
                    errorBody = errorBody?.encodeToByteArray()
                )
            )
        }

        override fun execute(request: HttpRequest): HttpResponse {
            requests.add(request)
            if (responseQueue.isNotEmpty()) {
                return responseQueue.removeAt(0)
            }
            return HttpResponse(
                code = defaultCode,
                headers = emptyMap(),
                body = defaultBody?.encodeToByteArray(),
                errorBody = defaultErrorBody?.encodeToByteArray()
            )
        }

        val lastRequest: HttpRequest? get() = requests.lastOrNull()
    }

    // =====================================================================
    // 1. getOpportunities — URL, method, Authorization header
    // =====================================================================

    @Test
    fun testGetOpportunities_requestFormat() {
        val client = RecordingHttpClient(defaultBody = "[]")
        val api = ConnectMarketplaceApi(client)

        api.getOpportunities("test-access-token-123")

        val req = client.lastRequest
        assertNotNull(req, "Expected a request to be recorded")
        assertTrue(req.url.contains("/api/opportunity/"), "URL should contain /api/opportunity/")
        assertEquals("GET", req.method, "getOpportunities should use GET")
        assertEquals("Bearer test-access-token-123", req.headers["Authorization"],
            "Authorization header should be Bearer <token>")
    }

    @Test
    fun testGetOpportunities_acceptHeader() {
        val client = RecordingHttpClient(defaultBody = "[]")
        val api = ConnectMarketplaceApi(client)

        api.getOpportunities("tok")

        val req = client.lastRequest!!
        assertEquals("application/json; version=1.0", req.headers["Accept"],
            "Accept header should include version")
    }

    // =====================================================================
    // 2. claimOpportunity — URL with id, method is POST
    // =====================================================================

    @Test
    fun testClaimOpportunity_requestFormat() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        api.claimOpportunity("my-token", 42)

        val req = client.lastRequest
        assertNotNull(req)
        assertTrue(req.url.contains("/api/opportunity/42/claim"),
            "URL should contain /api/opportunity/42/claim, got: ${req.url}")
        assertEquals("POST", req.method, "claimOpportunity should use POST")
        assertEquals("Bearer my-token", req.headers["Authorization"])
    }

    // =====================================================================
    // 3. getMessages — URL, method is GET
    // =====================================================================

    @Test
    fun testGetMessages_requestFormat() {
        val client = RecordingHttpClient(defaultBody = "[]")
        val api = ConnectMarketplaceApi(client)

        api.getMessages("msg-token")

        val req = client.lastRequest
        assertNotNull(req)
        assertTrue(req.url.contains("/messaging/retrieve_messages/"),
            "URL should contain /messaging/retrieve_messages/, got: ${req.url}")
        assertEquals("GET", req.method, "getMessages should use GET")
        assertEquals("Bearer msg-token", req.headers["Authorization"])
    }

    @Test
    fun testGetMessages_usesMessagingBaseUrl() {
        val client = RecordingHttpClient(defaultBody = "[]")
        val api = ConnectMarketplaceApi(client)
        // messagingBaseUrl defaults to connectid.dimagi.com
        api.getMessages("tok")

        val req = client.lastRequest!!
        assertTrue(req.url.startsWith("https://connectid.dimagi.com"),
            "Messages should go to messagingBaseUrl (connectid), got: ${req.url}")
    }

    // =====================================================================
    // 4. sendMessage — URL, method is POST, body contains thread_id and content
    // =====================================================================

    @Test
    fun testSendMessage_requestFormat() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        api.sendMessage("send-tok", "thread-abc", "Hello world")

        val req = client.lastRequest
        assertNotNull(req)
        assertTrue(req.url.contains("/messaging/send_message/"),
            "URL should contain /messaging/send_message/, got: ${req.url}")
        assertEquals("POST", req.method, "sendMessage should use POST")

        val body = req.body?.decodeToString() ?: ""
        assertTrue(body.contains("\"thread_id\""), "Body should contain thread_id key")
        assertTrue(body.contains("thread-abc"), "Body should contain the thread ID value")
        assertTrue(body.contains("\"content\""), "Body should contain content key")
        assertTrue(body.contains("Hello world"), "Body should contain the message content")
    }

    // =====================================================================
    // 5. updateConsent — URL, method is POST
    // =====================================================================

    @Test
    fun testUpdateConsent_requestFormat() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        api.updateConsent("consent-tok")

        val req = client.lastRequest
        assertNotNull(req)
        assertTrue(req.url.contains("/messaging/update_consent/"),
            "URL should contain /messaging/update_consent/, got: ${req.url}")
        assertEquals("POST", req.method, "updateConsent should use POST")
        assertEquals("Bearer consent-tok", req.headers["Authorization"])
    }

    // =====================================================================
    // 6. updateChannelConsent — body uses escapeJson on channelId
    // =====================================================================

    @Test
    fun testUpdateChannelConsent_escapesSpecialChars() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        // Channel ID with special chars that need JSON escaping
        api.updateChannelConsent("tok", "chan\"with\\special", true)

        val req = client.lastRequest
        assertNotNull(req)
        assertTrue(req.url.contains("/messaging/update_channel_consent/"),
            "URL should contain /messaging/update_channel_consent/, got: ${req.url}")

        val body = req.body?.decodeToString() ?: ""
        // The raw quote and backslash should be escaped in JSON
        assertFalse(body.contains("chan\"with"), "Raw double-quote should be escaped, not literal")
        assertTrue(body.contains("chan\\\"with"), "Double-quote should be escaped as \\\"")
        assertTrue(body.contains("\\\\special"), "Backslash should be escaped as \\\\")
        assertTrue(body.contains("\"consented\": true") || body.contains("\"consented\":true") ||
            body.contains("\"consented\": true"),
            "Body should contain consented boolean")
    }

    @Test
    fun testUpdateChannelConsent_normalChannelId() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        api.updateChannelConsent("tok", "channel-123", false)

        val body = client.lastRequest?.body?.decodeToString() ?: ""
        assertTrue(body.contains("channel-123"), "Body should contain the channel ID")
        assertTrue(body.contains("false"), "Body should contain consented=false")
    }

    // =====================================================================
    // 7. Opportunity JSON parsing — parse a canned response and verify fields
    // =====================================================================

    @Test
    fun testOpportunityParsing_fullFields() {
        val json = """[{
            "id": 99,
            "opportunity_id": "uuid-opp-99",
            "name": "Health Survey",
            "description": "A health survey opportunity",
            "short_description": "Health survey",
            "organization": "dimagi",
            "is_active": true,
            "max_visits_per_user": 50,
            "daily_max_visits_per_user": 5,
            "budget_per_visit": 100,
            "budget_per_user": 5000,
            "currency": "INR",
            "start_date": "2026-01-01",
            "end_date": "2026-12-31",
            "deliver_progress": 10,
            "is_user_suspended": false,
            "payment_accrued": 1000,
            "daily_start_time": "08:00:00",
            "daily_finish_time": "17:00:00"
        }]"""

        val client = RecordingHttpClient(defaultBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("tok")
        assertTrue(result.isSuccess)
        val opps = result.getOrThrow()
        assertEquals(1, opps.size)

        val opp = opps[0]
        assertEquals(99, opp.id)
        assertEquals("uuid-opp-99", opp.opportunityId)
        assertEquals("Health Survey", opp.name)
        assertEquals("A health survey opportunity", opp.description)
        assertEquals("Health survey", opp.shortDescription)
        assertEquals("dimagi", opp.organization)
        assertTrue(opp.isActive)
        assertEquals(50, opp.maxVisitsPerUser)
        assertEquals(5, opp.dailyMaxVisitsPerUser)
        assertEquals(100, opp.budgetPerVisit)
        assertEquals(5000, opp.budgetPerUser)
        assertEquals("INR", opp.currency)
        assertEquals("2026-01-01", opp.startDate)
        assertEquals("2026-12-31", opp.endDate)
        assertEquals(10, opp.deliverProgress)
        assertFalse(opp.isUserSuspended)
        assertEquals(1000, opp.paymentAccrued)
        assertEquals("08:00:00", opp.dailyStartTime)
        assertEquals("17:00:00", opp.dailyFinishTime)
    }

    // =====================================================================
    // 8. MessageThread JSON parsing — parse a canned thread list response
    // =====================================================================

    @Test
    fun testMessageThreadParsing() {
        val json = """[
            {
                "id": "thread-001",
                "participant_name": "Alice",
                "last_message": "See you tomorrow",
                "last_message_date": "2026-03-24T10:30:00Z",
                "unread_count": 3
            },
            {
                "id": "thread-002",
                "participant_name": "Bob",
                "last_message": "Thanks!",
                "last_message_date": "2026-03-23T08:15:00Z",
                "unread_count": 0
            }
        ]"""

        val client = RecordingHttpClient(defaultBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getMessages("tok")
        assertTrue(result.isSuccess)
        val threads = result.getOrThrow()
        assertEquals(2, threads.size)

        assertEquals("thread-001", threads[0].id)
        assertEquals("Alice", threads[0].participantName)
        assertEquals("See you tomorrow", threads[0].lastMessage)
        assertEquals("2026-03-24T10:30:00Z", threads[0].lastMessageDate)
        assertEquals(3, threads[0].unreadCount)

        assertEquals("thread-002", threads[1].id)
        assertEquals("Bob", threads[1].participantName)
        assertEquals("Thanks!", threads[1].lastMessage)
        assertEquals(0, threads[1].unreadCount)
    }

    // =====================================================================
    // 9. Error response (500) — verify Result.isFailure
    // =====================================================================

    @Test
    fun testServerError500_returnsFailure() {
        val client = RecordingHttpClient(
            defaultCode = 500,
            defaultBody = null,
            defaultErrorBody = "Internal Server Error"
        )
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("tok")
        assertTrue(result.isFailure, "500 response should result in failure")

        val exception = result.exceptionOrNull()
        assertNotNull(exception, "Failure should have an exception")
        assertTrue(exception.message?.contains("Internal Server Error") == true ||
            exception.message?.contains("500") == true,
            "Error message should reference the server error, got: ${exception.message}")
    }

    @Test
    fun testServerError500_claimOpportunity() {
        val client = RecordingHttpClient(
            defaultCode = 500,
            defaultBody = null,
            defaultErrorBody = "Server down"
        )
        val api = ConnectMarketplaceApi(client)

        val result = api.claimOpportunity("tok", 1)
        assertTrue(result.isFailure, "500 on claim should result in failure")
    }

    // =====================================================================
    // 10. Auth expired (401) — verify Result.isFailure
    // =====================================================================

    @Test
    fun testAuthExpired401_returnsFailure() {
        val client = RecordingHttpClient(
            defaultCode = 401,
            defaultBody = null,
            defaultErrorBody = """{"detail":"Token expired"}"""
        )
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("expired-token")
        assertTrue(result.isFailure, "401 response should result in failure")
    }

    @Test
    fun testAuthExpired401_sendMessage() {
        val client = RecordingHttpClient(
            defaultCode = 401,
            defaultBody = null,
            defaultErrorBody = """{"detail":"Authentication credentials were not provided."}"""
        )
        val api = ConnectMarketplaceApi(client)

        val result = api.sendMessage("bad-tok", "thread-1", "Hello")
        assertTrue(result.isFailure, "401 on sendMessage should result in failure")
    }

    // =====================================================================
    // Bonus: additional request format tests
    // =====================================================================

    @Test
    fun testGetOpportunityDetail_urlContainsId() {
        val json = """{"id":7,"opportunity_id":"opp-7","name":"Test","description":"D","organization":"Org","is_active":true}"""
        val client = RecordingHttpClient(defaultBody = json)
        val api = ConnectMarketplaceApi(client)

        api.getOpportunityDetail("tok", 7)

        val req = client.lastRequest!!
        assertTrue(req.url.contains("/api/opportunity/7/"),
            "URL should contain /api/opportunity/7/, got: ${req.url}")
        assertEquals("GET", req.method)
    }

    @Test
    fun testConfirmPayment_requestFormat() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        api.confirmPayment("pay-tok", 555)

        val req = client.lastRequest!!
        assertTrue(req.url.contains("/api/payment/confirm"),
            "URL should contain /api/payment/confirm, got: ${req.url}")
        assertEquals("POST", req.method)

        val body = req.body?.decodeToString() ?: ""
        assertTrue(body.contains("555"), "Body should contain payment ID")
        assertTrue(body.contains("\"confirmed\""), "Body should contain confirmed key")
    }

    @Test
    fun testMarkAsRead_requestFormat() {
        val client = RecordingHttpClient()
        val api = ConnectMarketplaceApi(client)

        api.markAsRead("tok", "msg-42")

        val req = client.lastRequest!!
        assertTrue(req.url.contains("/messaging/update_received/"),
            "URL should contain /messaging/update_received/, got: ${req.url}")
        assertEquals("POST", req.method)

        val body = req.body?.decodeToString() ?: ""
        assertTrue(body.contains("msg-42"), "Body should contain message ID")
    }

    @Test
    fun testCustomBaseUrl_isRespected() {
        val client = RecordingHttpClient(defaultBody = "[]")
        val api = ConnectMarketplaceApi(client)
        api.baseUrl = "https://staging.connect.dimagi.com"

        api.getOpportunities("tok")

        val req = client.lastRequest!!
        assertTrue(req.url.startsWith("https://staging.connect.dimagi.com"),
            "Should use custom baseUrl, got: ${req.url}")
    }
}
