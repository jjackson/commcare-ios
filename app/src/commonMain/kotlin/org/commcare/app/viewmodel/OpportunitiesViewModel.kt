package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.commcare.app.model.DeliveryProgressDetail
import org.commcare.app.model.LearnProgressDetail
import org.commcare.app.model.Opportunity
import org.commcare.app.network.ConnectMarketplaceApi

/**
 * ViewModel for the Connect opportunities hub.
 *
 * Manages loading and selection state for opportunity browsing, plus the
 * detail-level state for learn progress, delivery progress, and payments.
 *
 * All API calls run on a background coroutine and update mutableStateOf fields
 * so the Compose UI reacts automatically. Exceptions are caught to prevent
 * Kotlin/Native thread-state crashes.
 */
class OpportunitiesViewModel(
    private val api: ConnectMarketplaceApi,
    private val tokenManager: ConnectIdTokenManager
) {
    var opportunities by mutableStateOf<List<Opportunity>>(emptyList())
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var selectedOpportunity by mutableStateOf<Opportunity?>(null)

    // Detail view state
    var learnProgress by mutableStateOf<LearnProgressDetail?>(null)
    var deliveryProgress by mutableStateOf<DeliveryProgressDetail?>(null)

    private val scope = CoroutineScope(Dispatchers.Default)

    fun clearError() { errorMessage = null }

    /**
     * Fetch the list of available opportunities for the signed-in user.
     */
    fun loadOpportunities() {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    isLoading = false
                    return@launch
                }
                val result = api.getOpportunities(token)
                result.fold(
                    onSuccess = { list -> opportunities = list },
                    onFailure = { errorMessage = "Failed to load opportunities: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Load error: ${e::class.simpleName}: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Select an opportunity to view its detail screen.
     */
    fun selectOpportunity(opp: Opportunity) {
        selectedOpportunity = opp
        // Reset detail sub-state when switching opportunities
        learnProgress = null
        deliveryProgress = null
        errorMessage = null
    }

    /**
     * Clear the selected opportunity and return to the list.
     */
    fun clearSelection() {
        selectedOpportunity = null
        learnProgress = null
        deliveryProgress = null
        errorMessage = null
    }

    /**
     * POST to claim the opportunity, then refresh the list so claimed status updates.
     */
    fun claimOpportunity(id: Int) {
        isLoading = true
        errorMessage = null
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken()
                if (token == null) {
                    errorMessage = "Not signed in to ConnectID"
                    isLoading = false
                    return@launch
                }
                val result = api.claimOpportunity(token, id)
                result.fold(
                    onSuccess = {
                        // Refresh list to reflect new claimed status
                        val refreshResult = api.getOpportunities(token)
                        refreshResult.fold(
                            onSuccess = { list ->
                                opportunities = list
                                // Update selectedOpportunity with fresh data
                                selectedOpportunity = list.find { it.id == id }
                                    ?: selectedOpportunity
                            },
                            onFailure = { /* list stale but claim succeeded */ }
                        )
                    },
                    onFailure = { errorMessage = "Failed to claim opportunity: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Claim error: ${e::class.simpleName}: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Load learning progress for a claimed opportunity.
     */
    fun loadLearnProgress(opportunityId: Int) {
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken() ?: return@launch
                val result = api.getLearnProgress(token, opportunityId)
                result.fold(
                    onSuccess = { detail -> learnProgress = detail },
                    onFailure = { errorMessage = "Failed to load learn progress: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Learn progress error: ${e::class.simpleName}: ${e.message}"
            }
        }
    }

    /**
     * Load delivery progress for a claimed opportunity.
     */
    fun loadDeliveryProgress(opportunityId: Int) {
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken() ?: return@launch
                val result = api.getDeliveryProgress(token, opportunityId)
                result.fold(
                    onSuccess = { detail -> deliveryProgress = detail },
                    onFailure = { errorMessage = "Failed to load delivery progress: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Delivery progress error: ${e::class.simpleName}: ${e.message}"
            }
        }
    }

    /**
     * Confirm a pending payment, then refresh delivery progress which includes payments.
     */
    fun confirmPayment(paymentId: Int) {
        scope.launch {
            try {
                val token = tokenManager.getConnectIdToken() ?: return@launch
                val result = api.confirmPayment(token, paymentId)
                result.fold(
                    onSuccess = {
                        // Refresh delivery progress so the confirmed status is reflected
                        val oppId = selectedOpportunity?.id ?: return@fold
                        val refreshResult = api.getDeliveryProgress(token, oppId)
                        refreshResult.fold(
                            onSuccess = { detail -> deliveryProgress = detail },
                            onFailure = { /* list stale but confirm succeeded */ }
                        )
                    },
                    onFailure = { errorMessage = "Failed to confirm payment: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Confirm payment error: ${e::class.simpleName}: ${e.message}"
            }
        }
    }
}
