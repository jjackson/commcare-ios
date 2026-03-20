package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.PaymentRecord
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Shows a list of payments for a claimed opportunity.
 * Displays amount, confirmation status, and date for each payment.
 * Unconfirmed payments show a "Confirm" button.
 */
@Composable
fun PaymentScreen(viewModel: OpportunitiesViewModel) {
    val payments = viewModel.deliveryProgress?.payments ?: emptyList()

    Column(modifier = Modifier.fillMaxSize()) {
        if (payments.isEmpty()) {
            Text(
                text = "No payments recorded yet",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(payments) { payment ->
                    PaymentRow(
                        payment = payment,
                        onConfirm = { viewModel.confirmPayment(payment.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentRecord, onConfirm: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Amount
                Text(
                    text = payment.amount,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // Status badge
                val statusColor = if (payment.confirmed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = if (payment.confirmed) "Confirmed" else "Pending",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Date paid
            payment.datePaid?.let {
                Text(
                    text = "Paid: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Confirmation date
            payment.confirmationDate?.let {
                Text(
                    text = "Confirmed: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Confirm button for unconfirmed payments
            if (!payment.confirmed) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.width(120.dp)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}
