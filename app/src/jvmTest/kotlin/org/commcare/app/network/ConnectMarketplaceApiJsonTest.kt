package org.commcare.app.network

import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.HttpResponse
import org.commcare.core.interfaces.PlatformHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for ConnectMarketplaceApi JSON parsing through its public API methods.
 *
 * Uses a mock PlatformHttpClient that returns controlled JSON responses to
 * exercise the private JSON parsing helpers (extractJsonString, extractJsonInt,
 * extractJsonBoolean, extractJsonObjectByKey, extractJsonArrayByKey, splitJsonArray,
 * parseStringMap) inside ConnectMarketplaceApi.
 */
class ConnectMarketplaceApiJsonTest {

    /** A mock HTTP client that returns a pre-configured response. */
    private class MockHttpClient(
        private val responseCode: Int = 200,
        private val responseBody: String? = null,
        private val errorBody: String? = null
    ) : PlatformHttpClient {
        var lastRequest: HttpRequest? = null
            private set

        override fun execute(request: HttpRequest): HttpResponse {
            lastRequest = request
            return HttpResponse(
                code = responseCode,
                headers = emptyMap(),
                body = responseBody?.encodeToByteArray(),
                errorBody = errorBody?.encodeToByteArray()
            )
        }
    }

    // =====================================================================
    // getOpportunities — exercises splitJsonArray + parseOpportunity (complex)
    // =====================================================================

