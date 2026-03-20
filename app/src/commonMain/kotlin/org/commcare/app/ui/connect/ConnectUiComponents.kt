package org.commcare.app.ui.connect

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Android Connect primary color (indigo). */
val ConnectIndigo = Color(0xFF5C6BC0)
val ConnectIndigoLight = Color(0xFFE8EAF6)
val ConnectTeal = Color(0xFF009688)

/**
 * Standard Connect top bar: back arrow, "Connect" title, notification + chat icons.
 */
@Composable
fun ConnectTopBar(
    onBack: () -> Unit,
    onNotifications: (() -> Unit)? = null,
    onMessaging: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "\u2190",
            style = MaterialTheme.typography.headlineSmall,
            color = ConnectIndigo,
            modifier = Modifier
                .clickable { onBack() }
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .padding(8.dp)
        )
        Text(
            text = "Connect",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = ConnectIndigo,
            modifier = Modifier.weight(1f)
        )
        if (onNotifications != null) {
            Text(
                text = "\uD83D\uDD14",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clickable { onNotifications() }
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(8.dp)
            )
        }
        if (onMessaging != null) {
            Text(
                text = "\uD83D\uDCAC",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .clickable { onMessaging() }
                    .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                    .padding(8.dp)
            )
        }
    }
}

/** Pill-shaped outlined button matching Android "Review" / "View Info". */
@Composable
fun ConnectPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false
) {
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = if (filled) Color.White else ConnectIndigo,
        modifier = modifier
            .clip(shape)
            .then(
                if (filled) Modifier.background(ConnectIndigo, shape)
                else Modifier.border(1.dp, ConnectIndigo, shape)
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** Warning banner with triangle icon. */
@Composable
fun ConnectWarningBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF3E0))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "\u26A0", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF795548)
        )
    }
}

/** Circular progress matching Android's blue circle with percentage. */
@Composable
fun CircularProgressDisplay(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val percentage = (progress * 100).toInt()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(ConnectIndigoLight)
            .border(8.dp, ConnectIndigo, CircleShape)
    ) {
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = ConnectIndigo
        )
    }
}

/** Format "YYYY-MM-DD" to "DD Mon, YYYY" matching Android. */
fun formatDateForDisplay(dateStr: String?): String? {
    if (dateStr == null) return null
    return try {
        val parts = dateStr.split("-")
        if (parts.size != 3) return dateStr
        val year = parts[0]
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        val monthNames = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        )
        val monthName = monthNames.getOrElse(month - 1) { "???" }
        val dayStr = if (day < 10) "0$day" else "$day"
        "$dayStr $monthName, $year"
    } catch (_: Exception) {
        dateStr
    }
}

/** Shared error display used across registration wizard steps. */
@Composable
fun ErrorDisplay(errorMessage: String?, onDismiss: () -> Unit) {
    if (errorMessage != null) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = errorMessage,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = onDismiss) {
            Text("Dismiss")
        }
    }
}
