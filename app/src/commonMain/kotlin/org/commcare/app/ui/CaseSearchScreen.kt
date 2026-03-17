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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.CaseItem
import org.commcare.app.viewmodel.CaseSearchViewModel

/**
 * Screen for remote case search. Renders search fields from QueryPrompt configuration,
 * allows user to input search terms, and displays results.
 */
@Composable
fun CaseSearchScreen(
    viewModel: CaseSearchViewModel,
    onResultSelected: (CaseItem) -> Unit,
    onBack: () -> Unit
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
                text = viewModel.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        // Search fields
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            for (field in viewModel.searchFields) {
                OutlinedTextField(
                    value = viewModel.searchValues[field.key] ?: "",
                    onValueChange = { viewModel.updateSearchValue(field.key, it) },
                    label = {
                        Text(field.label + if (field.isRequired) " *" else "")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Button(
                onClick = { viewModel.executeSearch() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isSearching
            ) {
                Text(if (viewModel.isSearching) "Searching..." else "Search")
            }
        }

        HorizontalDivider()

        // Results
        if (viewModel.isSearching) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp)
            )
        } else if (viewModel.results.isEmpty()) {
            Text(
                text = if (viewModel.searchFields.isNotEmpty()) "Enter search criteria above" else "No results",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "${viewModel.results.size} results",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.results) { caseItem ->
                    CaseItemRow(caseItem = caseItem, onClick = { onResultSelected(caseItem) })
                }
            }
        }
    }
}
