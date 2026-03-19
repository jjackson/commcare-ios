package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ConnectIdViewModel

@Composable
fun BackupCodeStep(viewModel: ConnectIdViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = "Set a backup code",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This 6-digit code helps you recover your account",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = viewModel.backupCode,
            onValueChange = { if (it.length <= 6) viewModel.backupCode = it },
            label = { Text("6-digit backup code") },
            modifier = Modifier.fillMaxWidth().testTag("backup_code_field"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
        )

        Spacer(modifier = Modifier.height(8.dp))

        ErrorDisplay(viewModel.errorMessage) { viewModel.clearError() }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Button(
                onClick = { viewModel.submitBackupCode() },
                modifier = Modifier.fillMaxWidth().testTag("continue_button"),
                enabled = viewModel.backupCode.length == 6
            ) {
                Text("Continue")
            }
        }
    }
}
