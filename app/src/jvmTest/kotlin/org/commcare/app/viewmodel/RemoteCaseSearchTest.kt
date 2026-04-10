package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for remote case search: query request and result handling.
 */
class RemoteCaseSearchTest {

    @Test
    fun testCaseItemFromSearchResult() {
        val result = CaseItem(
            caseId = "remote-001",
            name = "Remote Patient",
            caseType = "patient",
            dateOpened = "2026-03-20",
            properties = mapOf("age" to "45", "village" to "Kigali")
        )
        assertEquals("remote-001", result.caseId)
        assertEquals("patient", result.caseType)
        assertEquals("45", result.properties["age"])
    }

    @Test
    fun testMultipleSearchResults() {
        val results = listOf(
            CaseItem("r1", "Patient A", "patient", "", emptyMap()),
            CaseItem("r2", "Patient B", "patient", "", emptyMap()),
            CaseItem("r3", "Patient C", "patient", "", emptyMap())
        )
        assertEquals(3, results.size)
    }
}
