package com.example.workpilotmini.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.UserProfile
import com.example.workpilotmini.ui.auth.AuthViewModel

/**
 * Profile screen used both as:
 *  - the normal "edit my profile" screen (from a Profile button on the dashboard), and
 *  - the post-signup "please complete your profile" prompt (isPostSignupPrompt = true),
 *    which adds a banner + a "Skip for now" option and calls [onDone] once the user has
 *    either saved or skipped.
 */
@Composable
fun ProfileScreen(
    viewModel: AuthViewModel,
    profile: UserProfile,
    isPostSignupPrompt: Boolean,
    onDone: () -> Unit,
    onGoToTeamSetup: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    // After a successful save while showing the post-signup prompt, move on automatically.
    LaunchedEffect(state.profileSaved) {
        if (state.profileSaved) {
            viewModel.clearProfileSaved()
            if (isPostSignupPrompt) onDone()
        }
    }

    var name by remember { mutableStateOf(profile.name) }
    var mobile by remember { mutableStateOf(profile.mobile) }
    var address by remember { mutableStateOf(profile.address) }

    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPasswordFields by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(Strings.profileTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        if (isPostSignupPrompt) {
            Spacer(Modifier.height(8.dp))
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    Strings.profileCompletePrompt(),
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(Strings.profileBasicInfoHeader(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(Strings.yourName()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = profile.email,
            onValueChange = {},
            label = { Text(Strings.email()) },
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = mobile,
            onValueChange = { mobile = it },
            label = { Text(Strings.mobileNumberOptional()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            label = { Text(Strings.addressOptional()) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { viewModel.updateProfile(name, mobile, address) },
            enabled = !state.isSavingProfile,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isSavingProfile) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(Strings.saveProfileButton())
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(Strings.profilePasswordHeader(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (!showPasswordFields) {
            OutlinedButton(onClick = { showPasswordFields = true }, modifier = Modifier.fillMaxWidth()) {
                Text(Strings.changePasswordButton())
            }
        } else {
            OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text(Strings.newPasswordLabel()) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(Strings.confirmPasswordLabel()) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { viewModel.updatePassword(newPassword, confirmPassword) },
                enabled = !state.isSavingProfile && newPassword.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isSavingProfile) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(Strings.updatePasswordButton())
                }
            }
        }

        // Only shown outside the post-signup prompt (which already routes straight to Team
        // Setup next) and while the user isn't on a team yet — lets them pick a team path
        // right from Profile, same as the initial team-setup screen.
        if (!isPostSignupPrompt && profile.teamId.isBlank()) {
            Spacer(Modifier.height(24.dp))
            Text(Strings.profileTeamHeader(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                if (profile.soloMode) Strings.profileSoloModeActive() else Strings.profileNoTeamYet(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onGoToTeamSetup, modifier = Modifier.fillMaxWidth()) {
                Text(Strings.profileCreateOrJoinTeamButton())
            }
            if (!profile.soloMode) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { viewModel.setSoloMode(onDone = {}) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(Strings.profileUseIndividuallyButton())
                }
            }
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        if (isPostSignupPrompt) {
            Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { viewModel.skipProfileCompletion(); onDone() }) {
                    Text(Strings.skipForNow())
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { viewModel.updateProfile(name, mobile, address) }, enabled = !state.isSavingProfile) {
                    if (state.isSavingProfile) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(Strings.saveAndContinue())
                    }
                }
            }
        } else {
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(Strings.back())
            }
        }
    }
}
