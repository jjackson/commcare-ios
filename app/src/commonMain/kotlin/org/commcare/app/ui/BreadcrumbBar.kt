package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * Displays a breadcrumb trail (Home > Module > Form) with tappable segments
 * to navigate back to any level.
 */
@Composable
fun BreadcrumbBar(
    segments: List<BreadcrumbSegment>,
    onSegmentClick: (Int) -> Unit
) {
    if (segments.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for ((index, segment) in segments.withIndex()) {
            if (index > 0) {
                Text(
                    text = " > ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            val isLast = index == segments.lastIndex
            Text(
                text = segment.label,
                style = MaterialTheme.typography.bodySmall,
                color = if (isLast) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.primary,
                modifier = if (!isLast) Modifier.clickable { onSegmentClick(index) }
                    .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                    .semantics { contentDescription = "Navigate to ${segment.label}" }
                else Modifier
            )
        }
    }
}

data class BreadcrumbSegment(
    val label: String,
    val menuId: String? = null
)
