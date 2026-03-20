package org.commcare.app.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Browse the list of available CommCare-Connect opportunities.
 *
 * Shows a refreshable list of opportunity cards with name, organization,
 * description, claimed status, and pay info. Tapping a card calls
 * [viewModel.selectOpportunity] and triggers [onOpportunitySelected].
 */
@Composable
fun OpportunitiesListScreen(
    viewModel: OpportunitiesViewModel,
    onOpportunitySelected: (Opportunity) -> Unit,
    onBack: () -> Unit
) {
    // Load opportunities on first composition
    LaunchedEffect(Unit) {
        viewModel.loadOpportunities()
    }

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
                text = "CommCare-Connect",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            // Refresh button
            Text(
                text = "Refresh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { viewModel.loadOpportunities() }
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .padding(8.dp)
            )
        }

        HorizontalDivider()

        // Loading indicator
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Error message
        if (viewModel.errorMessage != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = viewModel.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(onClick = { viewModel.clearError() }) {
                    Text("Dismiss")
                }
            }
        }

        if (!viewModel.isLoading) {
            if (viewModel.opportunities.isEmpty() && viewModel.errorMessage == null) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No opportunities available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.opportunities) { opp ->
                        OpportunityCard(
                            opportunity = opp,
                            onClick = {
                                viewModel.selectOpportunity(opp)
                                onOpportunitySelected(opp)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OpportunityCard(opportunity: Opportunity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Name (bold) + status badge on same row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = opportunity.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                OpportunityStatusBadge(opportunity)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Organization subtitle
            Text(
                text = opportunity.organization,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (opportunity.shortDescription.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = opportunity.shortDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Pay info
            val payInfo = buildPayInfo(opportunity)
            if (payInfo != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = payInfo,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun OpportunityStatusBadge(opportunity: Opportunity) {
    val (label, color) = when {
        !opportunity.isActive -> "Inactive" to MaterialTheme.colorScheme.error
        opportunity.claimed -> "Claimed" to MaterialTheme.colorScheme.tertiary
        else -> "Available" to MaterialTheme.colorScheme.secondary
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

private fun buildPayInfo(opportunity: Opportunity): String? {
    val parts = mutableListOf<String>()
    opportunity.maxPayPerVisit?.let { parts.add("Up to $it ${opportunity.currency}/visit") }
    opportunity.totalBudget?.let { parts.add("Budget: $it ${opportunity.currency}") }
    return if (parts.isEmpty()) null else parts.joinToString(" · ")
}
