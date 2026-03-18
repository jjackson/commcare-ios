package org.commcare.app.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.commcare.app.state.AppState
import org.commcare.app.viewmodel.LoginViewModel

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onDemoMode: (() -> Unit)? = null
) {
    val isLoggingIn = viewModel.appState is AppState.LoggingIn
    val focusManager = LocalFocusManager.current
    val usernameFocus = remember { FocusRequester() }
    val domainFocus = remember { FocusRequester() }
    val appIdFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }

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
        Text(
            text = "CommCare",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = viewModel.serverUrl,
            onValueChange = { viewModel.serverUrl = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { usernameFocus.requestFocus() }),
            enabled = !isLoggingIn
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.username,
            onValueChange = { viewModel.username = it },
            label = { Text("Username") },
            modifier = Modifier.fillMaxWidth().focusRequester(usernameFocus),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { domainFocus.requestFocus() }),
            enabled = !isLoggingIn
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.domain,
            onValueChange = { viewModel.domain = it },
            label = { Text("Domain") },
            modifier = Modifier.fillMaxWidth().focusRequester(domainFocus),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { appIdFocus.requestFocus() }),
            enabled = !isLoggingIn
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = viewModel.appId,
            onValueChange = { viewModel.appId = it },
            label = { Text("App ID") },
            modifier = Modifier.fillMaxWidth().focusRequester(appIdFocus),
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
            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus),
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
        }
    }
}
