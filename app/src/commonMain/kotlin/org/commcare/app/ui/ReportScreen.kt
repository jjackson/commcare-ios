package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ReportState
import org.commcare.app.viewmodel.ReportViewModel

/**
 * Screen for displaying UCR report data in a table format.
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
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
                text = viewModel.reportTitle.ifBlank { "Report" },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
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

        when (viewModel.reportState) {
            is ReportState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp)
                        .semantics { contentDescription = "Loading" }
                )
            }
            is ReportState.Loaded -> {
                val data = viewModel.reportData
                if (data != null && data.columns.isNotEmpty()) {
                    Text(
                        text = "${data.totalRows} rows",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )

                    // Scrollable table
                    val scrollState = rememberScrollState()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().horizontalScroll(scrollState)
                    ) {
                        // Header row
                        item {
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                for (col in data.columns) {
                                    Text(
                                        text = col,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            }
                            HorizontalDivider()
                        }

                        // Data rows
                        items(data.rows) { row ->
                            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                for ((i, cell) in row.withIndex()) {
                                    if (i < data.columns.size) {
                                        Text(
                                            text = cell,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.width(120.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Text(
                        text = "No data available",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                Text(
                    text = "Select a report to view",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
