package org.commcare.app.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.commcare.app.network.ConnectMarketplaceApi
import org.commcare.app.viewmodel.ConnectIdTokenManager
import org.commcare.app.viewmodel.MessagingViewModel
import org.commcare.app.viewmodel.OpportunitiesViewModel

/**
 * Top-level Connect marketplace navigator.
 *
 * Owns the [OpportunitiesViewModel] and [MessagingViewModel] instances and
 * manages in-screen navigation between:
 *   - Opportunities list
 *   - Opportunity detail
 *   - Messaging thread list
 *   - Single message thread
 *
 * The [initialTab] parameter lets callers deep-link to "messaging" directly
 * (e.g. when the user taps Messaging in the navigation drawer).
 */
@Composable
fun ConnectScreen(
    api: ConnectMarketplaceApi,
    tokenManager: ConnectIdTokenManager,
    onBack: () -> Unit,
    initialTab: String = "opportunities"
) {
    val opportunitiesViewModel = remember { OpportunitiesViewModel(api, tokenManager) }
    val messagingViewModel = remember { MessagingViewModel(api, tokenManager) }

    var currentScreen by remember { mutableStateOf(initialTab) }

    when (currentScreen) {
        "opportunities" -> OpportunitiesListScreen(
            viewModel = opportunitiesViewModel,
            onBack = onBack,
            onOpportunitySelected = { opp ->
                opportunitiesViewModel.selectOpportunity(opp)
                currentScreen = "detail"
            },
            onMessaging = { currentScreen = "messaging" }
        )

        "detail" -> OpportunityDetailScreen(
            viewModel = opportunitiesViewModel,
            onBack = {
                opportunitiesViewModel.clearSelection()
                currentScreen = "opportunities"
            }
        )

        "messaging" -> MessagingScreen(
            viewModel = messagingViewModel,
            onBack = { currentScreen = "opportunities" },
            onThreadSelected = { thread ->
                messagingViewModel.selectThread(thread)
                currentScreen = "thread"
            }
        )

        "thread" -> MessageThreadScreen(
            viewModel = messagingViewModel,
            onBack = {
                messagingViewModel.clearThread()
                currentScreen = "messaging"
            }
        )

        else -> OpportunitiesListScreen(
            viewModel = opportunitiesViewModel,
            onBack = onBack,
            onOpportunitySelected = { opp ->
                opportunitiesViewModel.selectOpportunity(opp)
                currentScreen = "detail"
            },
            onMessaging = { currentScreen = "messaging" }
        )
    }
}
