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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.SettingsViewModel
import org.commcare.app.viewmodel.UpdateState
import org.commcare.app.viewmodel.UpdateViewModel
import org.commcare.app.viewmodel.UserKeyRecordManager

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    updateViewModel: UpdateViewModel?,
    onBack: () -> Unit,
    onRecovery: (() -> Unit)? = null,
    keyRecordManager: UserKeyRecordManager? = null,
    username: String? = null,
    domain: String? = null
) {
    var showPinDialog by remember { mutableStateOf(false) }
    var pinDialogMessage by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Server section
        SectionHeader("Server")
        OutlinedTextField(
            value = viewModel.serverUrl,
            onValueChange = { viewModel.serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        SectionDivider()

        // Sync section
        SectionHeader("Sync")
        CheckboxRow(
            label = "Auto-sync",
            checked = viewModel.autoSync,
            onCheckedChange = { viewModel.autoSync = it }
        )
        OutlinedTextField(
            value = viewModel.syncFrequencyMinutes.toString(),
            onValueChange = {
                val parsed = it.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    viewModel.syncFrequencyMinutes = parsed
                }
            },
            label = { Text("Sync frequency (minutes)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = viewModel.autoSync
        )

        SectionDivider()

        // Search section
        SectionHeader("Search")
        CheckboxRow(
            label = "Fuzzy search",
            checked = viewModel.fuzzySearchEnabled,
            onCheckedChange = { viewModel.fuzzySearchEnabled = it }
        )

        SectionDivider()

        // Auto-update section
        SectionHeader("Updates")
        CheckboxRow(
            label = "Auto-update",
            checked = viewModel.autoUpdateEnabled,
            onCheckedChange = { viewModel.autoUpdateEnabled = it }
        )
        if (viewModel.autoUpdateEnabled) {
            OutlinedTextField(
                value = viewModel.autoUpdateFrequencyHours.toString(),
                onValueChange = {
                    val parsed = it.toIntOrNull()
                    if (parsed != null && parsed > 0) {
                        viewModel.autoUpdateFrequencyHours = parsed
                    }
                },
                label = { Text("Update check frequency (hours)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        // Manual update check
        if (updateViewModel != null) {
            Spacer(modifier = Modifier.height(8.dp))
            UpdateSection(updateViewModel)
        }

        // PIN / Quick Login section
        if (keyRecordManager != null && username != null && domain != null) {
            SectionDivider()
            SectionHeader("Quick Login")
            val hasPinSet = remember(username, domain) {
                keyRecordManager.hasPinSet(username, domain)
            }
            OutlinedButton(
                onClick = { showPinDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("set_pin_button")
            ) {
                Text(if (hasPinSet) "Change Login PIN" else "Set Login PIN")
            }
        }

        SectionDivider()

        // Locale section
        SectionHeader("Language")
        OutlinedTextField(
            value = viewModel.localeOverride ?: "",
            onValueChange = { viewModel.localeOverride = it.ifBlank { null } },
            label = { Text("Locale override (e.g., en, fra, hin)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        SectionDivider()

        // Developer section
        SectionHeader("Developer")
        CheckboxRow(
            label = "Developer mode",
            checked = viewModel.developerMode,
            onCheckedChange = { viewModel.developerMode = it }
        )
        if (viewModel.developerMode) {
            CheckboxRow(
                label = "Show debug info",
                checked = viewModel.showDebugInfo,
                onCheckedChange = { viewModel.showDebugInfo = it }
            )
            CheckboxRow(
                label = "Show form hierarchy",
                checked = viewModel.showFormHierarchy,
                onCheckedChange = { viewModel.showFormHierarchy = it }
            )
            CheckboxRow(
                label = "XPath tester",
                checked = viewModel.enableXPathTester,
                onCheckedChange = { viewModel.enableXPathTester = it }
            )
            OutlinedTextField(
                value = viewModel.logLevel,
                onValueChange = { viewModel.logLevel = it },
                label = { Text("Log level (debug, info, warn, error)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (viewModel.savedMessage != null) {
            Text(
                text = viewModel.savedMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Action buttons
        Button(
            onClick = { viewModel.save() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { viewModel.resetToDefaults() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to Defaults")
        }

        if (onRecovery != null) {
            Spacer(modifier = Modifier.height(24.dp))
            SectionDivider()
            SectionHeader("Troubleshooting")
            OutlinedButton(
                onClick = onRecovery,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Recovery Mode")
            }
        }
    }

    // PIN setup dialog
    if (showPinDialog && keyRecordManager != null && username != null && domain != null) {
        SetPinDialog(
            onConfirm = { newPin ->
                keyRecordManager.setPin(username, domain, newPin)
                pinDialogMessage = "PIN set successfully"
                showPinDialog = false
            },
            onDismiss = { showPinDialog = false },
            message = pinDialogMessage
        )
    }
}

@Composable
private fun UpdateSection(updateViewModel: UpdateViewModel) {
    when (val state = updateViewModel.updateState) {
        is UpdateState.Idle -> {
            OutlinedButton(
                onClick = { updateViewModel.checkForUpdates() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Check for Updates")
            }
        }
        is UpdateState.Checking -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                )
                Text("Checking for updates...", style = MaterialTheme.typography.bodyMedium)
            }
        }
        is UpdateState.Available -> {
            Column {
                Text(
                    text = "Update available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = { updateViewModel.installUpdate() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Install Update")
                }
            }
        }
        is UpdateState.Installing -> {
            Column {
                Text(
                    text = updateViewModel.updateMessage ?: "Installing...",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { updateViewModel.updateProgress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        is UpdateState.UpToDate -> {
            Text(
                text = "App is up to date",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        is UpdateState.Complete -> {
            Text(
                text = "Update installed successfully",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
        is UpdateState.Error -> {
            Column {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { updateViewModel.dismissUpdate() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(modifier = Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(modifier = Modifier.height(16.dp))
    HorizontalDivider()
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun CheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Dialog for setting a 6-digit login PIN.
 */
@Composable
private fun SetPinDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    message: String? = null
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Login PIN") },
        text = {
            Column {
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Text(
                    text = "Enter a 6-digit PIN for quick login.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pin = it },
                    label = { Text("PIN") },
                    modifier = Modifier.fillMaxWidth().testTag("set_pin_field"),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) confirmPin = it },
                    label = { Text("Confirm PIN") },
                    modifier = Modifier.fillMaxWidth().testTag("confirm_pin_field"),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        pin.length != 6 -> error = "PIN must be exactly 6 digits"
                        pin != confirmPin -> error = "PINs do not match"
                        else -> {
                            error = null
                            onConfirm(pin)
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_set_pin")
            ) {
                Text("Set PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
