package org.commcare.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.commcare.app.model.ApplicationRecord
import org.commcare.app.state.AppState
import org.commcare.app.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onDemoMode: (() -> Unit)? = null,
    seatedApp: ApplicationRecord? = null,
    allApps: List<ApplicationRecord> = emptyList(),
    onSwitchApp: ((ApplicationRecord) -> Unit)? = null,
    onAppManager: (() -> Unit)? = null,
    onConnectIdLogin: (() -> Unit)? = null
) {
    val isLoggingIn = viewModel.appState is AppState.LoggingIn
    val focusManager = LocalFocusManager.current
    val usernameFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    var appDropdownExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App banner — shown above CommCare title when an app is seated
        if (seatedApp != null) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = seatedApp.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = seatedApp.domain,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Text(
            text = "CommCare",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (seatedApp != null) {
            Spacer(modifier = Modifier.height(8.dp))

            if (allApps.size > 1 && onSwitchApp != null) {
                // Show app name as a tappable dropdown trigger
                Box {
                    TextButton(
                        onClick = { appDropdownExpanded = true },
                        modifier = Modifier.testTag("app_switcher_button")
                    ) {
                        Text(
                            text = "${seatedApp.displayName} ▼",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    DropdownMenu(
                        expanded = appDropdownExpanded,
                        onDismissRequest = { appDropdownExpanded = false }
                    ) {
                        allApps.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.displayName) },
                                onClick = {
                                    appDropdownExpanded = false
                                    onSwitchApp(app)
                                },
                                modifier = Modifier.testTag("app_option_${app.id}")
                            )
                        }
                    }
                }
            } else {
                // Single app — just show as subtitle
                Text(
                    text = seatedApp.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.testTag("app_name_subtitle")
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus).testTag("username_field"),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            enabled = !isLoggingIn
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).testTag("password_field"),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                focusManager.clearFocus()
                if (viewModel.username.isNotBlank() && viewModel.password.isNotBlank()) {
                    viewModel.login()
                }
            }),
            enabled = !isLoggingIn
        )

        if (viewModel.appState is AppState.LoginError) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = (viewModel.appState as AppState.LoginError).message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = { viewModel.resetError() }) {
                Text("Try Again")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (onConnectIdLogin != null && !isLoggingIn) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(Modifier.weight(1f).align(Alignment.CenterVertically))
                Text(
                    " or ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(Modifier.weight(1f).align(Alignment.CenterVertically))
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { onConnectIdLogin.invoke() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login with Connect ID")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (isLoggingIn) {
            CircularProgressIndicator(
                modifier = Modifier
            )
        } else {
            Button(
                onClick = { viewModel.login() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.username.isNotBlank() && viewModel.password.isNotBlank()
            ) {
                Text("Log In")
            }

            if (onDemoMode != null) {
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(
                    onClick = onDemoMode,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enter Demo Mode")
                }
            }

            if (onAppManager != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAppManager) {
                    Text("App Manager", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
