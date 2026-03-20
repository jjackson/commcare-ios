package org.commcare.app.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.commcare.app.model.Opportunity
import org.commcare.app.viewmodel.OpportunitiesViewModel

private enum class DetailTab { LEARN, DELIVER, PAYMENTS }

/**
 * Determines the current lifecycle step for a claimed opportunity.
 * Returns 1-4: New, Learn, Review, Delivery.
 */
private fun determineJobStep(opp: Opportunity): Int {
    if (opp.claim == null) return 1

    val learnSummary = opp.learnProgress
    val hasLearnApp = opp.learnApp != null

    // If there's a learn app, check whether learning is complete
    if (hasLearnApp) {
        if (learnSummary == null || learnSummary.completedModules < learnSummary.totalModules) {
            return 2 // Still learning
        }
    }

    // Check delivery progress
    if (opp.deliverProgress > 0) {
        return 4 // Delivering
    }

    // Learning complete but no deliveries yet -> review phase
    return 3
}

/**
 * Detail screen for a single Connect opportunity.
 *
 * Unclaimed: shows job intro with description, learn modules, delivery details,
 * and "Start Learning" button.
 * Claimed: shows 4-step progress indicator and tabbed sections for
 * Learn, Deliver, and Payments.
 *
 * [onDownloadApp] is forwarded to the Learn / Deliver sub-screens so they can
 * show download buttons.  When null, download buttons are hidden.
 */
@Composable
fun OpportunityDetailScreen(
    viewModel: OpportunitiesViewModel,
    onBack: () -> Unit,
    onDownloadApp: ((installUrl: String, appName: String) -> Unit)? = null
) {
    val opp = viewModel.selectedOpportunity ?: return

    // Load sub-detail data if already claimed
    LaunchedEffect(opp.id, opp.isClaimed) {
        if (opp.isClaimed) {
            viewModel.loadLearnProgress(opp.id)
            viewModel.loadDeliveryProgress(opp.id)
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

        if (opp.isClaimed) {
            // --- Claimed: Job Detail ---

            // End date with urgency coloring
            opp.endDate?.let { endDate ->
                Text(
                    text = "Ends: $endDate",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 4-step progress indicator
            JobProgressBar(
                currentStep = determineJobStep(opp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider()

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
                DetailTab.LEARN -> LearnProgressScreen(
                    viewModel = viewModel,
                    onDownloadApp = onDownloadApp
                )
                DetailTab.DELIVER -> DeliveryProgressScreen(
                    viewModel = viewModel,
                    opportunity = opp,
                    onDownloadApp = onDownloadApp
                )
                DetailTab.PAYMENTS -> PaymentScreen(viewModel = viewModel)
            }
        } else {
            // --- Unclaimed: Job Intro ---
            JobIntroContent(
                opp = opp,
                onStartLearning = { viewModel.startLearning(opp.opportunityId) },
                isLoading = viewModel.isLoading
            )
        }
    }
}

/**
 * Job intro layout for unclaimed opportunities.
 * Shows full description, learn modules list, delivery details, and start button.
 */
@Composable
private fun JobIntroContent(
    opp: Opportunity,
    onStartLearning: () -> Unit,
    isLoading: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Organization
        Text(
            text = opp.organization,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Full description
        if (opp.description.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = opp.description,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // --- Learn Modules ---
        Text(
            text = "Learn Modules",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val learnApp = opp.learnApp
        if (learnApp != null && learnApp.learnModules.isNotEmpty()) {
            learnApp.learnModules.forEachIndexed { index, module ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(28.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = module.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (module.description.isNotBlank()) {
                            Text(
                                text = module.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (module.timeEstimate > 0) {
                            Text(
                                text = "${module.timeEstimate} hour${if (module.timeEstimate != 1) "s" else ""}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No learning required",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // --- Delivery Details ---
        Text(
            text = "Delivery Details",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        val currency = opp.currency ?: ""

        if (opp.maxVisitsPerUser > 0) {
            LabeledField(label = "Max visits", value = "${opp.maxVisitsPerUser} per user")
        }
        if (opp.dailyMaxVisitsPerUser > 0) {
            LabeledField(label = "Daily limit", value = "${opp.dailyMaxVisitsPerUser} per day")
        }
        if (opp.budgetPerVisit > 0) {
            LabeledField(label = "Payment", value = "${opp.budgetPerVisit} $currency per visit")
        }
        if (opp.budgetPerUser > 0) {
            LabeledField(label = "Budget per user", value = "${opp.budgetPerUser} $currency")
        }
        opp.endDate?.let {
            LabeledField(label = "End date", value = it)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action button
        if (opp.isActive) {
            val hasLearnApp = opp.learnApp != null
            Button(
                onClick = onStartLearning,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (hasLearnApp) "Start Learning" else "Start")
            }
        } else {
            Text(
                text = "This opportunity is no longer active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ======================================================================
// Job Progress Bar
// ======================================================================

private val stepLabels = listOf("New", "Learn", "Review", "Delivery")

/**
 * 4-step progress indicator: New -> Learn -> Review -> Delivery.
 *
 * Each step shows as a numbered circle. Steps are colored:
 * - Completed: filled primary color
 * - Current: filled with a visible border
 * - Pending: gray outline
 */
@Composable
fun JobProgressBar(currentStep: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (step in 1..4) {
            val isCompleted = step < currentStep
            val isCurrent = step == currentStep

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                // Circle
                val circleColor = when {
                    isCompleted -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                }
                val borderColor = when {
                    isCompleted -> MaterialTheme.colorScheme.primary
                    isCurrent -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                val textColor = when {
                    isCompleted || isCurrent -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(circleColor, CircleShape)
                        .border(2.dp, borderColor, CircleShape)
                ) {
                    Text(
                        text = step.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stepLabels[step - 1],
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompleted || isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ======================================================================
// Shared components
// ======================================================================

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
