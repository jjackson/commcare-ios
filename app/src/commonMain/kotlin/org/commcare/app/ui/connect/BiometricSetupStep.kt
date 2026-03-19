package org.commcare.app.ui.connect

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.commcare.app.platform.BiometricResult
import org.commcare.app.platform.PlatformBiometricAuth
import org.commcare.app.viewmodel.ConnectIdViewModel

@Composable
fun BiometricSetupStep(viewModel: ConnectIdViewModel) {
    val biometricAuth = remember { PlatformBiometricAuth() }
    val biometricAvailable = remember { biometricAuth.canAuthenticate() }
    var showPinEntry by remember { mutableStateOf(!biometricAvailable) }
    var pinValue by remember { mutableStateOf("") }
    var biometricError by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = "Secure your account",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Choose how to protect your Personal ID",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        ErrorDisplay(viewModel.errorMessage) { viewModel.clearError() }

        if (biometricError != null) {
            Text(
                text = biometricError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (viewModel.isLoading) {
            CircularProgressIndicator(modifier = Modifier.testTag("biometric_spinner"))
        } else {
            // Biometric option (only shown if device supports it)
            if (biometricAvailable && !showPinEntry) {
                Button(
                    onClick = {
                        biometricError = null
                        biometricAuth.authenticate("Secure your Personal ID") { result ->
                            when (result) {
                                is BiometricResult.Success ->
                                    viewModel.completeBiometricSetup("biometric")
                                is BiometricResult.Failure ->
                                    biometricError = result.message
                                is BiometricResult.Cancelled ->
                                    biometricError = "Authentication cancelled"
                                is BiometricResult.Unavailable ->
                                    showPinEntry = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("use_biometric_button")
                ) {
                    Text("Use Face ID / Touch ID")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showPinEntry = true },
                    modifier = Modifier.fillMaxWidth().testTag("use_pin_button")
                ) {
                    Text("Use PIN instead")
                }
            }

            // PIN entry
            if (showPinEntry) {
                Text(
                    text = "Enter a 6-digit PIN",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { if (it.length <= 6) pinValue = it },
                    label = { Text("PIN") },
                    modifier = Modifier.fillMaxWidth().testTag("pin_field"),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { viewModel.completeBiometricSetup("pin", pinValue) },
                    modifier = Modifier.fillMaxWidth().testTag("set_pin_button"),
                    enabled = pinValue.length == 6
                ) {
                    Text("Set PIN")
                }
            }
        }
    }
}
