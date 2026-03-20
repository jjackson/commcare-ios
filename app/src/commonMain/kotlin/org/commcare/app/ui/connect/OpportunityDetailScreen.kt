package org.commcare.app.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

private enum class DetailTab { LEARN, DELIVER, PAYMENTS }

/**
 * Detail screen for a single Connect opportunity.
 *
 * Unclaimed: shows full info + "Claim" button.
 * Claimed: shows tabbed sections for Learn, Deliver, and Payments.
 */
@Composable
fun OpportunityDetailScreen(
    viewModel: OpportunitiesViewModel,
    onBack: () -> Unit
) {
    val opp = viewModel.selectedOpportunity ?: return

    // Load sub-detail data if already claimed
    LaunchedEffect(opp.id, opp.claimed) {
        if (opp.claimed) {
            viewModel.loadLearnProgress(opp.id)
            viewModel.loadDeliveryProgress(opp.id)
            viewModel.loadPayments(opp.id)
        }
    }

    var selectedTab by remember { mutableStateOf(DetailTab.LEARN) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .clickable { onBack() }
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = opp.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider()

        // Error message
        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (opp.claimed) {
            // Tab bar
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                DetailTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    Text(
                        text = tab.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clickable { selectedTab = tab }
                            .defaultMinSize(minHeight = 44.dp)
                            .padding(end = 24.dp, top = 8.dp, bottom = 8.dp)
                    )
                }
            }
            HorizontalDivider()

            // Tab content
            when (selectedTab) {
                DetailTab.LEARN -> LearnProgressScreen(viewModel = viewModel)
                DetailTab.DELIVER -> DeliveryProgressScreen(viewModel = viewModel, opportunity = opp)
                DetailTab.PAYMENTS -> PaymentScreen(viewModel = viewModel)
            }
        } else {
            // Full info + Claim button
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OpportunityInfoSection(opp)

                Spacer(modifier = Modifier.height(24.dp))

                if (opp.isActive) {
                    Button(
                        onClick = { viewModel.claimOpportunity(opp.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Claim This Opportunity")
                    }
                } else {
                    Text(
                        text = "This opportunity is no longer active.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun OpportunityInfoSection(opp: Opportunity) {
    // Organization
    LabeledField(label = "Organization", value = opp.organization)

    if (opp.description.isNotBlank()) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Description",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = opp.description,
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Budget / pay info
    opp.maxPayPerVisit?.let { LabeledField(label = "Max Pay / Visit", value = "$it ${opp.currency}") }
    opp.totalBudget?.let { LabeledField(label = "Total Budget", value = "$it ${opp.currency}") }
    opp.endDate?.let { LabeledField(label = "End Date", value = it) }

    // Status
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
        Text(if (opp.claimed) "Claimed" else if (opp.isActive) "Available" else "Inactive")
    }
}

@Composable
private fun LabeledField(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.defaultMinSize(minWidth = 120.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
