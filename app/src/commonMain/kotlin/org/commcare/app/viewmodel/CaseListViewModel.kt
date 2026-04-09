package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.commcare.app.engine.NavigationStep
import org.commcare.app.engine.SessionNavigatorImpl
import org.commcare.app.storage.SqlDelightUserSandbox
import org.commcare.app.ui.TileConfig
import org.commcare.app.ui.TileFieldData
import org.commcare.cases.model.Case
import org.commcare.suite.model.Detail
import org.commcare.suite.model.DetailField
import org.commcare.suite.model.EntityDatum
import org.commcare.suite.model.Text
import org.javarosa.core.services.storage.IStorageUtilityIndexed

/**
 * Manages case list display — loading, filtering, sorting, selection.
 * Integrates with SessionNavigatorImpl for datum-based filtering and navigation.
 * Supports tile-based display when Detail configuration has grid fields.
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

    /** Tile configuration loaded from suite Detail, null if list view */
    var tileConfig by mutableStateOf<TileConfig?>(null)
        private set

    /** Detail actions (e.g., "Register New Case") */
    var actions by mutableStateOf<List<ActionItem>>(emptyList())
        private set

    /** Whether auto-select is enabled for this case list */
    var autoSelectEnabled by mutableStateOf(false)
        private set

    private var allCases: List<CaseItem> = emptyList()
    private var detail: Detail? = null
    private var entityDatum: EntityDatum? = null

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

            // Load detail configuration if available
            if (datum is EntityDatum) {
                entityDatum = datum
                autoSelectEnabled = datum.isAutoSelectEnabled()
                loadDetailConfig(datum)
            }

            // Extract the case type from the nodeset xpath predicates. The
            // nodeset for an EntityDatum looks like
            //   instance('casedb')/casedb/case[@case_type='household'][@status='open']
            // We regex-extract the @case_type='...' literal. Previously this
            // code used datum.getDataId() which returns the variable name
            // ("case_id"), not the case type — causing the filter to match
            // nothing and leaving real case lists empty.
            val caseType = (datum as? EntityDatum)
                ?.getNodeset()
                ?.toString(true)
                ?.let { nodesetStr ->
                    CASE_TYPE_PREDICATE_REGEX.find(nodesetStr)?.groupValues?.get(1)
                }

            loadCasesFromStorage(storage, caseType)
        } catch (e: Exception) {
            errorMessage = "Failed to load cases: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    companion object {
        /** Matches `@case_type = 'household'` / `@case_type='household'` predicates. */
        private val CASE_TYPE_PREDICATE_REGEX =
            Regex("@case_type\\s*=\\s*'([^']*)'")
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

    private fun loadDetailConfig(datum: EntityDatum) {
        try {
            val shortDetailId = datum.getShortDetail() ?: return
            val d = navigator.platform.getDetail(shortDetailId) ?: return
            detail = d

            // Check if this detail uses tile layout
            if (d.usesEntityTileView()) {
                val maxWH = d.getMaxWidthHeight()
                val tileFields = d.fields.filter { it.isCaseTileField() }.map { field ->
                    TileFieldData(
                        value = "", // populated per-case in buildTileFields
                        gridX = field.gridX,
                        gridY = field.gridY,
                        gridWidth = field.gridWidth,
                        gridHeight = field.gridHeight,
                        fontSize = field.fontSize,
                        horizontalAlign = field.horizontalAlign,
                        verticalAlign = field.verticalAlign,
                        isImage = field.templateForm == "image",
                        headerText = evaluateHeader(field),
                        showBorder = field.showBorder,
                        showShading = field.showShading
                    )
                }
                tileConfig = TileConfig(
                    fields = tileFields,
                    maxWidth = maxWH.first,
                    maxHeight = maxWH.second,
                    numPerRow = d.numEntitiesToDisplayPerRow
                )
            }

            // Load actions
            loadActions(d)
        } catch (_: Exception) {
            // Detail config is optional — fall back to list view
        }
    }

    private fun loadActions(d: Detail) {
        try {
            val ec = navigator.session.getEvaluationContext()
            val relevantActions = d.getCustomActions(ec)
            actions = relevantActions.map { action ->
                val displayText = try {
                    action.display?.text?.evaluate() ?: "Action"
                } catch (_: Exception) {
                    "Action"
                }
                ActionItem(
                    displayText = displayText,
                    stackOperations = action.stackOperations ?: ArrayList()
                )
            }
        } catch (_: Exception) {
            actions = emptyList()
        }
    }

    private fun evaluateHeader(field: DetailField): String? {
        return try {
            field.getHeader()?.evaluate()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Build tile field data for a specific case item by evaluating detail field templates.
     */
    fun buildTileFields(caseItem: CaseItem): List<TileFieldData> {
        val d = detail ?: return emptyList()
        val fields = d.fields
        val result = mutableListOf<TileFieldData>()

        for (field in fields) {
            if (!field.isCaseTileField()) continue

            val value = evaluateFieldForCase(field, caseItem)
            result.add(TileFieldData(
                value = value,
                gridX = field.gridX,
                gridY = field.gridY,
                gridWidth = field.gridWidth,
                gridHeight = field.gridHeight,
                fontSize = field.fontSize,
                horizontalAlign = field.horizontalAlign,
                verticalAlign = field.verticalAlign,
                isImage = field.templateForm == "image",
                headerText = evaluateHeader(field),
                showBorder = field.showBorder,
                showShading = field.showShading
            ))
        }
        return result
    }

    /**
     * Evaluate a detail field template for a given case.
     * Falls back to property lookup if XPath evaluation isn't available.
     */
    private fun evaluateFieldForCase(field: DetailField, caseItem: CaseItem): String {
        val template = field.getTemplate()
        if (template is Text) {
            // Try to evaluate with the session's evaluation context
            try {
                val ec = navigator.session.getEvaluationContext()
                return template.evaluate(ec)
            } catch (_: Exception) {
                // Fall through to property-based lookup
            }

            // Fallback: try to extract the xpath argument as a property name
            try {
                val arg = template.getArgument()
                if (arg != null) {
                    // Common pattern: argument is like "name" or "case_name"
                    val propValue = caseItem.properties[arg]
                    if (propValue != null) return propValue
                }
            } catch (_: Exception) {
                // ignore
            }
        }

        // Final fallback: use case name for first field
        return ""
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
                    dateOpened = c.getDateOpened()?.toString() ?: "",
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

    /**
     * Check if auto-select should trigger (exactly one case in the list).
     * Returns the single case if auto-select conditions are met, null otherwise.
     */
    fun getAutoSelectCase(): CaseItem? {
        if (!autoSelectEnabled) return null
        return if (cases.size == 1) cases[0] else null
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

data class ActionItem(
    val displayText: String,
    val stackOperations: ArrayList<org.commcare.suite.model.StackOperation>
)

enum class SortMode {
    NAME_ASC, NAME_DESC, DATE_ASC, DATE_DESC
}
