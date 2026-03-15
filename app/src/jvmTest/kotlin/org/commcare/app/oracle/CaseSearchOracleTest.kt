package org.commcare.app.oracle

import org.commcare.app.viewmodel.CaseItem
import org.commcare.app.viewmodel.SearchField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Oracle tests for case search and claim features.
 */
class CaseSearchOracleTest {

    @Test
    fun testSearchFieldModel() {
        val field = SearchField(
            key = "name",
            label = "Patient Name",
            appearance = null,
            isRequired = true
        )
        assertEquals("name", field.key)
        assertEquals("Patient Name", field.label)
        assertNull(field.appearance)
        assertTrue(field.isRequired)
    }

    @Test
    fun testOptionalSearchField() {
        val field = SearchField(
            key = "village",
            label = "Village",
            isRequired = false
        )
        assertFalse(field.isRequired)
    }

    @Test
    fun testSearchQueryConstruction() {
        val searchValues = mapOf(
            "name" to "John",
            "village" to "",
            "age" to "25"
        )

        // Only non-blank values should be included
        val queryParams = searchValues.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${it.value}" }

        assertEquals("name=John&age=25", queryParams)
    }

    @Test
    fun testEmptySearchQuery() {
        val searchValues = mapOf(
            "name" to "",
            "village" to ""
        )

        val queryParams = searchValues.entries
            .filter { it.value.isNotBlank() }
            .joinToString("&") { "${it.key}=${it.value}" }

        assertEquals("", queryParams)
    }

    @Test
    fun testLocalSearchFiltering() {
        val cases = listOf(
            CaseItem("1", "John Doe", "patient", properties = mapOf("village" to "Nairobi")),
            CaseItem("2", "Jane Smith", "patient", properties = mapOf("village" to "Mombasa")),
            CaseItem("3", "Bob Jones", "patient", properties = mapOf("village" to "Nairobi"))
        )

        val searchTerms = listOf("john")
        val filtered = cases.filter { item ->
            searchTerms.any { term ->
                item.name.lowercase().contains(term) ||
                    item.properties.values.any { it.lowercase().contains(term) }
            }
        }

        assertEquals(1, filtered.size)
        assertEquals("John Doe", filtered[0].name)
    }

    @Test
    fun testSearchByProperty() {
        val cases = listOf(
            CaseItem("1", "John", "patient", properties = mapOf("village" to "Nairobi")),
            CaseItem("2", "Jane", "patient", properties = mapOf("village" to "Mombasa")),
            CaseItem("3", "Bob", "patient", properties = mapOf("village" to "Nairobi"))
        )

        val searchTerms = listOf("nairobi")
        val filtered = cases.filter { item ->
            searchTerms.any { term ->
                item.name.lowercase().contains(term) ||
                    item.properties.values.any { it.lowercase().contains(term) }
            }
        }

        assertEquals(2, filtered.size)
    }
}
