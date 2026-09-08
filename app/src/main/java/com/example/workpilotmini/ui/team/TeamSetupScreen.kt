package com.example.workpilotmini.ui.team

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.Team

private enum class TeamTab { CREATE, JOIN }

@Composable
fun TeamSetupScreen(
    viewModel: TeamViewModel,
    myUid: String,
    onTeamReady: (teamId: String) -> Unit,
    onUseIndividually: (() -> Unit)? = null,
    isSoloProcessing: Boolean = false,
    soloErrorMessage: String? = null
) {
    val state by viewModel.state.collectAsState()
    var tab by remember { mutableStateOf(TeamTab.CREATE) }

    var teamName by remember { mutableStateOf("") }
    var companyName by remember { mutableStateOf("") }
    var teamLocation by remember { mutableStateOf("") }
    var teamSize by remember { mutableStateOf(Team.MAX_SIZE.toFloat()) }
    var joinCode by remember { mutableStateOf("") }

    LaunchedEffect(state.done, state.team) {
        if (state.done) state.team?.let { onTeamReady(it.teamId) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(Strings.teamSetupTitle(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            Strings.teamSetupSubtitle(),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))

        TabRow(selectedTabIndex = tab.ordinal) {
            Tab(selected = tab == TeamTab.CREATE, onClick = { tab = TeamTab.CREATE; viewModel.clearError() }) {
                Text(Strings.createTeamTab(), modifier = Modifier.padding(12.dp))
            }
            Tab(selected = tab == TeamTab.JOIN, onClick = { tab = TeamTab.JOIN; viewModel.clearError() }) {
                Text(Strings.joinTeamTab(), modifier = Modifier.padding(12.dp))
            }
        }

        Spacer(Modifier.height(20.dp))

        if (tab == TeamTab.CREATE) {
            OutlinedTextField(
                value = teamName,
                onValueChange = { teamName = it },
                label = { Text(Strings.teamNameLabel()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = { Text(Strings.companyNameOptional()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = teamLocation,
                onValueChange = { teamLocation = it },
                label = { Text(Strings.teamLocationOptional()) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            Text(Strings.teamSizeLabel(teamSize.toInt(), Team.MIN_SIZE, Team.MAX_SIZE))
            Slider(
                value = teamSize,
                onValueChange = { teamSize = it },
                valueRange = Team.MIN_SIZE.toFloat()..Team.MAX_SIZE.toFloat(),
                steps = (Team.MAX_SIZE - Team.MIN_SIZE - 1).coerceAtLeast(0)
            )

            state.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.createTeam(teamName, teamSize.toInt(), myUid, companyName, teamLocation) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(Strings.createTeamButton())
                }
            }
        } else {
            OutlinedTextField(
                value = joinCode,
                onValueChange = { joinCode = it.uppercase() },
                label = { Text(Strings.inviteCodeFieldLabel()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            state.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { viewModel.joinTeam(joinCode, myUid) },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(Strings.joinTeamButton())
                }
            }
        }

        if (onUseIndividually != null) {
            Spacer(Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text(Strings.useIndividuallyHint(), style = MaterialTheme.typography.bodySmall)
            soloErrorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUseIndividually,
                enabled = !isSoloProcessing,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSoloProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(Strings.profileUseIndividuallyButton())
                }
            }
        }
    }
}
