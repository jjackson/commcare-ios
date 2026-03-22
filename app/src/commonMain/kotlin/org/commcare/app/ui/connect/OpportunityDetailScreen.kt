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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import org.commcare.app.model.daysUntil
import org.commcare.app.viewmodel.OpportunitiesViewModel

private enum class DetailTab { PROGRESS, PAYMENT }

/**
 * Determines lifecycle step: 1=New, 2=Learn, 3=Review, 4=Delivery, 5=Complete.
 */
private fun determineJobStep(opp: Opportunity): Int {
    if (opp.claim == null) return 1
    val learnSummary = opp.learnProgress
    if (opp.learnApp != null) {
        if (learnSummary == null || learnSummary.completedModules < learnSummary.totalModules)
            return 2
    }
    if (opp.deliverProgress > 0) return 4
    if (!opp.isActive) return 5
    return 3
}

@Composable
fun OpportunityDetailScreen(
    viewModel: OpportunitiesViewModel,
    onBack: () -> Unit,
    onDownloadApp: ((installUrl: String, appName: String) -> Unit)? = null
) {
    val opp = viewModel.selectedOpportunity ?: return

    LaunchedEffect(opp.id, opp.isClaimed) {
        if (opp.isClaimed) {
            viewModel.loadLearnProgress(opp.id)
            viewModel.loadDeliveryProgress(opp.id)
        }
    }

    var selectedTab by remember { mutableStateOf(DetailTab.PROGRESS) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Standard Connect top bar
        ConnectTopBar(onBack = onBack)

        if (opp.isClaimed) {
            // --- Claimed: Android-style job detail ---
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Card header with title, date, pill buttons
                JobDetailHeaderCard(opp, onBack = {
                    viewModel.clearSelection()
                    onBack()
                })

                // Warning banner for ended/suspended jobs
                if (!opp.isActive) {
                    ConnectWarningBanner("The job has ended. You will not earn any progress for additional work.")
                } else if (opp.isUserSuspended) {
                    ConnectWarningBanner("You are suspended from this opportunity.")
                }

                // Icon stepper row
                IconStepperRow(currentStep = determineJobStep(opp))

                // Tab bar (Progress | Payment)
                TabBar(selectedTab = selectedTab, onTabSelected = { selectedTab = it })

                // Tab content
                when (selectedTab) {
                    DetailTab.PROGRESS -> ProgressTabContent(
                        viewModel = viewModel,
                        opportunity = opp,
                        onDownloadApp = onDownloadApp
                    )
                    DetailTab.PAYMENT -> PaymentTabContent(
                        viewModel = viewModel,
                        opportunity = opp
                    )
                }
            }
        } else {
            // --- Unclaimed: Job Intro ---
            if (opp.isUserSuspended) {
                ConnectWarningBanner("You are suspended from this opportunity.")
            }
            JobIntroContent(
                opp = opp,
                onStartLearning = { viewModel.startLearning(opp.opportunityId) },
                isLoading = viewModel.isLoading
            )
        }
    }
}

/**
 * Card at top of detail screen matching Android:
 * title, end date, Resume + View Info pill buttons.
 */
@Composable
private fun JobDetailHeaderCard(opp: Opportunity, onBack: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = opp.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            val endDateDisplay = formatDateForDisplay(opp.endDate)
            if (endDateDisplay != null) {
                Text(
                    text = "Task ended on $endDateDisplay",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (opp.isActive) {
                    ConnectPillButton(text = "Resume", onClick = {})
                }
                ConnectPillButton(text = "View Info", onClick = {})
            }
        }
    }
}

/**
 * 5-step icon row matching Android's stepper.
 * Uses circle icons with emoji representations.
 */
@Composable
private fun IconStepperRow(currentStep: Int) {
    val steps = listOf(
        "\uD83D\uDCCB" to "Info",       // clipboard
        "\uD83D\uDCDA" to "Learn",      // books
        "\u2705" to "Review",            // check
        "\uD83D\uDE9A" to "Deliver",    // truck
        "\uD83D\uDCE8" to "Complete"    // envelope
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (icon, _) ->
            val stepNum = index + 1
            val isCompleted = stepNum < currentStep
            val isCurrent = stepNum == currentStep

            val bgColor = when {
                isCompleted -> ConnectIndigo
                isCurrent -> Color(0xFFFF9800) // orange for current
                else -> Color(0xFFE0E0E0)
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bgColor)
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Connector line between steps
            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .width(16.dp)
                        .background(
                            if (stepNum < currentStep) ConnectIndigo
                            else Color(0xFFE0E0E0)
                        )
                )
            }
        }
    }
}

/**
 * Tab bar matching Android's Progress | Payment with underline indicator.
 */
@Composable
private fun TabBar(selectedTab: DetailTab, onTabSelected: (DetailTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        DetailTab.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            val label = when (tab) {
                DetailTab.PROGRESS -> "Progress"
                DetailTab.PAYMENT -> "Payment"
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) }
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) ConnectIndigo
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isSelected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(3.dp)
                            .background(ConnectIndigo, RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
    HorizontalDivider()
}

/**
 * Progress tab: circular progress in blue card, delivery stats, sync button.
 */
