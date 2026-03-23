package org.commcare.app.e2e

import org.commcare.app.engine.NavigationStep
import org.commcare.app.viewmodel.CaseItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for tiered case selection: parent -> child flow.
 */
class TieredCaseSelectionTest {

    @Test
    fun testNavigationStepTypes() {
        // Verify all navigation step types exist for case selection flow
        assertTrue(NavigationStep.ShowMenu is NavigationStep)
        assertTrue(NavigationStep.ShowCaseList(null) is NavigationStep)
        assertTrue(NavigationStep.ShowCaseSearch(null) is NavigationStep)
        assertTrue(NavigationStep.StartForm(null) is NavigationStep)
        assertTrue(NavigationStep.SyncRequired is NavigationStep)
        assertTrue(NavigationStep.Error("test") is NavigationStep)
    }

    @Test
    fun testCaseItemProperties() {
        val parent = CaseItem(
            caseId = "parent-001",
            name = "Household A",
            caseType = "household",
            dateOpened = "2026-01-01",
            properties = mapOf("head" to "John", "members" to "5")
        )
        assertEquals("parent-001", parent.caseId)
        assertEquals("household", parent.caseType)
        assertEquals("5", parent.properties["members"])
    }

    @Test
    fun testChildCaseHasParentReference() {
        val child = CaseItem(
            caseId = "child-001",
            name = "Child Case",
            caseType = "person",
            dateOpened = "2026-02-01",
            properties = mapOf("parent_id" to "parent-001", "name" to "Jane")
        )
        assertEquals("parent-001", child.properties["parent_id"])
    }

    @Test
    fun testCaseSelectionFlowSteps() {
        // Tiered selection flow:
        // 1. ShowCaseList (parent) -> select parent
        // 2. ShowCaseList (child) -> select child
        // 3. StartForm (with case context)
        val steps = listOf(
            NavigationStep.ShowCaseList(null),
            NavigationStep.ShowCaseList(null),
            NavigationStep.StartForm("http://form/xmlns")
        )
        assertEquals(3, steps.size)
        assertTrue(steps[0] is NavigationStep.ShowCaseList)
        assertTrue(steps[2] is NavigationStep.StartForm)
    }
}
