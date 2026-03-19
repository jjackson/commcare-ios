package org.commcare.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commcare.core.interfaces.HttpRequest
import org.commcare.core.interfaces.createHttpClient

/** A single app entry returned by the HQ app list endpoint. */
data class HqAppEntry(
    val name: String,
    val domain: String,
    val profileUrl: String
)

private sealed class ListState {
    object Idle : ListState()
    object Loading : ListState()
    data class Success(val apps: List<HqAppEntry>) : ListState()
    data class Error(val message: String) : ListState()
}

/**
 * Screen that authenticates with CommCare HQ and shows available apps for installation.
 *
 * The user enters their HQ credentials, taps "Sign In", and sees a list of apps. Tapping an app
 * calls [onAppSelected] with that app's media-profile URL.
 *
 * @param onAppSelected  called with the profile URL when the user selects an app
 * @param onNavigateBack called when the user taps "← Back"
 */
@Composable
fun InstallFromListScreen(
    onAppSelected: (profileUrl: String) -> Unit,
    onNavigateBack: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var listState by remember { mutableStateOf<ListState>(ListState.Idle) }

    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val httpClient = remember { createHttpClient() }

    fun signIn() {
        if (username.isBlank() || password.isBlank()) return
        focusManager.clearFocus()
        listState = ListState.Loading

        scope.launch {
            val result = withContext(Dispatchers.Default) {
                fetchAppList(httpClient, username, password)
            }
            listState = result
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Back button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.testTag("back_button")
            ) {
                Text("← Back")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Install from App List",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("list_username_field"),
            singleLine = true,
            enabled = listState !is ListState.Loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("list_password_field"),
            singleLine = true,
            enabled = listState !is ListState.Loading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { signIn() })
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { signIn() },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sign_in_button"),
            enabled = username.isNotBlank() && password.isNotBlank() && listState !is ListState.Loading
        ) {
            Text("Sign In")
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val state = listState) {
            is ListState.Idle -> { /* nothing yet */ }

            is ListState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is ListState.Error -> {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            is ListState.Success -> {
                if (state.apps.isEmpty()) {
                    Text(
                        text = "No apps found for this account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = "Select an app to install:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(state.apps) { app ->
                            AppListItem(
                                app = app,
                                onClick = { onAppSelected(app.profileUrl) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(app: HqAppEntry, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Text(
            text = app.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = app.domain,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------------------
// Network + parsing helpers (pure, no Compose)
// ---------------------------------------------------------------------------

private val HQ_HOSTS = listOf(
    "https://www.commcarehq.org",
    "https://india.commcarehq.org"
)
private const val APP_LIST_PATH = "/phone/list_apps"

/**
 * Tries each HQ host in order and returns the first successful app list, or an error if all fail.
 */
private fun fetchAppList(
    httpClient: org.commcare.core.interfaces.PlatformHttpClient,
    username: String,
    password: String
): ListState {
    val authHeader = "Basic " + encodeBasicAuth(username, password)
    var lastError = "Unable to connect to CommCare HQ"

    for (host in HQ_HOSTS) {
        try {
            val response = httpClient.execute(
                HttpRequest(
                    url = "$host$APP_LIST_PATH",
                    method = "GET",
                    headers = mapOf("Authorization" to authHeader)
                )
            )
            when {
                response.code in 200..299 -> {
                    val body = response.body ?: return ListState.Error("Empty response from server")
                    val apps = parseAppListXml(body.decodeToString())
                    return ListState.Success(apps)
                }
                response.code == 401 -> return ListState.Error("Invalid username or password")
                response.code == 403 -> return ListState.Error("Access denied. Check your credentials.")
                response.code == 404 -> {
                    lastError = "App list not available on $host (404)"
                    continue // try next host
                }
                else -> {
                    lastError = "Server error (${response.code}) on $host"
                    continue
                }
            }
        } catch (e: Exception) {
            lastError = "Connection failed: ${e.message}"
            // try next host
        }
    }
    return ListState.Error(lastError)
}

/**
 * Parses the HQ app list XML response.
 *
 * Expected format:
 * ```xml
 * <apps>
 *   <app domain="myproject" name="My App" profile="https://.../media_profile.ccpr"/>
 * </apps>
 * ```
 *
 * Uses simple string parsing so it works in commonMain without an XML library dependency.
 */
internal fun parseAppListXml(xml: String): List<HqAppEntry> {
    val apps = mutableListOf<HqAppEntry>()

    // Find all <app .../> or <app ...> tags
    var searchFrom = 0
    while (true) {
        val tagStart = xml.indexOf("<app ", searchFrom)
        if (tagStart == -1) break

        val tagEnd = xml.indexOf('>', tagStart)
        if (tagEnd == -1) break

        val tagContent = xml.substring(tagStart, tagEnd + 1)
        searchFrom = tagEnd + 1

        val name = extractAttr(tagContent, "name") ?: continue
        val domain = extractAttr(tagContent, "domain") ?: continue
        val profile = extractAttr(tagContent, "profile") ?: continue

        apps.add(HqAppEntry(name = name, domain = domain, profileUrl = profile))
    }
    return apps
}

/** Extracts the value of a named XML attribute from a tag string. */
private fun extractAttr(tag: String, attrName: String): String? {
    // Match: attrName="value" or attrName='value'
    for (quote in listOf('"', '\'')) {
        val prefix = "$attrName=$quote"
        val start = tag.indexOf(prefix)
        if (start == -1) continue
        val valueStart = start + prefix.length
        val valueEnd = tag.indexOf(quote, valueStart)
        if (valueEnd == -1) continue
        return tag.substring(valueStart, valueEnd)
    }
    return null
}

private fun encodeBasicAuth(user: String, pass: String): String {
    val raw = "$user:$pass"
    return base64Encode(raw.encodeToByteArray())
}

private fun base64Encode(data: ByteArray): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val sb = StringBuilder()
    var i = 0
    while (i < data.size) {
        val b0 = data[i].toInt() and 0xFF
        val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
        sb.append(chars[(b0 shr 2) and 0x3F])
        sb.append(chars[((b0 shl 4) or (b1 shr 4)) and 0x3F])
        if (i + 1 < data.size) {
            sb.append(chars[((b1 shl 2) or (b2 shr 6)) and 0x3F])
        } else {
            sb.append('=')
        }
        if (i + 2 < data.size) {
            sb.append(chars[b2 and 0x3F])
        } else {
            sb.append('=')
        }
        i += 3
    }
    return sb.toString()
}
