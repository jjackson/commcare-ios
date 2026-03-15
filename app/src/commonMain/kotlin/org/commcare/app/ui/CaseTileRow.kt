package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Data class representing a resolved tile field ready for display.
 */
data class TileFieldData(
    val value: String,
    val gridX: Int,
    val gridY: Int,
    val gridWidth: Int,
    val gridHeight: Int,
    val fontSize: String? = null,
    val horizontalAlign: String? = null,
    val verticalAlign: String? = null,
    val isImage: Boolean = false,
    val headerText: String? = null,
    val showBorder: Boolean = false,
    val showShading: Boolean = false
)

/**
 * Configuration for how the case tile grid should be rendered.
 */
data class TileConfig(
    val fields: List<TileFieldData>,
    val maxWidth: Int,
    val maxHeight: Int,
    val numPerRow: Int = 1
)

/**
 * Renders a case as a tile card with fields positioned in a grid layout.
 * Grid coordinates from DetailField define relative positions within the tile.
 */
@Composable
fun CaseTileRow(
    tileConfig: TileConfig,
    fields: List<TileFieldData>,
    onClick: () -> Unit
) {
    val cellWidth = 80.dp
    val cellHeight = 32.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cellHeight * tileConfig.maxHeight)
                .padding(8.dp)
        ) {
            for (field in fields) {
                if (field.value.isBlank() && !field.isImage) continue

                val xOffset = cellWidth * field.gridX
                val yOffset = cellHeight * field.gridY
                val fieldWidth = cellWidth * field.gridWidth
                val fieldHeight = cellHeight * field.gridHeight

                val hAlign = when (field.horizontalAlign) {
                    "center" -> Alignment.CenterStart
                    "right" -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                }

                val textAlign = when (field.horizontalAlign) {
                    "center" -> TextAlign.Center
                    "right" -> TextAlign.End
                    else -> TextAlign.Start
                }

                val fontSize = when (field.fontSize) {
                    "large" -> 18.sp
                    "small" -> 12.sp
                    "extra-small" -> 10.sp
                    "extra-large" -> 22.sp
                    else -> 14.sp
                }

                Box(
                    modifier = Modifier
                        .width(fieldWidth)
                        .height(fieldHeight)
                        .padding(start = xOffset, top = yOffset),
                    contentAlignment = hAlign
                ) {
                    if (field.isImage) {
                        // Image fields show a placeholder — actual image loading requires
                        // platform-specific implementation
                        Text(
                            text = "[img]",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = field.value,
                            fontSize = fontSize,
                            textAlign = textAlign,
                            maxLines = if (field.gridHeight > 1) field.gridHeight else 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Header row for tile view showing column headers.
 */
@Composable
fun CaseTileHeader(tileConfig: TileConfig) {
    val cellWidth = 80.dp
    val hasHeaders = tileConfig.fields.any { !it.headerText.isNullOrBlank() }
    if (!hasHeaders) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 4.dp)
    ) {
        for (field in tileConfig.fields) {
            val header = field.headerText ?: continue
            if (header.isBlank()) continue

            val xOffset = cellWidth * field.gridX

            Box(
                modifier = Modifier
                    .width(cellWidth * field.gridWidth)
                    .padding(start = xOffset)
            ) {
                Text(
                    text = header,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}
