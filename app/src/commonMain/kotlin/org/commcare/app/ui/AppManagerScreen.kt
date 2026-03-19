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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.commcare.app.model.AppStatus
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.viewmodel.AppManagerViewModel

@Composable
fun AppManagerScreen(
    viewModel: AppManagerViewModel,
    onBack: () -> Unit,
    onInstallNew: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    var appToUninstall by remember { mutableStateOf<ApplicationRecord?>(null) }

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
                text = "App Manager",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            if (viewModel.apps.isEmpty()) {
                item {
                    Text(
                        text = "No apps installed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(viewModel.apps) { app ->
                    AppCard(
                        app = app,
                        isSeated = app.id == viewModel.seatedAppId,
                        onSetActive = { viewModel.seatApp(app.id) },
                        onArchive = { viewModel.archiveApp(app.id) },
                        onUninstall = { appToUninstall = app }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        HorizontalDivider()

        // Bottom: Install New App button
        Column(modifier = Modifier.padding(16.dp)) {
            val canInstall = viewModel.canInstallMore()
            Button(
                onClick = onInstallNew,
                modifier = Modifier.fillMaxWidth(),
                enabled = canInstall
            ) {
                Text("Install New App")
            }
            if (!canInstall) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "(max ${AppManagerViewModel.MAX_APPS} apps)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Uninstall confirmation dialog
    val uninstallTarget = appToUninstall
    if (uninstallTarget != null) {
        AlertDialog(
            onDismissRequest = { appToUninstall = null },
            title = { Text("Uninstall App") },
            text = { Text("Remove \"${uninstallTarget.displayName}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.uninstallApp(uninstallTarget.id)
                        appToUninstall = null
                    }
                ) {
                    Text(
                        text = "Uninstall",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { appToUninstall = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AppCard(
    app: ApplicationRecord,
    isSeated: Boolean,
    onSetActive: () -> Unit,
    onArchive: () -> Unit,
    onUninstall: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                AppStatusBadge(app = app, isSeated = isSeated)
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "${app.domain} · v${app.majorVersion}.${app.minorVersion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isSeated && app.isUsable()) {
                    TextButton(
                        onClick = onSetActive,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("Set Active")
                    }
                }
                if (app.isUsable()) {
                    TextButton(
                        onClick = onArchive,
                        modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                    ) {
                        Text("Archive")
                    }
                }
                TextButton(
                    onClick = onUninstall,
                    modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                ) {
                    Text(
                        text = "Uninstall",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun AppStatusBadge(app: ApplicationRecord, isSeated: Boolean) {
    val (label, color) = when {
        isSeated && app.isUsable() -> "Active" to MaterialTheme.colorScheme.primary
        app.isArchived() -> "Archived" to MaterialTheme.colorScheme.tertiary
        else -> "Installed" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}
