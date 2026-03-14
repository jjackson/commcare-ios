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

    fun loadDetail(caseItem: CaseItem) {
        caseName = caseItem.name
        details = buildDetailRows(caseItem)
    }

    private fun buildDetailRows(caseItem: CaseItem): List<DetailRow> {
        val rows = mutableListOf<DetailRow>()
        rows.add(DetailRow("Case ID", caseItem.caseId))
        rows.add(DetailRow("Name", caseItem.name))
        rows.add(DetailRow("Type", caseItem.caseType))
        if (caseItem.dateOpened.isNotBlank()) {
            rows.add(DetailRow("Date Opened", caseItem.dateOpened))
        }
        for ((key, value) in caseItem.properties) {
            // Skip meta fields already shown
            if (key in setOf("case-id", "case-type", "case-status", "date-opened")) continue
            rows.add(DetailRow(formatLabel(key), value))
        }
        return rows
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
    val value: String
)
