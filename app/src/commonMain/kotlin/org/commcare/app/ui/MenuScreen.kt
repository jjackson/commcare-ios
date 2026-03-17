package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.MenuItem
import org.commcare.app.viewmodel.MenuViewModel

/**
 * Menu screen that renders either a list or grid layout based on the menu's style attribute.
 * Grid style: renders a configurable-column grid of icon+label cards.
 * List style (default): renders a vertical list of menu items.
 */
@Composable
fun MenuScreen(viewModel: MenuViewModel, onBack: (() -> Unit)? = null) {
    val style = viewModel.menuStyle
    if (style == "grid") {
        GridMenuScreen(viewModel = viewModel, onBack = onBack)
    } else {
        ListMenuScreen(viewModel = viewModel, onBack = onBack)
    }
}

@Composable
private fun ListMenuScreen(viewModel: MenuViewModel, onBack: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Text(
                    text = "<",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable { onBack() }
                        .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                        .semantics { contentDescription = "Go back" }
                        .padding(end = 8.dp)
                )
            }
            Text(
                text = viewModel.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
        }

        HorizontalDivider()

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        if (viewModel.menuItems.isEmpty()) {
            Text(
                text = "No menu items available",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(viewModel.menuItems) { item ->
                    MenuItemRow(item = item, onClick = { viewModel.selectItem(item) })
                }
            }
        }
    }
}

@Composable
fun MenuItemRow(item: MenuItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() }
            .semantics { contentDescription = item.displayText }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.displayText,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (item.isMenu) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = ">",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
