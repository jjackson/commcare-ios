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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ActionItem
import org.commcare.app.viewmodel.CaseItem
import org.commcare.app.viewmodel.CaseListViewModel
import org.commcare.app.viewmodel.SortMode

@Composable
fun CaseListScreen(
    viewModel: CaseListViewModel,
    title: String = "Select Case",
    onCaseSelected: (CaseItem) -> Unit,
    onActionSelected: ((ActionItem) -> Unit)? = null,
    onBack: () -> Unit
) {
    // Auto-select: if enabled and exactly one case, select it immediately
    val autoCase = viewModel.getAutoSelectCase()
    LaunchedEffect(autoCase) {
        if (autoCase != null) {
            onCaseSelected(autoCase)
        }
    }

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
                    .semantics { contentDescription = "Go back" }
                    .padding(end = 8.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Action buttons (e.g., "Register New Case")
        if (viewModel.actions.isNotEmpty() && onActionSelected != null) {
            for (action in viewModel.actions) {
                OutlinedButton(
                    onClick = { onActionSelected(action) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                    Text(action.displayText)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Search
        OutlinedTextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.updateSearch(it) },
            label = { Text("Search") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true
        )

        // Sort toggle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${viewModel.cases.size} cases",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = when (viewModel.sortMode) {
                    SortMode.NAME_ASC -> "Name A-Z"
                    SortMode.NAME_DESC -> "Name Z-A"
                    SortMode.DATE_ASC -> "Oldest first"
                    SortMode.DATE_DESC -> "Newest first"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { viewModel.cycleSortMode() }
                    .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                    .semantics { contentDescription = "Change sort order" }
                    .padding(8.dp)
            )
        }

        HorizontalDivider()

        if (viewModel.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp)
                    .semantics { contentDescription = "Loading" }
            )
        } else if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        } else if (viewModel.cases.isEmpty()) {
            Text(
                text = "No cases found",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val tc = viewModel.tileConfig
            if (tc != null) {
                // Tile view
                CaseTileHeader(tc)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.cases) { caseItem ->
                        val fields = viewModel.buildTileFields(caseItem)
                        CaseTileRow(
                            tileConfig = tc,
                            fields = fields,
                            onClick = { onCaseSelected(caseItem) }
                        )
                    }
                }
            } else {
                // Standard list view
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.cases) { caseItem ->
                        CaseItemRow(caseItem = caseItem, onClick = { onCaseSelected(caseItem) })
                    }
                }
            }
        }
    }
}

@Composable
fun CaseItemRow(caseItem: CaseItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .semantics { contentDescription = "Case: ${caseItem.name}, type: ${caseItem.caseType}" },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = caseItem.name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = caseItem.caseType,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
