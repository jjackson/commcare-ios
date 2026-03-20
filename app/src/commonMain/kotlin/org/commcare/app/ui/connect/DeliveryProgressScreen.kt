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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
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
 * Delivery records list with filter chips and status badges.
 * Used as a drill-down from the progress tab.
 */
@Composable
fun DeliveryProgressScreen(
    viewModel: OpportunitiesViewModel,
    opportunity: Opportunity,
    onStartDelivery: (() -> Unit)? = null,
    onDownloadApp: ((installUrl: String, appName: String) -> Unit)? = null
) {
    val detail = viewModel.deliveryProgress
    var selectedFilter by remember { mutableStateOf(DeliveryFilter.ALL) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = "Delivery Records",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        if (detail == null || detail.deliveries.isEmpty()) {
            Text(
                text = "No delivery records yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Filter chips
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
                        color = if (isSelected) Color.White else ConnectIndigo,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected) ConnectIndigo
                                else ConnectIndigoLight
                            )
                            .clickable { selectedFilter = filter }
                            .defaultMinSize(minHeight = 36.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                StatusBadge(record.status)
            }
            record.visitDate?.let {
                Text(
                    text = formatDateForDisplay(it) ?: it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.entityName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            record.reason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
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
                            color = ConnectIndigo,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(ConnectIndigoLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (color, bgColor) = when (status) {
        "approved" -> ConnectIndigo to ConnectIndigoLight
        "pending" -> Color(0xFFFF9800) to Color(0xFFFFF3E0)
        "rejected" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        "over_limit" -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant to Color(0xFFEEEEEE)
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
