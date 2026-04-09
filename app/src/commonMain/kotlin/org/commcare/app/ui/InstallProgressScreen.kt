package org.commcare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.AppInstallViewModel
import org.commcare.app.viewmodel.InstallState
import org.commcare.app.viewmodel.InstallStep
import org.commcare.app.viewmodel.StepStatus

/**
 * Full-screen install progress UI.
 *
 * Displays the list of [InstallStep]s with visual status indicators:
 *   - COMPLETED  → green check mark "✓"
 *   - IN_PROGRESS → small [CircularProgressIndicator]
 *   - PENDING    → gray circle "○"
 *   - FAILED     → red cross "✗"
 *
 * An overall [LinearProgressIndicator] shows aggregate progress.
 * A Cancel button lets the user abort.
 * On failure, an error message and Retry button are shown.
 * On completion, the caller is expected to react to [InstallState.Completed]
 * and navigate away; this composable does not self-navigate.
 *
 * @param viewModel   the [AppInstallViewModel] owning install state
 * @param onCancel    called when the user taps Cancel
 * @param onRetry     called when the user taps Retry after a failure
 */
@Composable
fun InstallProgressScreen(
    viewModel: AppInstallViewModel,
    onCancel: () -> Unit,
    onRetry: () -> Unit
) {
    when (val state = viewModel.installState) {
        is InstallState.Idle -> {
            // Nothing visible while idle — the parent should trigger install()
        }
        is InstallState.Installing -> {
            InstallProgressContent(
                appName = state.appName,
                steps = state.steps,
                onCancel = onCancel
            )
        }
        is InstallState.Completed -> {
            // Caller reacts to Completed state and navigates; show a brief "Done" UI
            InstallDoneContent(appName = state.app.displayName)
        }
        is InstallState.Failed -> {
            InstallFailedContent(
                errorMessage = state.message,
                onRetry = onRetry,
                onCancel = onCancel
            )
        }
    }
}

// ---- Private content composables -------------------------------------

@Composable
private fun InstallProgressContent(
    appName: String,
    steps: List<InstallStep>,
    onCancel: () -> Unit
) {
    val completedCount = steps.count { it.status == StepStatus.COMPLETED }
    val totalSteps = steps.size
    val overallProgress = if (totalSteps == 0) 0f else completedCount.toFloat() / totalSteps

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App icon placeholder + name
        Text(
            text = "CC",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = appName,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("install_progress_app_name")
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Step list
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            steps.forEach { step ->
                StepRow(step)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Overall progress bar
        LinearProgressIndicator(
            progress = { overallProgress },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag("install_cancel_button")
        ) {
            Text("Cancel")
        }
    }
}

@Composable
private fun StepRow(step: InstallStep) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Status indicator
        when (step.status) {
            StepStatus.COMPLETED -> Text(
                text = "✓",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            StepStatus.IN_PROGRESS -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
            StepStatus.PENDING -> Text(
                text = "○",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            StepStatus.FAILED -> Text(
                text = "✗",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Step label
        val labelColor = when (step.status) {
            StepStatus.COMPLETED -> MaterialTheme.colorScheme.onSurface
            StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onSurface
            StepStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            StepStatus.FAILED -> MaterialTheme.colorScheme.error
        }
        Text(
            text = step.label,
            style = MaterialTheme.typography.bodyMedium,
            color = labelColor
        )
    }
}

@Composable
private fun InstallDoneContent(appName: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "✓",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "$appName installed",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.testTag("install_done_label")
        )
    }
}

@Composable
private fun InstallFailedContent(
    errorMessage: String,
    onRetry: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Installation Failed",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("install_failed_label")
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().testTag("install_retry_button")
        ) {
            Text("Retry")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth().testTag("install_cancel_button")
        ) {
            Text("Cancel")
        }
    }
}
