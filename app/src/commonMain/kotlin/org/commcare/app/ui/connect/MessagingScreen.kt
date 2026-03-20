package org.commcare.app.ui.connect

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.commcare.app.model.MessageThread
import org.commcare.app.viewmodel.MessagingViewModel

/**
 * Displays the list of message threads for the Connect messaging hub.
 *
 * If the user has not yet consented to messaging a prompt is shown with an
 * "Enable" button that calls [viewModel.updateConsent]. Once consented, a
 * scrollable list of thread cards is rendered, each showing the participant
 * name, last message preview, date, and an unread badge when applicable.
 *
 * Tapping a thread calls [viewModel.selectThread] and triggers
 * [onThreadSelected].
 */
@Composable
fun MessagingScreen(
    viewModel: MessagingViewModel,
    onThreadSelected: (MessageThread) -> Unit,
    onBack: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.loadThreads()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .clickable { onBack() }
                    .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                    .padding(end = 8.dp)
                    .testTag("messaging_back_button")
            )
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            if (viewModel.unreadCount > 0) {
                Text(
                    text = "${viewModel.unreadCount} unread",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        HorizontalDivider()

        // Loading indicator
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
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
            if (!viewModel.hasConsented) {
                // Consent prompt
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Enable messaging to communicate with coordinators",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.updateConsent() },
                            modifier = Modifier.testTag("enable_messaging_button")
                        ) {
                            Text("Enable")
                        }
                    }
                }
            } else if (viewModel.threads.isEmpty() && viewModel.errorMessage == null) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.threads) { thread ->
                        MessageThreadCard(
                            thread = thread,
                            onClick = {
                                viewModel.selectThread(thread)
                                onThreadSelected(thread)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageThreadCard(thread: MessageThread, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
            .testTag("thread_card_${thread.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Main content
            Column(modifier = Modifier.weight(1f)) {
                // Participant name (bold)
                Text(
                    text = thread.participantName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Last message preview
                Text(
                    text = thread.lastMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Date
                Text(
                    text = thread.lastMessageDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Unread badge
            if (thread.unreadCount > 0) {
                Surface(
                    modifier = Modifier.size(28.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = thread.unreadCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
