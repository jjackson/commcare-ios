package org.commcare.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private const val PIN_LENGTH = 6

/**
 * Numeric PIN pad screen for quick login.
 *
 * Shows an app banner, 6-dot PIN indicator, a 3x4 numeric keypad,
 * and a "Forgot PIN?" link that falls back to password mode.
 * Auto-submits when all 6 digits have been entered.
 */
@Composable
fun PinEntryScreen(
    appName: String?,
    onPinEntered: (String) -> Unit,
    onForgotPin: () -> Unit,
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    var pin by remember { mutableStateOf("") }

    // Reset PIN when an error is shown (incorrect PIN)
    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            pin = ""
        }
    }

    // Auto-submit when PIN reaches full length
    LaunchedEffect(pin) {
        if (pin.length == PIN_LENGTH) {
            onPinEntered(pin)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App banner
        Text(
            text = appName ?: "CommCare",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Enter PIN",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(24.dp))

        // PIN dots
        PinDots(enteredCount = pin.length)

        Spacer(modifier = Modifier.height(16.dp))

        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("pin_error")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        } else {
            Spacer(modifier = Modifier.height(16.dp))

            // Numeric keypad
            PinKeypad(
                onDigit = { digit ->
                    if (pin.length < PIN_LENGTH) {
                        pin += digit
                    }
                },
                onBackspace = {
                    if (pin.isNotEmpty()) {
                        pin = pin.dropLast(1)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Forgot PIN link
        TextButton(
            onClick = onForgotPin,
            modifier = Modifier.testTag("forgot_pin_button")
        ) {
            Text("Forgot PIN?")
        }
    }
}

/**
 * Row of 6 dots showing how many PIN digits have been entered.
 * Filled dots = entered, empty dots = remaining.
 */
@Composable
private fun PinDots(enteredCount: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag("pin_dots")
    ) {
        repeat(PIN_LENGTH) { index ->
            val filled = index < enteredCount
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant
                    )
            )
        }
    }
}

/**
 * 3x4 numeric keypad:
 * ```
 * [1] [2] [3]
 * [4] [5] [6]
 * [7] [8] [9]
 *     [0] [<]
 * ```
 */
@Composable
private fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.testTag("pin_keypad")
    ) {
        // Row 1: 1 2 3
        KeypadRow(digits = listOf('1', '2', '3'), onDigit = onDigit)
        // Row 2: 4 5 6
        KeypadRow(digits = listOf('4', '5', '6'), onDigit = onDigit)
        // Row 3: 7 8 9
        KeypadRow(digits = listOf('7', '8', '9'), onDigit = onDigit)
        // Row 4: [empty] 0 [backspace]
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Empty space on the left
            Spacer(modifier = Modifier.width(72.dp))

            // 0 key
            KeypadButton(label = "0", onClick = { onDigit('0') })

            // Backspace key
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onBackspace() }
                    .testTag("pin_backspace"),
                contentAlignment = Alignment.Center
            ) {
                // Unicode left-pointing triangle as backspace icon
                Text(
                    text = "\u232B",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(
    digits: List<Char>,
    onDigit: (Char) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (digit in digits) {
            KeypadButton(label = digit.toString(), onClick = { onDigit(digit) })
        }
    }
}

@Composable
private fun KeypadButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 72.dp, minHeight = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }
            .testTag("pin_key_$label"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}
