package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.DeliveryRecord
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Shows delivery progress for a claimed opportunity:
 * a list of delivery records with status, and summary stats.
 * Includes a "Start Delivery" button that would launch the CommCare deliver app.
 */
@Composable
fun DeliveryProgressScreen(
    viewModel: OpportunitiesViewModel,
    opportunity: Opportunity,
    onStartDelivery: (() -> Unit)? = null
) {
    val detail = viewModel.deliveryProgress

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Delivery Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (detail == null) {
            Text(
                text = "No delivery data available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Summary stats
            val totalDeliveries = detail.deliveries.size
            val approvedCount = detail.deliveries.count { it.status == "approved" }

            // Progress bar
            val progress = if (detail.maxPayments > 0) {
                approvedCount.toFloat() / detail.maxPayments.toFloat()
            } else if (totalDeliveries > 0) {
                approvedCount.toFloat() / totalDeliveries.toFloat()
            } else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Approved",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "$approvedCount / ${if (detail.maxPayments > 0) detail.maxPayments else totalDeliveries}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            val pendingCount = detail.deliveries.count { it.status == "pending" }
            val rejectedCount = detail.deliveries.count { it.status == "rejected" }
            Row(modifier = Modifier.fillMaxWidth()) {
                DeliveryStatCard(label = "Total", value = totalDeliveries.toString(), modifier = Modifier.weight(1f))
                DeliveryStatCard(label = "Approved", value = approvedCount.toString(), modifier = Modifier.weight(1f))
                DeliveryStatCard(label = "Pending", value = pendingCount.toString(), modifier = Modifier.weight(1f))
                DeliveryStatCard(label = "Rejected", value = rejectedCount.toString(), modifier = Modifier.weight(1f))
            }

            // Payment accrued
            if (detail.paymentAccrued > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Payment accrued: ${detail.paymentAccrued}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Delivery button
        val hasDeliverApp = opportunity.deliverApp != null
        Button(
            onClick = { onStartDelivery?.invoke() },
            enabled = hasDeliverApp && onStartDelivery != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Delivery")
        }

        if (!hasDeliverApp) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "No delivery app configured for this opportunity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Delivery records list
        if (detail != null && detail.deliveries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Delivery Records",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            // Note: We're inside a Column, so we can't use LazyColumn directly.
            // Show a limited number of records to avoid nesting scrollable containers.
            for (record in detail.deliveries.take(50)) {
                DeliveryRecordRow(record)
            }
            if (detail.deliveries.size > 50) {
                Text(
                    text = "... and ${detail.deliveries.size - 50} more",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DeliveryRecordRow(record: DeliveryRecord) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.deliverUnitName ?: "Delivery #${record.id}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                val statusColor = when (record.status) {
                    "approved" -> MaterialTheme.colorScheme.primary
                    "pending" -> MaterialTheme.colorScheme.tertiary
                    "rejected" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = record.status.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                )
            }
            record.visitDate?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.entityName?.let {
                Text(
                    text = "Entity: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.reason?.let {
                Text(
                    text = "Reason: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DeliveryStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
