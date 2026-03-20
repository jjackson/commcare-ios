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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.model.Assessment
import org.commcare.app.model.CompletedModule
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Shows the learning progress for a claimed opportunity with completed modules,
 * assessments, and an overall progress indicator.
 *
 * When [onDownloadApp] is provided and the opportunity has a learn app with an
 * install URL, a "Download Learn App" button is shown so the user can install
 * the app via the standard install-progress screen.
 */
@Composable
fun LearnProgressScreen(
    viewModel: OpportunitiesViewModel,
    onDownloadApp: ((installUrl: String, appName: String) -> Unit)? = null
) {
    val detail = viewModel.learnProgress
    val opp = viewModel.selectedOpportunity

    Column(modifier = Modifier.fillMaxSize()) {
        // Download Learn App button — shown when a learn app install URL is available
        val learnApp = opp?.learnApp
        val learnInstallUrl = learnApp?.installUrl
        if (onDownloadApp != null && !learnInstallUrl.isNullOrBlank()) {
            Button(
                onClick = { onDownloadApp(learnInstallUrl, learnApp.name) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Download Learn App")
            }
        }

        if (detail == null) {
            Text(
                text = "No learning data available",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Overall progress header from opportunity's inline learn_progress summary
            val summary = opp?.learnProgress
            if (summary != null && summary.totalModules > 0) {
                val progress = summary.completedModules.toFloat() / summary.totalModules.toFloat()

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
                            text = "${summary.completedModules} / ${summary.totalModules} completed",
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

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Completed modules section
                if (detail.completedModules.isNotEmpty()) {
                    item {
                        Text(
                            text = "Completed Modules",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(detail.completedModules) { module ->
                        CompletedModuleRow(module)
                    }
                }

                // Assessments section
                if (detail.assessments.isNotEmpty()) {
                    item {
                        Text(
                            text = "Assessments",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(detail.assessments) { assessment ->
                        AssessmentRow(assessment)
                    }
                }

                if (detail.completedModules.isEmpty() && detail.assessments.isEmpty()) {
                    item {
                        Text(
                            text = "No learning activity yet",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletedModuleRow(module: CompletedModule) {
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
            Text(
                text = "[x]",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Module #${module.module}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Completed: ${module.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                module.duration?.let {
                    Text(
                        text = "Duration: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AssessmentRow(assessment: Assessment) {
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
            val statusSymbol = if (assessment.passed) "[x]" else "[ ]"
            Text(
                text = statusSymbol,
                style = MaterialTheme.typography.bodyMedium,
                color = if (assessment.passed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Assessment - ${assessment.date}",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Score: ${assessment.score} / ${assessment.passingScore}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (assessment.passed) "Passed" else "Not passed",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (assessment.passed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
