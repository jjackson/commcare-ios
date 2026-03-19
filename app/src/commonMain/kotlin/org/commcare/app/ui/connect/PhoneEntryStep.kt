package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ConnectIdViewModel

@Composable
fun PhoneEntryStep(viewModel: ConnectIdViewModel) {
    var consentChecked by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = "Enter your phone number",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = viewModel.countryCode,
                onValueChange = { viewModel.countryCode = it },
                label = { Text("Code") },
                modifier = Modifier.width(88.dp).testTag("country_code_field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = viewModel.phoneNumber,
                onValueChange = { viewModel.phoneNumber = it },
                label = { Text("Phone number") },
                modifier = Modifier.weight(1f).testTag("phone_number_field"),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = consentChecked,
                onCheckedChange = { consentChecked = it },
                modifier = Modifier.testTag("consent_checkbox")
            )
            Text(
                text = "I agree to the terms",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        ErrorDisplay(viewModel.errorMessage) { viewModel.clearError() }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Button(
                onClick = { viewModel.submitPhone() },
                modifier = Modifier.fillMaxWidth().testTag("continue_button"),
                enabled = viewModel.phoneNumber.isNotBlank() && consentChecked
            ) {
                Text("Continue")
            }
        }
    }
}
