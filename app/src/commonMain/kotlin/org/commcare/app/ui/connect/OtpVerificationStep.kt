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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.ConnectIdViewModel

@Composable
fun OtpVerificationStep(viewModel: ConnectIdViewModel) {
    // Send OTP automatically on first composition
    LaunchedEffect(Unit) {
        viewModel.sendOtp()
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Text(
            text = "Verify your phone",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter the verification code sent to your phone",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = viewModel.otpCode,
            onValueChange = { if (it.length <= 6) viewModel.otpCode = it },
            label = { Text("Verification code") },
            modifier = Modifier.fillMaxWidth().testTag("otp_field"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = MaterialTheme.typography.headlineSmall.copy(textAlign = TextAlign.Center)
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
                onClick = { viewModel.verifyOtp() },
                modifier = Modifier.fillMaxWidth().testTag("verify_button"),
                enabled = viewModel.otpCode.length == 6
            ) {
                Text("Verify")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { viewModel.sendOtp() },
                modifier = Modifier.fillMaxWidth().testTag("resend_button")
            ) {
                Text("Resend Code")
            }
        }
    }
}
