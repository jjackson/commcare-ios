package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Configuration for a graph rendered from case detail `<graph>` elements.
 */
data class GraphConfig(
    val title: String,
    val type: GraphType,
    val series: List<GraphSeries>,
    val xLabel: String = "",
    val yLabel: String = ""
)

enum class GraphType { Bar, Line, Pie, Bubble }

data class GraphSeries(
    val label: String,
    val values: List<Pair<String, Double>>
)

/**
 * Generates HTML + JavaScript for rendering a graph using inline SVG.
 * Uses a lightweight approach without external charting libraries for KMP compatibility.
 */
fun generateGraphHtml(config: GraphConfig): String {
    val dataPoints = config.series.firstOrNull()?.values ?: emptyList()
    if (dataPoints.isEmpty()) return "<html><body><p>No data</p></body></html>"

    val maxVal = dataPoints.maxOfOrNull { it.second } ?: 1.0
    val barWidth = 40
    val chartWidth = dataPoints.size * (barWidth + 10) + 40
    val chartHeight = 200

    return buildString {
        append("<html><head><style>")
        append("body { font-family: -apple-system, sans-serif; margin: 16px; }")
        append("h3 { color: #333; }")
        append(".bar { fill: #4285f4; }")
        append(".label { font-size: 11px; fill: #666; }")
        append("</style></head><body>")
        append("<h3>${config.title}</h3>")
        append("<svg width=\"$chartWidth\" height=\"${chartHeight + 40}\">")

        for ((i, point) in dataPoints.withIndex()) {
            val barHeight = if (maxVal > 0) (point.second / maxVal * chartHeight).toInt() else 0
            val x = 20 + i * (barWidth + 10)
            val y = chartHeight - barHeight
            append("<rect class=\"bar\" x=\"$x\" y=\"$y\" width=\"$barWidth\" height=\"$barHeight\" />")
            append("<text class=\"label\" x=\"${x + barWidth / 2}\" y=\"${chartHeight + 15}\" text-anchor=\"middle\">${point.first}</text>")
            append("<text class=\"label\" x=\"${x + barWidth / 2}\" y=\"${y - 4}\" text-anchor=\"middle\">${point.second}</text>")
        }

        append("</svg></body></html>")
    }
}

/**
 * Screen for displaying a graph. In full implementation, this would use a platform WebView.
 * For now, shows graph metadata and a placeholder.
 */
@Composable
fun GraphScreen(
    config: GraphConfig,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.clickable { onBack() }
                    .defaultMinSize(minHeight = 44.dp, minWidth = 44.dp)
                    .padding(end = 8.dp)
            )
            Text(
                text = config.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        HorizontalDivider()

        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Chart Type: ${config.type.name}", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))

            for (series in config.series) {
                Text(
                    text = "${series.label}: ${series.values.size} data points",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Graph rendering requires platform WebView",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