    @Test
    fun testGetOpportunities_emptyArray() {
        val client = MockHttpClient(responseBody = "[]")
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("access-token")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    @Test
    fun testGetOpportunities_singleMinimalOpportunity() {
        val json = """[{"id":42,"opportunity_id":"opp-1","name":"Test Opp","description":"Desc","organization":"Org","is_active":true}]"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("access-token")
        assertTrue(result.isSuccess)
        val opps = result.getOrThrow()
        assertEquals(1, opps.size)
        assertEquals(42, opps[0].id)
        assertEquals("opp-1", opps[0].opportunityId)
        assertEquals("Test Opp", opps[0].name)
        assertEquals("Desc", opps[0].description)
        assertEquals("Org", opps[0].organization)
        assertTrue(opps[0].isActive)
    }

    @Test
    fun testGetOpportunities_multipleOpportunities() {
        val json = """[{"id":1,"opportunity_id":"opp-a","name":"First","description":"A","organization":"Org1","is_active":true},{"id":2,"opportunity_id":"opp-b","name":"Second","description":"B","organization":"Org2","is_active":false}]"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("access-token")
        assertTrue(result.isSuccess)
        val opps = result.getOrThrow()
        assertEquals(2, opps.size)
        assertEquals("First", opps[0].name)
        assertEquals("Second", opps[1].name)
        assertFalse(opps[1].isActive)
    }

    @Test
    fun testGetOpportunities_httpError() {
        val client = MockHttpClient(responseCode = 500, errorBody = "Internal Server Error")
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("access-token")
        assertTrue(result.isFailure)
    }

    // =====================================================================
    // getOpportunityDetail — exercises nested object parsing
    // =====================================================================

    @Test
    fun testGetOpportunityDetail_withNestedObjects() {
        val json = """{
            "id": 10,
            "opportunity_id": "opp-detail",
            "name": "Detailed Opp",
            "description": "Full description here",
            "organization": "Org",
            "is_active": true,
            "learn_app": {
                "id": 5,
                "cc_domain": "test-domain",
                "cc_app_id": "app-123",
                "name": "Learn App",
                "description": "Learning",
                "organization": "Org",
                "learn_modules": [],
                "passing_score": 80
            },
            "deliver_app": {
                "id": 6,
                "cc_domain": "test-domain",
                "cc_app_id": "app-456",
                "name": "Deliver App",
                "description": "Delivering",
                "organization": "Org",
                "learn_modules": [],
                "passing_score": -1
            },
            "max_visits_per_user": 100,
            "budget_per_visit": 50,
            "currency": "USD"
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 10)
        assertTrue(result.isSuccess)
        val opp = result.getOrThrow()
        assertEquals(10, opp.id)
        assertEquals("Detailed Opp", opp.name)
        assertEquals(100, opp.maxVisitsPerUser)
        assertEquals(50, opp.budgetPerVisit)
        assertEquals("USD", opp.currency)

        // Nested learn_app
        assertNotNull(opp.learnApp)
        assertEquals("Learn App", opp.learnApp!!.name)
        assertEquals("test-domain", opp.learnApp!!.ccDomain)
        assertEquals("app-123", opp.learnApp!!.ccAppId)
        assertEquals(80, opp.learnApp!!.passingScore)

        // Nested deliver_app
        assertNotNull(opp.deliverApp)
        assertEquals("Deliver App", opp.deliverApp!!.name)
        assertEquals("app-456", opp.deliverApp!!.ccAppId)
    }

    @Test
    fun testGetOpportunityDetail_withNullNestedObjects() {
        val json = """{
            "id": 11,
            "opportunity_id": "opp-null",
            "name": "No Apps",
            "description": "Opp without apps",
            "organization": "Org",
            "is_active": false,
            "learn_app": null,
            "deliver_app": null,
            "claim": null
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 11)
        assertTrue(result.isSuccess)
        val opp = result.getOrThrow()
        assertNull(opp.learnApp)
        assertNull(opp.deliverApp)
        assertNull(opp.claim)
    }

    @Test
    fun testGetOpportunityDetail_withLearnModules() {
        val json = """{
            "id": 12,
            "opportunity_id": "opp-modules",
            "name": "With Modules",
            "description": "Has modules",
            "organization": "Org",
            "is_active": true,
            "learn_app": {
                "id": 7,
                "cc_domain": "domain",
                "cc_app_id": "app-789",
                "name": "App",
                "description": "Desc",
                "organization": "Org",
                "learn_modules": [
                    {"id": 1, "slug": "mod-1", "name": "Module 1", "description": "First", "time_estimate": 30},
                    {"id": 2, "slug": "mod-2", "name": "Module 2", "description": "Second", "time_estimate": 45}
                ],
                "passing_score": 70
            }
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 12)
        assertTrue(result.isSuccess)
        val modules = result.getOrThrow().learnApp!!.learnModules
        assertEquals(2, modules.size)
        assertEquals("mod-1", modules[0].slug)
        assertEquals("Module 1", modules[0].name)
        assertEquals(30, modules[0].timeEstimate)
        assertEquals("mod-2", modules[1].slug)
        assertEquals(45, modules[1].timeEstimate)
    }

    @Test
    fun testGetOpportunityDetail_withClaim() {
        val json = """{
            "id": 13,
            "opportunity_id": "opp-claimed",
            "name": "Claimed",
            "description": "D",
            "organization": "O",
            "is_active": true,
            "claim": {
                "id": 99,
                "max_payments": 50,
                "end_date": "2026-12-31",
                "date_claimed": "2026-01-15",
                "payment_units": [
                    {"max_visits": 10, "payment_unit": 1, "payment_unit_id": "pu-1"}
                ]
            }
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 13)
        assertTrue(result.isSuccess)
        val claim = result.getOrThrow().claim
        assertNotNull(claim)
        assertEquals(99, claim.id)
        assertEquals(50, claim.maxPayments)
        assertEquals("2026-12-31", claim.endDate)
        assertEquals("2026-01-15", claim.dateClaimed)
        assertEquals(1, claim.paymentUnits.size)
        assertEquals(10, claim.paymentUnits[0].maxVisits)
        assertEquals("pu-1", claim.paymentUnits[0].paymentUnitId)
    }

    // =====================================================================
    // getLearnProgress — exercises extractJsonArrayByKey + nested objects
    // =====================================================================

    @Test
    fun testGetLearnProgress_parsesCompletedModulesAndAssessments() {
        val json = """{
            "completed_modules": [
                {"id": 1, "module": 10, "date": "2026-03-01", "duration": "00:30:00"},
                {"id": 2, "module": 11, "date": "2026-03-02", "duration": null}
            ],
            "assessments": [
                {"id": 100, "date": "2026-03-03", "score": 85, "passing_score": 70, "passed": true}
            ]
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getLearnProgress("access-token", 1)
        assertTrue(result.isSuccess)
        val progress = result.getOrThrow()
        assertEquals(2, progress.completedModules.size)
        assertEquals(10, progress.completedModules[0].module)
        assertEquals("2026-03-01", progress.completedModules[0].date)
        assertEquals("00:30:00", progress.completedModules[0].duration)
        assertNull(progress.completedModules[1].duration)

        assertEquals(1, progress.assessments.size)
        assertEquals(85, progress.assessments[0].score)
        assertEquals(70, progress.assessments[0].passingScore)
        assertTrue(progress.assessments[0].passed)
    }

    @Test
    fun testGetLearnProgress_emptyArrays() {
        val json = """{"completed_modules": [], "assessments": []}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getLearnProgress("access-token", 1)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().completedModules.size)
        assertEquals(0, result.getOrThrow().assessments.size)
    }

