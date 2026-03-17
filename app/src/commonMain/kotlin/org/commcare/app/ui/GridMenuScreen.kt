package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.commcare.app.viewmodel.MenuItem
import org.commcare.app.viewmodel.MenuViewModel

/**
 * Grid-style menu display. Renders menu items as icon+label cards in a grid layout.
 * Used when the suite defines style="grid" on a menu element.
 */
@Composable
fun GridMenuScreen(
    viewModel: MenuViewModel,
    columnCount: Int = 3,
    onBack: (() -> Unit)? = null
) {
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
                color = MaterialTheme.colorScheme.primary
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(columnCount),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(viewModel.menuItems) { item ->
                    GridMenuItem(item = item, onClick = { viewModel.selectItem(item) })
                }
            }
        }
    }
}

@Composable
fun GridMenuItem(item: MenuItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { onClick() }
            .semantics { contentDescription = item.displayText },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon placeholder — actual icon loading from imageUri requires platform support
            if (item.imageUri != null) {
                Text(
                    text = if (item.isMenu) "\uD83D\uDCC1" else "\uD83D\uDCCB",
                    style = MaterialTheme.typography.headlineLarge
                )
            } else {
                Text(
                    text = if (item.isMenu) "\uD83D\uDCC1" else "\uD83D\uDCCB",
                    style = MaterialTheme.typography.headlineLarge
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.displayText,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
