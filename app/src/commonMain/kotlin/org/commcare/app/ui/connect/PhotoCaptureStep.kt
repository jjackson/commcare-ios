package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.commcare.app.platform.PlatformImageCapture
import org.commcare.app.viewmodel.ConnectIdViewModel

@Composable
fun PhotoCaptureStep(viewModel: ConnectIdViewModel) {
    val imageCapture = remember { PlatformImageCapture() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Take a selfie",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "A photo helps verify your identity",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Large camera button
        Box(
            modifier = Modifier
                .size(120.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "\uD83D\uDCF7",
                style = MaterialTheme.typography.displayLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        ErrorDisplay(viewModel.errorMessage) { viewModel.clearError() }

        Spacer(modifier = Modifier.height(16.dp))

        if (viewModel.isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    imageCapture.captureFromCamera { filePath ->
                        if (filePath != null) {
                            // Pass the file path as the "base64" identifier;
                            // real encoding happens server-side or in the VM.
                            viewModel.onPhotoCaptured(filePath)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("take_photo_button")
            ) {
                Text("Take Photo")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { viewModel.onPhotoCaptured("") },
                modifier = Modifier.fillMaxWidth().testTag("skip_photo_button")
            ) {
                Text("Skip Photo")
            }
        }
    }
}
