package org.commcare.app.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.commcare.app.model.CommCareAppInfo
import org.commcare.app.model.DeliveryProgressDetail
import org.commcare.app.model.LearnProgressDetail
import org.commcare.app.model.Opportunity
import org.commcare.app.network.ConnectMarketplaceApi

/**
 * Tracks the state of a Connect app download and install operation.
 */
sealed class DownloadState {
    data object Idle : DownloadState()
    data class Downloading(val appName: String) : DownloadState()
    data class Complete(val appName: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

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
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var selectedOpportunity by mutableStateOf<Opportunity?>(null)
        private set

    // Detail view state
    var learnProgress by mutableStateOf<LearnProgressDetail?>(null)
        private set
    var deliveryProgress by mutableStateOf<DeliveryProgressDetail?>(null)
        private set

    // Connect app download state
    var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)
        private set


    /**
     * Cached HQ SSO token obtained after a Connect app install completes.
     * The caller (e.g. LoginViewModel) can use this for auto-login.
     */
    var cachedHqSsoToken by mutableStateOf<String?>(null)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun cancel() { scope.cancel() }

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
                    val detail = tokenManager.lastTokenError ?: "no cached token and refresh failed"
                    errorMessage = "Not signed in to ConnectID ($detail)"
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
    fun claimOpportunity(opportunityUuid: String) {
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
                val result = api.claimOpportunity(token, opportunityUuid)
                result.fold(
                    onSuccess = {
                        // Refresh list to reflect new claimed status
                        val refreshResult = api.getOpportunities(token)
                        refreshResult.fold(
                            onSuccess = { list ->
                                opportunities = list
                                selectedOpportunity = list.find { it.opportunityId == opportunityUuid }
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
     * Start the learn app for an unclaimed opportunity.
     * POSTs to /users/start_learn_app with the opportunity UUID, then refreshes.
     */
    fun startLearning(opportunityId: String) {
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
                val result = api.startLearnApp(token, opportunityId)
                result.fold(
                    onSuccess = {
                        // Refresh list so claimed/learn status updates
                        val refreshResult = api.getOpportunities(token)
                        refreshResult.fold(
                            onSuccess = { list ->
                                opportunities = list
                                selectedOpportunity = list.find { it.opportunityId == opportunityId }
                                    ?: selectedOpportunity
                            },
                            onFailure = { /* list stale but start succeeded */ }
                        )
                    },
                    onFailure = { errorMessage = "Failed to start learning: ${it.message}" }
                )
            } catch (e: Exception) {
                errorMessage = "Start learning error: ${e::class.simpleName}: ${e.message}"
            } finally {
                isLoading = false
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

    // -----------------------------------------------------------------
    // Connect app download + install
    // -----------------------------------------------------------------

    /**
     * Initiate download and install of a Connect app (learn or deliver).
     *
     * Delegates the actual installation to the [onDownloadApp] callback which
     * triggers the standard [AppInstallViewModel] install-progress screen via
     * App.kt's pendingConnectInstall wiring. This method manages the download
     * state tracking and SSO token retrieval.
     *
     * After install completes, retrieves an HQ SSO token for auto-login.
     *
     * @param appInfo   The CommCareAppInfo describing the app to install.
     * @param onComplete Callback with true on success, false on failure.
     */
    fun downloadAndInstallApp(appInfo: CommCareAppInfo, onComplete: (Boolean) -> Unit) {
        val installUrl = appInfo.installUrl
        if (installUrl.isNullOrBlank()) {
            downloadState = DownloadState.Error("No install URL available for ${appInfo.name}")
            onComplete(false)
            return
        }

        downloadState = DownloadState.Downloading(appInfo.name)
        scope.launch {
            try {
                // Attempt to retrieve the HQ SSO token for auto-login after install.
                // The actual app installation is handled externally via the onDownloadApp
                // callback (which goes through App.kt -> AppInstallViewModel), but we
                // pre-fetch the SSO token so it's ready when the install completes.
                val hqSsoToken = try {
                    tokenManager.getHqSsoToken(
                        hqUrl = "https://www.commcarehq.org",
                        domain = appInfo.ccDomain,
                        hqUsername = tokenManager.getStoredUsername() ?: ""
                    )
                } catch (_: Exception) {
                    null
                }

                cachedHqSsoToken = hqSsoToken
                downloadState = DownloadState.Complete(appInfo.name)
                onComplete(true)
            } catch (e: Exception) {
                downloadState = DownloadState.Error(
                    "Download failed: ${e.message ?: e::class.simpleName}"
                )
                onComplete(false)
            }
        }
    }

    /** Reset download state to idle. */
    fun resetDownloadState() {
        downloadState = DownloadState.Idle
        cachedHqSsoToken = null
    }

    /**
     * Returns true if this opportunity's learning phase is considered complete.
     * Learning is complete when:
     * - There is no learn app (learning not required), OR
     * - All learn modules have been completed
     */
    fun isLearningComplete(opp: Opportunity): Boolean {
        if (opp.learnApp == null) return true
        val summary = opp.learnProgress ?: return false
        return summary.totalModules > 0 && summary.completedModules >= summary.totalModules
    }

    /**
     * Returns true if this opportunity is ready for delivery.
     * Delivery is ready when:
     * - The opportunity is claimed, AND
     * - Learning is complete (or no learn app required)
     */
    fun isReadyForDelivery(opp: Opportunity): Boolean {
        return opp.isClaimed && isLearningComplete(opp)
    }
}
