package org.commcare.app.ui.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

@Composable
fun OpportunitiesListScreen(
    viewModel: OpportunitiesViewModel,
    onOpportunitySelected: (Opportunity) -> Unit,
    onBack: () -> Unit,
    onMessaging: (() -> Unit)? = null
) {
    LaunchedEffect(Unit) {
        viewModel.loadOpportunities()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Android-style top bar
        ConnectTopBar(
            onBack = onBack,
            onMessaging = onMessaging
        )

        // Loading
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ConnectIndigo)
            }
        }

        // Error
        if (viewModel.errorMessage != null) {
            ConnectWarningBanner(viewModel.errorMessage!!)
        }

        if (!viewModel.isLoading) {
            if (viewModel.opportunities.isEmpty() && viewModel.errorMessage == null) {
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
                val inProgress = viewModel.opportunities.filter { it.isClaimed && it.isActive }
                val available = viewModel.opportunities.filter { !it.isClaimed && it.isActive }
                val completed = viewModel.opportunities.filter { !it.isActive }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (inProgress.isNotEmpty()) {
                        item { SectionHeader("In Progress") }
                        items(inProgress) { opp ->
                            OpportunityCard(
                                opportunity = opp,
                                showResume = true,
                                onResume = {
                                    viewModel.selectOpportunity(opp)
                                    onOpportunitySelected(opp)
                                },
                                onViewInfo = {
                                    viewModel.selectOpportunity(opp)
                                    onOpportunitySelected(opp)
                                }
                            )
                        }
                    }

                    if (available.isNotEmpty()) {
                        item { SectionHeader("Available") }
                        items(available) { opp ->
                            OpportunityCard(
                                opportunity = opp,
                                showResume = false,
                                onViewInfo = {
                                    viewModel.selectOpportunity(opp)
                                    onOpportunitySelected(opp)
                                }
                            )
                        }
                    }

                    if (completed.isNotEmpty()) {
                        item { SectionHeader("Completed") }
                        items(completed) { opp ->
                            OpportunityCard(
                                opportunity = opp,
                                showResume = opp.isClaimed,
                                onResume = {
                                    viewModel.selectOpportunity(opp)
                                    onOpportunitySelected(opp)
                                },
                                onViewInfo = {
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
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * Opportunity card matching Android design:
 * - White card with subtle elevation
 * - Title + end date
 * - "Review" and "View Info" pill buttons
 */
@Composable
private fun OpportunityCard(
    opportunity: Opportunity,
    showResume: Boolean,
    onResume: (() -> Unit)? = null,
    onViewInfo: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Title
            Text(
                text = opportunity.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(4.dp))

            // End date in Android format
            val endDateDisplay = formatDateForDisplay(opportunity.endDate)
            if (endDateDisplay != null) {
                Text(
                    text = "Task ended on $endDateDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pill buttons row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (showResume && onResume != null) {
                    ConnectPillButton(
                        text = "Resume",
                        onClick = onResume
                    )
                } else if (!showResume && opportunity.isClaimed) {
                    ConnectPillButton(
                        text = "Review",
                        onClick = onViewInfo
                    )
                }
                ConnectPillButton(
                    text = "View Info",
                    onClick = onViewInfo
                )
            }
        }
    }
}
