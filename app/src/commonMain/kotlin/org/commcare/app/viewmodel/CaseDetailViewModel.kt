package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Displays case detail properties. Reads detail configuration
 * from the CaseItem's property map.
 */
class CaseDetailViewModel {
    var details by mutableStateOf<List<DetailRow>>(emptyList())
        private set
    var caseName by mutableStateOf("")
        private set
    var tabs by mutableStateOf<List<DetailTab>>(emptyList())
        private set
    var selectedTabIndex by mutableStateOf(0)
        private set

    fun loadDetail(caseItem: CaseItem) {
        caseName = caseItem.name
        details = buildDetailRows(caseItem)
        tabs = buildTabs(details)
    }

    fun selectTab(index: Int) {
        if (index in tabs.indices) {
            selectedTabIndex = index
        }
    }

    private fun buildDetailRows(caseItem: CaseItem): List<DetailRow> {
        val rows = mutableListOf<DetailRow>()
        rows.add(DetailRow("Case ID", caseItem.caseId, tab = "Info"))
        rows.add(DetailRow("Name", caseItem.name, tab = "Info"))
        rows.add(DetailRow("Type", caseItem.caseType, tab = "Info"))
        if (caseItem.dateOpened.isNotBlank()) {
            rows.add(DetailRow("Date Opened", caseItem.dateOpened, tab = "Info"))
        }
        for ((key, value) in caseItem.properties) {
            if (key in setOf("case-id", "case-type", "case-status", "date-opened")) continue
            rows.add(DetailRow(formatLabel(key), value, tab = "Details"))
        }
        return rows
    }

    private fun buildTabs(rows: List<DetailRow>): List<DetailTab> {
        return rows.groupBy { it.tab }
            .map { (tabName, tabRows) -> DetailTab(tabName, tabRows) }
    }

    private fun formatLabel(key: String): String {
        return key.replace("-", " ").replace("_", " ")
            .split(" ").joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}

data class DetailRow(
    val label: String,
    val value: String,
    val tab: String = "Details"
)

data class DetailTab(
    val name: String,
    val rows: List<DetailRow>
)
