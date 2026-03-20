package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.PaymentRecord
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Standalone payment screen — used when navigated to directly.
 * Shows the Earned/Transferred summary + individual payment list.
 */
@Composable
fun PaymentScreen(viewModel: OpportunitiesViewModel) {
    val payments = viewModel.deliveryProgress?.payments ?: emptyList()
    val currency = viewModel.selectedOpportunity?.currency ?: ""

    val totalEarned = payments.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val totalTransferred = payments.filter { it.confirmed }.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Earned + Transferred summary cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = ConnectIndigo),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Earned", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text("\u2193", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${totalEarned.toInt()} $currency",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = ConnectTeal),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Transferred", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    Text("\u21C4", style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${totalTransferred.toInt()} $currency",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (payments.isEmpty()) {
            Text(
                text = "No payments recorded yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            payments.forEach { payment ->
                PaymentRow(
                    payment = payment,
                    currency = currency,
                    onConfirm = { viewModel.confirmPayment(payment.id) }
                )
            }
        }
    }
}

@Composable
private fun PaymentRow(payment: PaymentRecord, currency: String, onConfirm: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Confirm Payment") },
            text = {
                val amountDisplay = if (currency.isNotBlank()) "${payment.amount} $currency"
                else payment.amount
                Text("Confirm receipt of $amountDisplay?")
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmDialog = false
                    onConfirm()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${payment.amount} $currency",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                payment.datePaid?.let {
                    Text(
                        text = formatDateForDisplay(it) ?: it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (payment.confirmed) {
                Text(
                    text = "\u2713 Confirmed",
                    style = MaterialTheme.typography.labelSmall,
                    color = ConnectIndigo
                )
            } else {
                ConnectPillButton(
                    text = "Confirm",
                    onClick = { showConfirmDialog = true }
                )
            }
        }
    }
}
