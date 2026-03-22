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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.Assessment
import org.commcare.app.model.CompletedModule
import org.commcare.app.model.LearnModuleInfo
import org.commcare.app.viewmodel.DownloadState
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
        // "Start Learning" button — shown when a learn app install URL is available
        // and learning is not yet complete
        val learnApp = opp?.learnApp
        val learnInstallUrl = learnApp?.installUrl
        val learningComplete = opp != null && viewModel.isLearningComplete(opp)
        if (onDownloadApp != null && learnApp != null && !learnInstallUrl.isNullOrBlank() && !learningComplete) {
            val downloadState = viewModel.downloadState
            val isDownloading = downloadState is DownloadState.Downloading &&
                downloadState.appName == learnApp.name
            Button(
                onClick = {
                    viewModel.downloadAndInstallApp(learnApp) { success ->
                        if (success) {
                            onDownloadApp(learnInstallUrl, learnApp.name)
                        }
                    }
                },
                enabled = !isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(if (isDownloading) "Downloading..." else "Start Learning")
            }
            if (downloadState is DownloadState.Error) {
                Text(
                    text = downloadState.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
        // Show completion message when learning is done
        if (learningComplete && learnApp != null) {
            Text(
                text = "Learning complete!",
                style = MaterialTheme.typography.bodyMedium,
                color = ConnectIndigo,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
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
                // Learn module list from the learn app (shows all modules with completion status)
                val learnModules = opp?.learnApp?.learnModules ?: emptyList()
                if (learnModules.isNotEmpty()) {
                    item {
                        Text(
                            text = "Learning Modules",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(learnModules) { mod ->
                        LearnModuleRow(mod)
                    }
                }

                // Completed modules section (from learn_progress endpoint)
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
private fun LearnModuleRow(module: LearnModuleInfo) {
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
                text = if (module.completed) "[x]" else "[ ]",
                style = MaterialTheme.typography.bodyMedium,
                color = if (module.completed) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
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
                if (module.timeEstimate > 0) {
                    Text(
                        text = "~${module.timeEstimate} min",
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
