package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.RecoveryViewModel
import org.commcare.app.viewmodel.UnsentForm

/**
 * Recovery mode screen — manage unsent forms, view logs, clear data.
 */
@Composable
fun RecoveryScreen(
    viewModel: RecoveryViewModel,
    onBack: () -> Unit,
    onClearData: () -> Unit
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
                    .semantics { contentDescription = "Go back" }
                    .padding(end = 8.dp)
            )
            Text(
                text = "Recovery Mode",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        HorizontalDivider()

        if (viewModel.actionResult != null) {
            Text(
                text = viewModel.actionResult!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Unsent Forms section
            item {
                Text("Unsent Forms", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (viewModel.unsentForms.isEmpty()) {
                item {
                    Text(
                        "No unsent forms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(viewModel.unsentForms) { form ->
                    UnsentFormCard(
                        form = form,
                        onRetry = { viewModel.forceSubmitForm(form.id) },
                        onDelete = { viewModel.deleteForm(form.id) },
                        onExport = { viewModel.exportFormXml(form.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Actions section
            item {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Actions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                OutlinedButton(
                    onClick = { viewModel.loadLogs() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("View Logs")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                Button(
                    onClick = {
                        viewModel.clearUserData()
                        onClearData()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Clear User Data")
                }
            }
        }
    }
}

@Composable
private fun UnsentFormCard(
    form: UnsentForm,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(form.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                form.timestamp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                ) { Text("Retry") }
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                ) { Text("Export") }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
