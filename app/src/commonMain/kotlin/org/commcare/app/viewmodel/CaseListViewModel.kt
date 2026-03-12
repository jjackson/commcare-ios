package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.cases.model.Case
import org.javarosa.core.services.storage.IStorageUtilityIndexed

/**
 * Manages case list display — loading, filtering, sorting, selection.
 */
class CaseListViewModel {
    var cases by mutableStateOf<List<CaseItem>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var selectedCase by mutableStateOf<CaseItem?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var allCases: List<CaseItem> = emptyList()

    /**
     * Load cases from storage matching the given case type.
     */
    fun loadCases(storage: IStorageUtilityIndexed<Case>, caseType: String? = null) {
        isLoading = true
        errorMessage = null
        try {
            val items = mutableListOf<CaseItem>()
            val iter = storage.iterate()
            while (iter.hasMore()) {
                val c = iter.nextRecord()
                // Filter by case type and open status
                val isOpen = !c.isClosed()
                val matchesType = caseType == null || c.getTypeId() == caseType
                if (isOpen && matchesType) {
                    items.add(CaseItem(
                        caseId = c.getCaseId() ?: "",
                        name = c.getName() ?: "Unnamed",
                        caseType = c.getTypeId() ?: "",
                        properties = buildPropertyMap(c)
                    ))
                }
            }
            allCases = items
            applyFilter()
        } catch (e: Exception) {
            errorMessage = "Failed to load cases: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    fun updateSearch(query: String) {
        searchQuery = query
        applyFilter()
    }

    private fun applyFilter() {
        cases = if (searchQuery.isBlank()) {
            allCases
        } else {
            val q = searchQuery.lowercase()
            allCases.filter { it.name.lowercase().contains(q) }
        }
    }

    fun selectCase(item: CaseItem) {
        selectedCase = item
    }

    fun clearSelection() {
        selectedCase = null
    }

    private fun buildPropertyMap(c: Case): Map<String, String> {
        val props = mutableMapOf<String, String>()
        try {
            for (field in c.getMetaDataFields()) {
                props[field] = c.getMetaData(field).toString()
            }
        } catch (e: Exception) {
            // Some cases may not implement IMetaData fully
        }
        return props
    }
}

data class CaseItem(
    val caseId: String,
    val name: String,
    val caseType: String,
    val properties: Map<String, String> = emptyMap()
)
