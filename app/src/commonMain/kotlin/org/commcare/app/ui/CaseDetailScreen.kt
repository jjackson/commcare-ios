package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.CaseDetailViewModel
import org.commcare.app.viewmodel.CaseItem

@Composable
fun CaseDetailScreen(
    caseItem: CaseItem,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    viewModel: CaseDetailViewModel? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable { onBack() }
                    .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = caseItem.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        // Tabbed detail view (when viewModel with tabs is provided)
        if (viewModel != null && viewModel.tabs.size > 1) {
            ScrollableTabRow(
                selectedTabIndex = viewModel.selectedTabIndex,
                modifier = Modifier.fillMaxWidth()
            ) {
                viewModel.tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = viewModel.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(tab.name) }
                    )
                }
            }

            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                val currentTab = viewModel.tabs.getOrNull(viewModel.selectedTabIndex)
                if (currentTab != null) {
                    for (row in currentTab.rows) {
                        DetailRow(label = row.label, value = row.value)
                    }
                }
            }
        } else {
            // Flat detail view (backward compatible)
            Column(modifier = Modifier.weight(1f).padding(16.dp)) {
                DetailRow(label = "Type", value = caseItem.caseType)
                DetailRow(label = "Case ID", value = caseItem.caseId)
                if (caseItem.dateOpened.isNotBlank()) {
                    DetailRow(label = "Date Opened", value = caseItem.dateOpened)
                }

                Spacer(modifier = Modifier.height(8.dp))

                for ((key, value) in caseItem.properties) {
                    if (key !in setOf("status", "case-id", "case-type", "case-status", "date-opened")) {
                        DetailRow(label = formatLabel(key), value = value)
                    }
                }
            }
        }

        // Action button
        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Text("Select This Case")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f)
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.6f)
        )
    }
}

private fun formatLabel(key: String): String {
    return key.replace("-", " ").replace("_", " ")
        .split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}