@Composable
private fun ProgressTabContent(
    viewModel: OpportunitiesViewModel,
    opportunity: Opportunity,
    onDownloadApp: ((installUrl: String, appName: String) -> Unit)? = null
) {
    val detail = viewModel.deliveryProgress

    // "Delivery Progress" header
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\uD83D\uDCE6",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            text = "Delivery Progress",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }

    // Blue card with circular progress
    val totalDeliveries = detail?.deliveries?.size ?: 0
    val approvedCount = detail?.deliveries?.count { it.status == "approved" } ?: 0
    val maxPayments = detail?.maxPayments ?: opportunity.maxVisitsPerUser
    val progressDenom = if (maxPayments > 0) maxPayments else totalDeliveries.coerceAtLeast(1)
    val progress = approvedCount.toFloat() / progressDenom.toFloat()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = ConnectIndigo),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You have completed $approvedCount of $progressDenom visits.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Circular progress
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF3F51B5))
                    .border(6.dp, Color(0xFF7986CB), CircleShape)
            ) {
                Text(
                    text = "${(progress * 100).toInt().coerceIn(0, 100)}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    // Sync button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Click to sync progress",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Button(
            onClick = { viewModel.loadDeliveryProgress(opportunity.id) },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF424242)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Text("SYNC \u21BB", color = Color.White)
        }
    }

    // Download buttons if applicable
    val deliverApp = opportunity.deliverApp
    if (onDownloadApp != null && deliverApp != null && !deliverApp.installUrl.isNullOrBlank()) {
        Button(
            onClick = { onDownloadApp(deliverApp.installUrl, deliverApp.name) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ConnectIndigo)
        ) {
            Text("Download Deliver App")
        }
    }

    val learnApp = opportunity.learnApp
    if (onDownloadApp != null && learnApp != null && !learnApp.installUrl.isNullOrBlank()) {
        Button(
            onClick = { onDownloadApp(learnApp.installUrl, learnApp.name) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ConnectIndigo)
        ) {
            Text("Download Learn App")
        }
    }

    // Per-payment-unit breakdown with approved/remaining counts
    if (opportunity.paymentUnits.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Payment Breakdown",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        val allDeliveries = detail?.deliveries ?: opportunity.deliveries
        opportunity.paymentUnits.forEach { unit ->
            val unitDeliveries = allDeliveries.filter { it.deliverUnitSlugId == unit.paymentUnitId }
            val unitApproved = unitDeliveries.count { it.status == "approved" }
            val maxForUnit = unit.maxTotal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = unit.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = buildString {
                            append("$unitApproved approved")
                            if (maxForUnit != null) append(" / $maxForUnit max")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "${unit.amount} ${opportunity.currency ?: ""}/visit",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Payment tab: Earned and Transferred summary cards matching Android.
 */
@Composable
private fun PaymentTabContent(
    viewModel: OpportunitiesViewModel,
    opportunity: Opportunity
) {
    val detail = viewModel.deliveryProgress
    val currency = opportunity.currency ?: ""
    val payments = detail?.payments ?: emptyList()

    val totalEarned = payments.sumOf {
        it.amount.toDoubleOrNull() ?: 0.0
    }
    val totalTransferred = payments.filter { it.confirmed }.sumOf {
        it.amount.toDoubleOrNull() ?: 0.0
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Earned + Transferred cards side by side
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Earned card (blue)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = ConnectIndigo),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Earned",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "\u2193",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${totalEarned.toInt()} $currency",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        // Transferred card (teal/green)
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(containerColor = ConnectTeal),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Transferred",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
                Text(
                    text = "\u21C4",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${totalTransferred.toInt()} $currency",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }

    // Individual payment records below
    if (payments.isNotEmpty()) {
        Spacer(modifier = Modifier.height(16.dp))
        payments.forEach { payment ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
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
                    Text(
                        text = if (payment.confirmed) "\u2713 Confirmed" else "Pending",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (payment.confirmed) ConnectIndigo
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Job intro layout for unclaimed opportunities.
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
    ) {
        // Icon stepper at top
        IconStepperRow(currentStep = 1)

        // Delivery Details card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = ConnectIndigo),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Delivery Details",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Review the delivery details",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val currency = opp.currency ?: ""

                // Checklist items matching Android
                if (opp.maxVisitsPerUser > 0) {
                    DeliveryDetailItem(
                        icon = "\u2713",
                        text = "${opp.maxVisitsPerUser} maximum Visits"
                    )
                }

                val daysLeft = opp.daysRemaining
                DeliveryDetailItem(
                    icon = "\u2713",
                    text = "$daysLeft Days to complete"
                )

                if (opp.dailyMaxVisitsPerUser > 0) {
                    DeliveryDetailItem(
                        icon = "\u2713",
                        text = "Maximum visits per day ${opp.dailyMaxVisitsPerUser}"
                    )
                }

                // Payment per visit with units
                if (opp.paymentUnits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pay per visit:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White
                    )
                    opp.paymentUnits.forEach { unit ->
                        Text(
                            text = " \u2022 ${unit.name}: ${unit.amount} $currency",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White
                        )
                    }
                } else if (opp.budgetPerVisit > 0) {
                    DeliveryDetailItem(
                        icon = "\u2713",
                        text = "Payment: ${opp.budgetPerVisit} $currency per visit"
                    )
                }

                // Working hours if specified
                if (opp.dailyStartTime != null && opp.dailyFinishTime != null) {
                    DeliveryDetailItem(
                        icon = "\u2713",
                        text = "Working hours: ${opp.dailyStartTime} - ${opp.dailyFinishTime}"
                    )
                }

                // Payment accrued
                if (opp.paymentAccrued > 0) {
                    DeliveryDetailItem(
                        icon = "\u2713",
                        text = "Payment accrued: ${opp.paymentAccrued} $currency"
                    )
                }
            }
        }

        // Description
        if (opp.description.isNotBlank()) {
            Text(
                text = opp.description,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Action button
        if (opp.isActive) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onStartLearning,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ConnectIndigo)
            ) {
                Text(if (opp.learnApp != null) "Start Learning" else "Start")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DeliveryDetailItem(icon: String, text: String) {
    Row(
        modifier = Modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.width(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White
        )
    }
}
