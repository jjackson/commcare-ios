package org.commcare.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.commcare.app.platform.PlatformBarcodeScanner
import org.commcare.app.viewmodel.SetupViewModel

@Composable
fun SetupScreen(
    setupViewModel: SetupViewModel,
    onInstall: (String) -> Unit,
    onSignUpPersonalId: (() -> Unit)? = null,
    isConnectIdRegistered: Boolean = false,
    onConnectOpportunities: (() -> Unit)? = null
) {
    val scanner = remember { PlatformBarcodeScanner() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo / icon area — reuse "CC" text styling from login
        Text(
            text = "CC",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "CommCare",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Set up your application",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(40.dp))

        // If a profile URL was entered/scanned, show Install button
        if (setupViewModel.profileUrl.isNotBlank()) {
            Text(
                text = setupViewModel.profileUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { onInstall(setupViewModel.profileUrl) },
                modifier = Modifier.fillMaxWidth().testTag("install_button")
            ) {
                Text("Install")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Connect button — shown when Connect ID is registered
        if (isConnectIdRegistered && onConnectOpportunities != null) {
            Button(
                onClick = { onConnectOpportunities.invoke() },
                modifier = Modifier.fillMaxWidth().testTag("connect_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary
                )
            ) {
                Text("Connect")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // "or" divider
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text("  or  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Scan Application Barcode (primary)
        Button(
            onClick = {
                scanner.scanBarcode { result ->
                    if (result != null) {
                        setupViewModel.onQrScanned(result)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("scan_barcode_button")
        ) {
            Text("Scan Application Barcode")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Enter Code or URL (secondary)
        OutlinedButton(
            onClick = { setupViewModel.showEnterCode() },
            modifier = Modifier.fillMaxWidth().testTag("enter_code_button")
        ) {
            Text("Enter Code or URL")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Divider with "or"
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = "  or  ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Install from App List (tertiary)
        TextButton(
            onClick = { setupViewModel.showInstallFromList() },
            modifier = Modifier.fillMaxWidth().testTag("install_from_list_button")
        ) {
            Text("Install from App List")
        }

        // Error message if present
        if (setupViewModel.errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = setupViewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Personal ID link — shows sign-up or already-registered state
        if (onSignUpPersonalId != null) {
            if (isConnectIdRegistered) {
                Text(
                    text = "Signed in to Personal ID \u2713",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("signup_link")
                )
            } else {
                TextButton(
                    onClick = { onSignUpPersonalId.invoke() },
                    modifier = Modifier.testTag("signup_link")
                ) {
                    Text(
                        text = "Sign up for Personal ID",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
