package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.NavigationStep
import org.commcare.app.engine.SessionNavigatorImpl
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.cases.model.Case
import org.javarosa.core.services.storage.IStorageUtilityIndexed

/**
 * Manages case list display — loading, filtering, sorting, selection.
 * Integrates with SessionNavigatorImpl for datum-based filtering and navigation.
 */
class CaseListViewModel(
    private val navigator: SessionNavigatorImpl,
    private val sandbox: SqlDelightUserSandbox
) {
    var cases by mutableStateOf<List<CaseItem>>(emptyList())
        private set
    var searchQuery by mutableStateOf("")
    var selectedCase by mutableStateOf<CaseItem?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var sortMode by mutableStateOf(SortMode.NAME_ASC)
        private set

    private var allCases: List<CaseItem> = emptyList()

    /**
     * Load cases from the sandbox's case storage.
     * Uses the session's needed datum to determine case type filtering.
     */
    fun loadCases() {
        isLoading = true
        errorMessage = null
        try {
            val storage = sandbox.getCaseStorage()
            val datum = navigator.session.getNeededDatum()
            val caseType = datum?.getDataId()

            loadCasesFromStorage(storage, caseType)
        } catch (e: Exception) {
            errorMessage = "Failed to load cases: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    /**
     * Load cases from an explicit storage with optional type filter.
     */
    fun loadCases(storage: IStorageUtilityIndexed<Case>, caseType: String? = null) {
        isLoading = true
        errorMessage = null
        try {
            loadCasesFromStorage(storage, caseType)
        } catch (e: Exception) {
            errorMessage = "Failed to load cases: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    private fun loadCasesFromStorage(storage: IStorageUtilityIndexed<Case>, caseType: String?) {
        val items = mutableListOf<CaseItem>()
        val iter = storage.iterate()
        while (iter.hasMore()) {
            val c = iter.nextRecord()
            val isOpen = !c.isClosed()
            val matchesType = caseType == null || c.getTypeId() == caseType
            if (isOpen && matchesType) {
                items.add(CaseItem(
                    caseId = c.getCaseId() ?: "",
                    name = c.getName() ?: "Unnamed",
                    caseType = c.getTypeId() ?: "",
                    dateOpened = c.getMetaData("date-opened")?.toString() ?: "",
                    properties = buildPropertyMap(c)
                ))
            }
        }
        allCases = items
        applyFilterAndSort()
    }

    fun updateSearch(query: String) {
        searchQuery = query
        applyFilterAndSort()
    }

    fun cycleSortMode() {
        sortMode = when (sortMode) {
            SortMode.NAME_ASC -> SortMode.NAME_DESC
            SortMode.NAME_DESC -> SortMode.DATE_DESC
            SortMode.DATE_DESC -> SortMode.DATE_ASC
            SortMode.DATE_ASC -> SortMode.NAME_ASC
        }
        applyFilterAndSort()
    }

    private fun applyFilterAndSort() {
        val filtered = if (searchQuery.isBlank()) {
            allCases
        } else {
            val q = searchQuery.lowercase()
            allCases.filter { item ->
                item.name.lowercase().contains(q) ||
                    item.properties.values.any { it.lowercase().contains(q) }
            }
        }
        cases = when (sortMode) {
            SortMode.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
            SortMode.NAME_DESC -> filtered.sortedByDescending { it.name.lowercase() }
            SortMode.DATE_ASC -> filtered.sortedBy { it.dateOpened }
            SortMode.DATE_DESC -> filtered.sortedByDescending { it.dateOpened }
        }
    }

    /**
     * Select a case and advance the session state machine.
     * Returns the next navigation step after case selection.
     */
    fun selectCase(item: CaseItem): NavigationStep {
        selectedCase = item
        navigator.selectCase(item.caseId)
        return navigator.getNextStep()
    }

    fun clearSelection() {
        selectedCase = null
    }

    private fun buildPropertyMap(c: Case): Map<String, String> {
        val props = mutableMapOf<String, String>()
        try {
            for (field in c.getMetaDataFields()) {
                val value = c.getMetaData(field)
                if (value != null) {
                    props[field] = value.toString()
                }
            }
        } catch (_: Exception) {
            // Some cases may not implement IMetaData fully
        }
        return props
    }
}

data class CaseItem(
    val caseId: String,
    val name: String,
    val caseType: String,
    val dateOpened: String = "",
    val properties: Map<String, String> = emptyMap()
)

enum class SortMode {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC
}
