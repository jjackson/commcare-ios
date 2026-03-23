package org.commcare.app.viewmodel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for CaseDetailViewModel tab grouping and navigation.
 */
class CaseDetailTabsTest {

    @Test
    fun testLoadDetailCreatesTabs() {
        val vm = CaseDetailViewModel()
        val caseItem = CaseItem(
            caseId = "case-001",
            name = "Test Case",
            caseType = "patient",
            dateOpened = "2026-01-15",
            properties = mapOf(
                "age" to "35",
                "gender" to "F",
                "village" to "Kigali"
            )
        )

        vm.loadDetail(caseItem)

        assertTrue(vm.tabs.isNotEmpty(), "Should create at least one tab")
        assertTrue(vm.details.isNotEmpty(), "Should have detail rows")
    }

    @Test
    fun testTabsGroupByTabName() {
        val vm = CaseDetailViewModel()
        val caseItem = CaseItem(
            caseId = "case-002",
            name = "Grouped Case",
            caseType = "household",
            dateOpened = "2026-02-01",
            properties = mapOf(
                "head_of_household" to "John",
                "members" to "5"
            )
        )

        vm.loadDetail(caseItem)

        // Should have "Info" tab (meta fields) and "Details" tab (properties)
        assertEquals(2, vm.tabs.size, "Should create Info and Details tabs")
        assertEquals("Info", vm.tabs[0].name)
        assertEquals("Details", vm.tabs[1].name)
    }

    @Test
    fun testTabSelection() {
        val vm = CaseDetailViewModel()
        val caseItem = CaseItem(
            caseId = "case-003",
            name = "Tab Select",
            caseType = "visit",
            dateOpened = "",
            properties = mapOf("notes" to "test")
        )

        vm.loadDetail(caseItem)

        assertEquals(0, vm.selectedTabIndex)
        vm.selectTab(1)
        assertEquals(1, vm.selectedTabIndex)
    }

    @Test
    fun testSelectTabOutOfBoundsIsIgnored() {
        val vm = CaseDetailViewModel()
        val caseItem = CaseItem(
            caseId = "case-004",
            name = "Bounds Test",
            caseType = "case",
            dateOpened = "",
            properties = emptyMap()
        )

        vm.loadDetail(caseItem)
        vm.selectTab(99)
        assertEquals(0, vm.selectedTabIndex, "Out-of-bounds tab index should be ignored")
    }

    @Test
    fun testInfoTabContainsMetaFields() {
        val vm = CaseDetailViewModel()
        val caseItem = CaseItem(
            caseId = "abc-123",
            name = "Meta Case",
            caseType = "person",
            dateOpened = "2026-03-01",
            properties = mapOf("address" to "123 Main St")
        )

        vm.loadDetail(caseItem)

        val infoTab = vm.tabs.find { it.name == "Info" }
        assertTrue(infoTab != null, "Info tab should exist")
        assertTrue(infoTab.rows.any { it.label == "Case ID" && it.value == "abc-123" })
        assertTrue(infoTab.rows.any { it.label == "Name" && it.value == "Meta Case" })
        assertTrue(infoTab.rows.any { it.label == "Type" && it.value == "person" })
    }

    @Test
    fun testDetailsTabContainsProperties() {
        val vm = CaseDetailViewModel()
        val caseItem = CaseItem(
            caseId = "prop-001",
            name = "Prop Case",
            caseType = "case",
            dateOpened = "",
            properties = mapOf(
                "color" to "blue",
                "size" to "large"
            )
        )

        vm.loadDetail(caseItem)

        val detailsTab = vm.tabs.find { it.name == "Details" }
        assertTrue(detailsTab != null, "Details tab should exist")
        assertTrue(detailsTab.rows.any { it.value == "blue" })
        assertTrue(detailsTab.rows.any { it.value == "large" })
    }
}
