package com.example.workpilotmini.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.AppLanguage
import com.example.workpilotmini.localization.Strings

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit,
    onGoToSignUp: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetEmail by remember { mutableStateOf("") }

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = {
                showResetDialog = false
                viewModel.clearResetEmailSent()
            },
            title = { Text(Strings.resetPasswordTitle()) },
            text = {
                Column {
                    if (state.resetEmailSent) {
                        Text(Strings.resetPasswordSent(resetEmail))
                    } else {
                        Text(Strings.resetPasswordPrompt())
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = { Text(Strings.email()) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                if (state.resetEmailSent) {
                    TextButton(onClick = {
                        showResetDialog = false
                        viewModel.clearResetEmailSent()
                    }) { Text(Strings.ok()) }
                } else {
                    TextButton(
                        onClick = { viewModel.sendPasswordReset(resetEmail.trim()) },
                        enabled = !state.isLoading
                    ) { Text(Strings.send()) }
                }
            },
            dismissButton = {
                if (!state.resetEmailSent) {
                    TextButton(onClick = {
                        showResetDialog = false
                        viewModel.clearError()
                    }) { Text(Strings.cancel()) }
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(Strings.appName(), style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = { AppLanguage.toggle() }) {
                Text(Strings.languageToggleLabel())
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(Strings.loginSubtitle(), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(Strings.email()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text(Strings.password()) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(
            Strings.forgotPassword(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.clickable {
                resetEmail = email.trim()
                showResetDialog = true
            }
        )

        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.login(email.trim(), password) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(Strings.login())
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            Text(Strings.noAccountPrefix())
            Text(
                Strings.signUpLink(),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onGoToSignUp)
            )
        }
    }
}
