package org.commcare.app.ui.connect

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Shows delivery statistics for a claimed opportunity:
 * total / completed / pending / approved counts, a progress bar,
 * and a "Start Delivery" button that would launch the CommCare deliver app.
 */
@Composable
fun DeliveryProgressScreen(
    viewModel: OpportunitiesViewModel,
    opportunity: Opportunity,
    onStartDelivery: (() -> Unit)? = null
) {
    val status = viewModel.deliveryStatus

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Delivery Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (status == null) {
            Text(
                text = "No delivery data available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Progress bar
            val progress = if (status.totalDeliveries > 0) {
                status.completedDeliveries.toFloat() / status.totalDeliveries.toFloat()
            } else 0f

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${status.completedDeliveries} / ${status.totalDeliveries}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats grid
            Row(modifier = Modifier.fillMaxWidth()) {
                DeliveryStatCard(label = "Total", value = status.totalDeliveries.toString(), modifier = Modifier.weight(1f))
                DeliveryStatCard(label = "Completed", value = status.completedDeliveries.toString(), modifier = Modifier.weight(1f))
                DeliveryStatCard(label = "Pending", value = status.pendingDeliveries.toString(), modifier = Modifier.weight(1f))
                DeliveryStatCard(label = "Approved", value = status.approvedDeliveries.toString(), modifier = Modifier.weight(1f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Start Delivery button
        val hasDeliverApp = opportunity.deliverAppId != null
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
