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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.DiagnosticResult
import org.commcare.app.viewmodel.DiagnosticStatus
import org.commcare.app.viewmodel.DiagnosticsViewModel

/**
 * Screen showing connection diagnostic results — server ping, auth check, sync status.
 */
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    lastSyncTime: String?,
    pendingFormCount: Int,
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
                    .semantics { contentDescription = "Go back" }
                    .padding(end = 8.dp)
            )
            Text(
                text = "Connection Diagnostics",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Button(
                onClick = { viewModel.runDiagnostics(lastSyncTime, pendingFormCount) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !viewModel.isRunning
            ) {
                Text(if (viewModel.isRunning) "Running..." else "Run Diagnostics")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (viewModel.isRunning && viewModel.results.isEmpty()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                        .semantics { contentDescription = "Loading" }
                )
            }

            LazyColumn {
                items(viewModel.results) { result ->
                    DiagnosticResultCard(result)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun DiagnosticResultCard(result: DiagnosticResult) {
    val statusColor = when (result.status) {
        DiagnosticStatus.OK -> MaterialTheme.colorScheme.primary
        DiagnosticStatus.Warning -> MaterialTheme.colorScheme.tertiary
        DiagnosticStatus.Error -> MaterialTheme.colorScheme.error
    }
    val statusLabel = when (result.status) {
        DiagnosticStatus.OK -> "PASS"
        DiagnosticStatus.Warning -> "WARN"
        DiagnosticStatus.Error -> "FAIL"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.name,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = result.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}
