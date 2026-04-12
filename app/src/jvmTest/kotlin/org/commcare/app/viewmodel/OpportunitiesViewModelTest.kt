package org.commcare.app.viewmodel

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import org.commcare.app.network.ConnectIdApi
import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.app.platform.PlatformKeychainStore
import org.commcare.app.storage.CommCareDatabase
import org.commcare.app.storage.ConnectIdRepository
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
 * Tests for [OpportunitiesViewModel] state management.
 *
 * Uses mock HTTP clients to verify state transitions without
 * network calls. Covers the multi-path state fields identified
 * by the Phase 10 ViewModel survey.
 *
 * Phase 10 Stream 1 — ViewModel test backfill.
 */
class OpportunitiesViewModelTest {

    private class MockMarketplaceClient(
        var responseBody: String = "[]",
        var responseCode: Int = 200
    ) : PlatformHttpClient {
        var requestCount = 0
        override fun execute(request: HttpRequest): HttpResponse {
            requestCount++
            return HttpResponse(
                code = responseCode,
                body = responseBody.encodeToByteArray(),
                headers = emptyMap()
            )
        }
    }

    private class MockTokenClient : PlatformHttpClient {
        override fun execute(request: HttpRequest): HttpResponse {
            return HttpResponse(
                code = 200,
                body = """{"access_token":"test-token","expires_in":3600}""".encodeToByteArray(),
                headers = emptyMap()
            )
        }
    }

    private fun createDb(): CommCareDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CommCareDatabase.Schema.create(driver)
        return CommCareDatabase(driver)
    }

    private fun createTokenManager(db: CommCareDatabase): ConnectIdTokenManager {
        val keychainStore = PlatformKeychainStore()
        keychainStore.store("connect_username", "test-user")
        keychainStore.store("connect_password", "test-pass")
        val connectApi = ConnectIdApi(MockTokenClient())
        val repo = ConnectIdRepository(db)
        return ConnectIdTokenManager(connectApi, repo, keychainStore, db)
    }

    private fun createVm(
        marketplaceClient: MockMarketplaceClient = MockMarketplaceClient()
    ): OpportunitiesViewModel {
        val db = createDb()
        val api = ConnectMarketplaceApi(marketplaceClient)
        val tokenManager = createTokenManager(db)
        return OpportunitiesViewModel(api, tokenManager)
    }

    @Test
    fun initialStateIsEmpty() {
        val vm = createVm()
        assertTrue(vm.opportunities.isEmpty())
        assertNull(vm.selectedOpportunity)
        assertFalse(vm.isLoading)
        assertNull(vm.errorMessage)
    }

    @Test
    fun loadOpportunitiesPopulatesList() {
        val client = MockMarketplaceClient(
            responseBody = """[{
                "id": 1, "opportunity_id": "opp-1", "name": "Demo",
                "description": "Test opp", "short_description": null,
                "organization": "test-org", "learn_app": null, "deliver_app": null,
                "start_date": "2026-01-01", "end_date": "2027-01-01",
                "max_visits_per_user": 100, "daily_max_visits_per_user": 5,
                "budget_per_visit": 10, "total_budget": 1000,
                "claim": null, "learn_progress": null, "deliver_progress": 0,
                "currency": "USD", "is_active": true, "budget_per_user": 100,
                "payment_units": [], "is_user_suspended": false,
                "verification_flags": null, "catchment_areas": []
            }]"""
        )
        val vm = createVm(client)
        vm.loadOpportunities()

        // loadOpportunities runs on a coroutine — but since the mock HTTP
        // client is synchronous, the state should be updated immediately
        // after the coroutine completes.
        // Give a moment for the coroutine to execute.
        Thread.sleep(100)

        assertEquals(1, vm.opportunities.size)
        assertEquals("Demo", vm.opportunities[0].name)
        assertFalse(vm.isLoading)
    }

    @Test
    fun selectOpportunityUpdatesState() {
        val vm = createVm()
        val opp = org.commcare.app.model.Opportunity(
            id = 1, opportunityId = "opp-1", name = "Test",
            description = "", shortDescription = null,
            organization = "org", learnApp = null, deliverApp = null,
            startDate = null, endDate = null,
            maxVisitsPerUser = 0, dailyMaxVisitsPerUser = 0,
            budgetPerVisit = 0, totalBudget = null,
            claim = null, learnProgress = null, deliverProgress = 0,
            currency = null, isActive = true, budgetPerUser = 0,
            paymentUnits = emptyList(), isUserSuspended = false,
            verificationFlags = null, catchmentAreas = emptyList()
        )

        vm.selectOpportunity(opp)
        assertNotNull(vm.selectedOpportunity)
        assertEquals("Test", vm.selectedOpportunity!!.name)
    }

    @Test
    fun clearSelectionResetsState() {
        val vm = createVm()
        val opp = org.commcare.app.model.Opportunity(
            id = 1, opportunityId = "opp-1", name = "Test",
            description = "", shortDescription = null,
            organization = "org", learnApp = null, deliverApp = null,
            startDate = null, endDate = null,
            maxVisitsPerUser = 0, dailyMaxVisitsPerUser = 0,
            budgetPerVisit = 0, totalBudget = null,
            claim = null, learnProgress = null, deliverProgress = 0,
            currency = null, isActive = true, budgetPerUser = 0,
            paymentUnits = emptyList(), isUserSuspended = false,
            verificationFlags = null, catchmentAreas = emptyList()
        )

        vm.selectOpportunity(opp)
        assertNotNull(vm.selectedOpportunity)

        vm.clearSelection()
        assertNull(vm.selectedOpportunity)
    }

    @Test
    fun errorMessageSetsOnFailedLoad() {
        val client = MockMarketplaceClient(responseCode = 500, responseBody = "Server Error")
        val vm = createVm(client)
        vm.loadOpportunities()
        Thread.sleep(100)

        assertNotNull(vm.errorMessage, "error should be set on 500 response")
        assertFalse(vm.isLoading)
    }
}
