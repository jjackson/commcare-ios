package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.DrawerViewModel

/**
 * Content composable for the navigation drawer.
 *
 * Renders the drawer body including profile card, optional Connect sections,
 * app switcher list, About row, and Connect ID sign-in action.
 *
 * Intended to be placed inside a ModalNavigationDrawer in HomeScreen (Task 2).
 */
@Composable
fun NavigationDrawerContent(
    viewModel: DrawerViewModel,
    username: String,
    onSwitchApp: (String) -> Unit,
    onOpportunities: () -> Unit,
    onMessaging: () -> Unit,
    onAbout: () -> Unit,
    onConnectIdAction: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp)
    ) {
        // Profile card at the top
        ProfileCard(
            username = username,
            profileName = viewModel.profileName,
            profilePhone = viewModel.profilePhone
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        // Connect-only rows (visible only when Connect access is enabled)
        if (viewModel.hasConnectAccess) {
            DrawerRow(
                icon = "\uD83D\uDCBC", // briefcase
                label = "Opportunities",
                onClick = onOpportunities
            )
        }

        // App list section
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "CommCare Apps",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        if (viewModel.apps.isEmpty()) {
            Text(
                text = "No apps installed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            viewModel.apps.forEach { app ->
                val isSeated = app.id == viewModel.seatedAppId
                AppDrawerRow(
                    appName = app.displayName,
                    isActive = isSeated,
                    onClick = {
                        if (!isSeated) {
                            onSwitchApp(app.id)
                        }
                        onClose()
                    }
                )
            }
        }

        // Messaging row (Connect only) — shows unread count badge when non-zero
        if (viewModel.hasConnectAccess) {
            Spacer(modifier = Modifier.height(4.dp))
            val unread = viewModel.unreadMessageCount
            DrawerRow(
                icon = "\uD83D\uDCAC", // speech bubble
                label = if (unread > 0) "Messaging ($unread)" else "Messaging",
                onClick = onMessaging
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(4.dp))

        // About row
        DrawerRow(
            icon = "\u2139\uFE0F", // information
            label = "About",
            onClick = onAbout
        )

        // Connect ID action
        DrawerRow(
            icon = "\uD83D\uDD11", // key
            label = if (viewModel.hasConnectAccess) "Sign out of Personal ID" else "Sign in to Personal ID",
            onClick = onConnectIdAction
        )
    }
}

/**
 * A single tappable row inside the navigation drawer.
 * Uses text/emoji icons since material-icons-extended is not a dependency.
 */
@Composable
private fun DrawerRow(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * A row representing an installed app in the drawer app list.
 * Highlights the active/seated app with primary color and a check mark.
 */
@Composable
private fun AppDrawerRow(
    appName: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    val textColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 44.dp)
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (isActive) "\u2713 " else "    ", // checkmark or indent
            style = MaterialTheme.typography.bodyLarge,
            color = textColor
        )
        Text(
            text = appName,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
