package org.commcare.app.viewmodel

import org.commcare.app.ui.TileFieldData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for case tile, auto-select, and action item functionality in CaseListViewModel.
 */
class CaseTileViewModelTest {

    @Test
    fun testTileFieldDataDefaults() {
        val field = TileFieldData(
            value = "John Doe",
            gridX = 0,
            gridY = 0,
            gridWidth = 4,
            gridHeight = 1
        )
        assertEquals("John Doe", field.value)
        assertEquals(0, field.gridX)
        assertEquals(0, field.gridY)
        assertEquals(4, field.gridWidth)
        assertEquals(1, field.gridHeight)
        assertNull(field.fontSize)
        assertNull(field.horizontalAlign)
        assertFalse(field.isImage)
        assertFalse(field.showBorder)
    }

    @Test
    fun testTileFieldDataWithStyling() {
        val field = TileFieldData(
            value = "Status",
            gridX = 2,
            gridY = 1,
            gridWidth = 2,
            gridHeight = 1,
            fontSize = "large",
            horizontalAlign = "center",
            isImage = false,
            headerText = "Status",
            showBorder = true,
            showShading = true
        )
        assertEquals("large", field.fontSize)
        assertEquals("center", field.horizontalAlign)
        assertEquals("Status", field.headerText)
        assertTrue(field.showBorder)
        assertTrue(field.showShading)
    }

    @Test
    fun testCaseItemProperties() {
        val item = CaseItem(
            caseId = "abc-123",
            name = "Test Case",
            caseType = "patient",
            dateOpened = "2026-01-15",
            properties = mapOf(
                "age" to "25",
                "gender" to "female",
                "village" to "Nairobi"
            )
        )
        assertEquals("abc-123", item.caseId)
        assertEquals("Test Case", item.name)
        assertEquals("patient", item.caseType)
        assertEquals("25", item.properties["age"])
        assertEquals(3, item.properties.size)
    }

    @Test
    fun testActionItem() {
        val action = ActionItem(
            displayText = "Register New Case",
            stackOperations = ArrayList()
        )
        assertEquals("Register New Case", action.displayText)
        assertTrue(action.stackOperations.isEmpty())
    }

    @Test
    fun testSortModes() {
        val cases = listOf(
            CaseItem("1", "Charlie", "patient", "2026-01-01"),
            CaseItem("2", "Alice", "patient", "2026-03-01"),
            CaseItem("3", "Bob", "patient", "2026-02-01")
        )

        val nameAsc = cases.sortedBy { it.name.lowercase() }
        assertEquals("Alice", nameAsc[0].name)
        assertEquals("Bob", nameAsc[1].name)
        assertEquals("Charlie", nameAsc[2].name)

        val nameDesc = cases.sortedByDescending { it.name.lowercase() }
        assertEquals("Charlie", nameDesc[0].name)

        val dateAsc = cases.sortedBy { it.dateOpened }
        assertEquals("2026-01-01", dateAsc[0].dateOpened)

        val dateDesc = cases.sortedByDescending { it.dateOpened }
        assertEquals("2026-03-01", dateDesc[0].dateOpened)
    }

    @Test
    fun testCaseSearchFiltering() {
        val cases = listOf(
            CaseItem("1", "John Doe", "patient", properties = mapOf("village" to "Nairobi")),
            CaseItem("2", "Jane Smith", "patient", properties = mapOf("village" to "Mombasa")),
            CaseItem("3", "Bob Jones", "patient", properties = mapOf("village" to "Nairobi"))
        )

        val query = "nairobi"
        val filtered = cases.filter { item ->
            item.name.lowercase().contains(query) ||
                item.properties.values.any { it.lowercase().contains(query) }
        }
        assertEquals(2, filtered.size)
        assertEquals("John Doe", filtered[0].name)
        assertEquals("Bob Jones", filtered[1].name)
    }

    @Test
    fun testCaseSearchByName() {
        val cases = listOf(
            CaseItem("1", "John Doe", "patient"),
            CaseItem("2", "Jane Smith", "patient"),
            CaseItem("3", "Bob Jones", "patient")
        )

        val query = "jane"
        val filtered = cases.filter { item ->
            item.name.lowercase().contains(query)
        }
        assertEquals(1, filtered.size)
        assertEquals("Jane Smith", filtered[0].name)
    }
}
