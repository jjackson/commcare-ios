package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ConnectIdViewModel

@Composable
fun NameEntryStep(viewModel: ConnectIdViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = "What's your name?",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = viewModel.fullName,
            onValueChange = { viewModel.fullName = it },
            label = { Text("Full name") },
            modifier = Modifier.fillMaxWidth().testTag("full_name_field"),
            singleLine = true
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
                onClick = { viewModel.submitName() },
                modifier = Modifier.fillMaxWidth().testTag("continue_button"),
                enabled = viewModel.fullName.isNotBlank()
            ) {
                Text("Continue")
            }
        }
    }
}
