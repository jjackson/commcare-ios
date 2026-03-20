package org.commcare.app.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.DeliveryRecord
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

private enum class DeliveryFilter(val label: String) {
    ALL("All"),
    APPROVED("Approved"),
    PENDING("Pending"),
    REJECTED("Rejected")
}

/**
 * Shows delivery progress for a claimed opportunity:
 * a list of delivery records with status, summary stats, and filter buttons.
 * Includes a "Start Delivery" button that would launch the CommCare deliver app.
 */
@Composable
fun DeliveryProgressScreen(
    viewModel: OpportunitiesViewModel,
    opportunity: Opportunity,
    onStartDelivery: (() -> Unit)? = null
) {
    val detail = viewModel.deliveryProgress
    var selectedFilter by remember { mutableStateOf(DeliveryFilter.ALL) }

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

        // Delivery records list with filter
        if (detail != null && detail.deliveries.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Delivery Records",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Filter buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeliveryFilter.entries.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .clickable { selectedFilter = filter }
                            .defaultMinSize(minHeight = 36.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Filtered records
            val filteredRecords = when (selectedFilter) {
                DeliveryFilter.ALL -> detail.deliveries
                DeliveryFilter.APPROVED -> detail.deliveries.filter { it.status == "approved" }
                DeliveryFilter.PENDING -> detail.deliveries.filter { it.status == "pending" }
                DeliveryFilter.REJECTED -> detail.deliveries.filter { it.status == "rejected" }
            }

            if (filteredRecords.isEmpty()) {
                Text(
                    text = "No ${selectedFilter.label.lowercase()} records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                for (record in filteredRecords.take(50)) {
                    DeliveryRecordRow(record)
                }
                if (filteredRecords.size > 50) {
                    Text(
                        text = "... and ${filteredRecords.size - 50} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                // Color-coded status badge
                StatusBadge(record.status)
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
            // Display flags if present
            if (record.flags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for ((flagKey, flagValue) in record.flags) {
                        Text(
                            text = "$flagKey: $flagValue",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Color-coded status badge for delivery records.
 */
@Composable
private fun StatusBadge(status: String) {
    val color = when (status) {
        "approved" -> MaterialTheme.colorScheme.primary
        "pending" -> MaterialTheme.colorScheme.tertiary
        "rejected" -> MaterialTheme.colorScheme.error
        "over_limit" -> MaterialTheme.colorScheme.error
        "incomplete" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bgColor = when (status) {
        "approved" -> MaterialTheme.colorScheme.primaryContainer
        "pending" -> MaterialTheme.colorScheme.tertiaryContainer
        "rejected" -> MaterialTheme.colorScheme.errorContainer
        "over_limit" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Text(
        text = status.replaceFirstChar { it.uppercase() }.replace('_', ' '),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
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
