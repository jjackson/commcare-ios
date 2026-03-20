package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.model.LearnModule
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Shows the learning modules for a claimed opportunity with completion status
 * and an overall progress indicator.
 */
@Composable
fun LearnProgressScreen(viewModel: OpportunitiesViewModel) {
    val modules = viewModel.learnModules

    Column(modifier = Modifier.fillMaxSize()) {
        // Overall progress header
        if (modules.isNotEmpty()) {
            val completedCount = modules.count { it.completionStatus == "completed" }
            val progress = completedCount.toFloat() / modules.size.toFloat()

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Progress",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$completedCount / ${modules.size} completed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }

        if (modules.isEmpty()) {
            Text(
                text = "No learning modules available",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(modules) { module ->
                    LearnModuleRow(module)
                }
            }
        }
    }
}

@Composable
private fun LearnModuleRow(module: LearnModule) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon (text-based, no material-icons dependency)
            val statusSymbol = when (module.completionStatus) {
                "completed" -> "[x]"
                "in_progress" -> "[~]"
                else -> "[ ]"
            }
            Text(
                text = statusSymbol,
                style = MaterialTheme.typography.bodyMedium,
                color = when (module.completionStatus) {
                    "completed" -> MaterialTheme.colorScheme.primary
                    "in_progress" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = module.name,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (module.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
