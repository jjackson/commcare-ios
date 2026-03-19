package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared error display used across registration wizard steps.
 * Shows the error message and a "Dismiss" text button to clear it.
 */
@Composable
fun ErrorDisplay(errorMessage: String?, onDismiss: () -> Unit) {
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onDismiss) {
            Text("Dismiss")
        }
    }
}