    @Test
    fun testGetLearnProgress_missingArrayKeys_defaultsToEmpty() {
        // No completed_modules or assessments keys
        val json = """{}"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getLearnProgress("access-token", 1)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().completedModules.size)
        assertEquals(0, result.getOrThrow().assessments.size)
    }

    // =====================================================================
    // getDeliveryProgress — exercises complex nested parsing with flags map
    // =====================================================================

    @Test
    fun testGetDeliveryProgress_fullResponse() {
        val json = """{
            "deliveries": [
                {
                    "id": 1,
                    "status": "approved",
                    "visit_date": "2026-03-10",
                    "deliver_unit_name": "Visit",
                    "deliver_unit_slug": "visit",
                    "deliver_unit_slug_id": "v1",
                    "entity_id": "ent-1",
                    "entity_name": "Patient A",
                    "reason": null,
                    "flags": {"gps_valid": "true", "photo_valid": "false"},
                    "last_modified": "2026-03-10T12:00:00Z"
                }
            ],
            "payments": [
                {
                    "id": 10,
                    "payment_id": "pay-1",
                    "amount": "50.00",
                    "date_paid": "2026-03-11",
                    "confirmed": true,
                    "confirmation_date": "2026-03-12"
                }
            ],
            "max_payments": 100,
            "payment_accrued": 50,
            "end_date": "2026-12-31"
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getDeliveryProgress("access-token", 1)
        assertTrue(result.isSuccess)
        val progress = result.getOrThrow()

        // Deliveries
        assertEquals(1, progress.deliveries.size)
        val delivery = progress.deliveries[0]
        assertEquals(1, delivery.id)
        assertEquals("approved", delivery.status)
        assertEquals("2026-03-10", delivery.visitDate)
        assertEquals("Patient A", delivery.entityName)
        assertNull(delivery.reason)
        assertEquals("true", delivery.flags["gps_valid"])
        assertEquals("false", delivery.flags["photo_valid"])

        // Payments
        assertEquals(1, progress.payments.size)
        val payment = progress.payments[0]
        assertEquals(10, payment.id)
        assertEquals("pay-1", payment.paymentId)
        assertEquals("50.00", payment.amount)
        assertTrue(payment.confirmed)

        // Top-level fields
        assertEquals(100, progress.maxPayments)
        assertEquals(50, progress.paymentAccrued)
        assertEquals("2026-12-31", progress.endDate)
    }

    @Test
    fun testGetDeliveryProgress_emptyFlags() {
        val json = """{
            "deliveries": [
                {"id": 1, "status": "pending", "flags": {}}
            ],
            "payments": [],
            "max_payments": 10,
            "payment_accrued": 0
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getDeliveryProgress("access-token", 1)
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().deliveries[0].flags.size)
    }

    // =====================================================================
    // getMessages — exercises splitJsonArray for top-level arrays of objects
    // =====================================================================

    @Test
    fun testGetMessages_parsesThreads() {
        val json = """[
            {"id": "t1", "participant_name": "Alice", "last_message": "Hello", "last_message_date": "2026-03-10", "unread_count": 2},
            {"id": "t2", "participant_name": "Bob", "last_message": "Hi there", "last_message_date": "2026-03-09", "unread_count": 0}
        ]"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getMessages("access-token")
        assertTrue(result.isSuccess)
        val threads = result.getOrThrow()
        assertEquals(2, threads.size)
        assertEquals("t1", threads[0].id)
        assertEquals("Alice", threads[0].participantName)
        assertEquals(2, threads[0].unreadCount)
        assertEquals("Bob", threads[1].participantName)
        assertEquals(0, threads[1].unreadCount)
    }

    @Test
    fun testGetMessages_emptyList() {
        val client = MockHttpClient(responseBody = "[]")
        val api = ConnectMarketplaceApi(client)

        val result = api.getMessages("access-token")
        assertTrue(result.isSuccess)
        assertEquals(0, result.getOrThrow().size)
    }

    // =====================================================================
    // getThreadMessages — exercises per-message parsing
    // =====================================================================

    @Test
    fun testGetThreadMessages_parsesMessages() {
        val json = """[
            {"id": "m1", "thread_id": "t1", "sender_name": "Alice", "content": "Hello!", "timestamp": "2026-03-10T10:00:00Z", "is_from_me": false},
            {"id": "m2", "thread_id": "t1", "sender_name": "Me", "content": "Hi Alice", "timestamp": "2026-03-10T10:01:00Z", "is_from_me": true}
        ]"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getThreadMessages("access-token", "t1")
        assertTrue(result.isSuccess)
        val messages = result.getOrThrow()
        assertEquals(2, messages.size)
        assertFalse(messages[0].isFromMe)
        assertEquals("Hello!", messages[0].content)
        assertTrue(messages[1].isFromMe)
        assertEquals("Hi Alice", messages[1].content)
    }

    // =====================================================================
    // JSON edge cases — strings with special characters
    // =====================================================================

    @Test
    fun testOpportunityWithEscapedQuotesInDescription() {
        val json = """[{"id":1,"opportunity_id":"opp-esc","name":"Test","description":"A \"quoted\" description","organization":"Org","is_active":true}]"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("access-token")
        assertTrue(result.isSuccess)
        val opp = result.getOrThrow()[0]
        // The marketplace API's extractJsonString properly skips escaped quotes
        assertEquals("A \\\"quoted\\\" description", opp.description)
    }

    @Test
    fun testOpportunityWithNewlinesInDescription() {
        val json = """[{"id":1,"opportunity_id":"opp-nl","name":"Test","description":"Line1\\nLine2","organization":"Org","is_active":true}]"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunities("access-token")
        assertTrue(result.isSuccess)
        // The raw JSON string "Line1\\nLine2" should be extracted as-is
        assertTrue(result.getOrThrow()[0].description.contains("Line1"))
    }

    @Test
    fun testOpportunityWithPaymentUnits() {
        val json = """{
            "id": 20,
            "opportunity_id": "opp-pu",
            "name": "With Payment Units",
            "description": "D",
            "organization": "O",
            "is_active": true,
            "payment_units": [
                {"id": 1, "payment_unit_id": "pu-abc", "name": "Per Visit", "max_total": 100, "max_daily": 10, "amount": 50, "end_date": "2026-12-31"},
                {"id": 2, "payment_unit_id": "pu-def", "name": "Bonus", "max_total": null, "max_daily": null, "amount": 100, "end_date": null}
            ]
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 20)
        assertTrue(result.isSuccess)
        val units = result.getOrThrow().paymentUnits
        assertEquals(2, units.size)
        assertEquals("Per Visit", units[0].name)
        assertEquals(100, units[0].maxTotal)
        assertEquals(10, units[0].maxDaily)
        assertEquals(50, units[0].amount)
        assertEquals("pu-def", units[1].paymentUnitId)
        assertNull(units[1].maxTotal)
        assertNull(units[1].maxDaily)
    }

    @Test
    fun testOpportunityWithVerificationFlags() {
        val json = """{
            "id": 21,
            "opportunity_id": "opp-vf",
            "name": "VF",
            "description": "D",
            "organization": "O",
            "is_active": true,
            "verification_flags": {
                "form_submission_start": "2026-01-01",
                "form_submission_end": "2026-12-31"
            }
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 21)
        assertTrue(result.isSuccess)
        val vf = result.getOrThrow().verificationFlags
        assertNotNull(vf)
        assertEquals("2026-01-01", vf.formSubmissionStart)
        assertEquals("2026-12-31", vf.formSubmissionEnd)
    }

    @Test
    fun testOpportunityWithCatchmentAreas() {
        val json = """{
            "id": 22,
            "opportunity_id": "opp-ca",
            "name": "CA",
            "description": "D",
            "organization": "O",
            "is_active": true,
            "catchment_areas": [
                {"id": 1, "name": "Zone A", "latitude": "12.345", "longitude": "67.890", "radius": 500, "active": true},
                {"id": 2, "name": "Zone B", "latitude": "-1.23", "longitude": "36.82", "radius": 1000, "active": false}
            ]
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 22)
        assertTrue(result.isSuccess)
        val areas = result.getOrThrow().catchmentAreas
        assertEquals(2, areas.size)
        assertEquals("Zone A", areas[0].name)
        assertEquals("12.345", areas[0].latitude)
        assertEquals(500, areas[0].radius)
        assertTrue(areas[0].active)
        assertFalse(areas[1].active)
    }

    @Test
    fun testOpportunityWithLearnProgress() {
        val json = """{
            "id": 23,
            "opportunity_id": "opp-lp",
            "name": "LP",
            "description": "D",
            "organization": "O",
            "is_active": true,
            "learn_progress": {"total_modules": 5, "completed_modules": 3}
        }"""
        val client = MockHttpClient(responseBody = json)
        val api = ConnectMarketplaceApi(client)

        val result = api.getOpportunityDetail("access-token", 23)
        assertTrue(result.isSuccess)
        val lp = result.getOrThrow().learnProgress
        assertNotNull(lp)
        assertEquals(5, lp.totalModules)
        assertEquals(3, lp.completedModules)
    }

    // =====================================================================
    // Claim + confirm payment
    // =====================================================================

    @Test
    fun testClaimOpportunity_sendsCorrectRequest() {
        val client = MockHttpClient(responseCode = 201, responseBody = "{}")
        val api = ConnectMarketplaceApi(client)

        val result = api.claimOpportunity("access-token", 42)
        assertTrue(result.isSuccess)
        assertTrue(client.lastRequest!!.url.endsWith("/api/opportunity/42/claim"))
        assertEquals("Bearer access-token", client.lastRequest!!.headers["Authorization"])
    }

    @Test
    fun testConfirmPayment_sendsCorrectBody() {
        val client = MockHttpClient(responseCode = 200, responseBody = "{}")
        val api = ConnectMarketplaceApi(client)

        val result = api.confirmPayment("access-token", 99)
        assertTrue(result.isSuccess)
        val body = client.lastRequest!!.body!!.decodeToString()
        assertTrue(body.contains("\"id\":99"))
        assertTrue(body.contains("\"confirmed\":\"true\""))
    }
}
