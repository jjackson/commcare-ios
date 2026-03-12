package org.commcare.app.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.FormStatus
import org.commcare.app.viewmodel.FormQueueViewModel
import org.commcare.app.viewmodel.SyncState
import org.commcare.app.viewmodel.SyncViewModel

@Composable
fun SyncScreen(
    syncViewModel: SyncViewModel,
    formQueueViewModel: FormQueueViewModel,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Sync",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Sync status
        when (val state = syncViewModel.syncState) {
            is SyncState.Idle -> {
                Text("Ready to sync", style = MaterialTheme.typography.bodyLarge)
                if (syncViewModel.lastSyncTime != null) {
                    Text(
                        text = "Last sync: ${syncViewModel.lastSyncTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is SyncState.Syncing -> {
                Text(state.message, style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is SyncState.Complete -> {
                Text(
                    "Sync complete",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                if (syncViewModel.lastSyncTime != null) {
                    Text(
                        text = "Last sync: ${syncViewModel.lastSyncTime}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            is SyncState.Error -> {
                Text(
                    state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { syncViewModel.sync() },
            modifier = Modifier.fillMaxWidth(),
            enabled = syncViewModel.syncState !is SyncState.Syncing
        ) {
            Text("Sync Now")
        }

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Form queue
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Unsent Forms (${formQueueViewModel.pendingCount})",
                style = MaterialTheme.typography.titleMedium
            )
            if (formQueueViewModel.pendingCount > 0) {
                OutlinedButton(
                    onClick = { formQueueViewModel.submitAll() },
                    enabled = !formQueueViewModel.isSubmitting
                ) {
                    Text("Submit All")
                }
            }
        }

        if (formQueueViewModel.lastError != null) {
            Text(
                text = formQueueViewModel.lastError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (formQueueViewModel.queuedForms.isEmpty()) {
            Text(
                text = "No unsent forms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(formQueueViewModel.queuedForms) { form ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(form.formName, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = when (form.status) {
                                        FormStatus.PENDING -> "Pending"
                                        FormStatus.SUBMITTING -> "Submitting..."
                                        FormStatus.SUBMITTED -> "Submitted"
                                        FormStatus.FAILED -> "Failed (retry ${form.retryCount})"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = when (form.status) {
                                        FormStatus.FAILED -> MaterialTheme.colorScheme.error
                                        FormStatus.SUBMITTED -> MaterialTheme.colorScheme.primary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}
