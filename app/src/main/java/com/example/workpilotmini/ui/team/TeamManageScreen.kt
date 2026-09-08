package com.example.workpilotmini.ui.team

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.UserProfile

@Composable
fun TeamManageScreen(
    viewModel: TeamViewModel,
    team: Team,
    myUid: String,
    isAdmin: Boolean,
    onOpenMemberReport: (uid: String, name: String) -> Unit = { _, _ -> }
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var memberPendingDelete by remember { mutableStateOf<UserProfile?>(null) }

    LaunchedEffect(team.teamId) { viewModel.loadMembers(team) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(team.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (team.companyName.isNotBlank()) {
            Text(team.companyName, style = MaterialTheme.typography.bodyMedium)
        }
        if (team.location.isNotBlank()) {
            Text(Strings.teamLocationInline(team.location), style = MaterialTheme.typography.bodySmall)
        }
        Text(
            Strings.memberCount(team.memberUids.size, team.maxSize),
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(8.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text(Strings.inviteCodeOptional(), style = MaterialTheme.typography.labelMedium)
                Text(team.inviteCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    Strings.inviteCodeShareHint(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (isAdmin) {
            // NOTE: "create member account directly" (admin sets email + password) has been
            // removed — the only way to add someone now is by email search below, which
            // requires them to have already signed up themselves.
            Spacer(Modifier.height(20.dp))
            Text(Strings.addByEmailSectionTitle(), style = MaterialTheme.typography.titleMedium)
            Text(
                Strings.addByEmailSectionHint(),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("member@example.com") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { viewModel.addMemberByEmail(team, email, myUid) },
                    enabled = !state.isAddingMember
                ) {
                    if (state.isAddingMember) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(Strings.add())
                    }
                }
            }

            state.addMemberSuccessMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
                LaunchedEffect(it) { email = "" }
            }
            state.memberActionMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.primary)
            }
            state.errorMessage?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(Strings.membersHeader(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 2000.dp)
        ) {
            items(state.members) { member ->
                ElevatedCard(
                    onClick = { if (isAdmin) onOpenMemberReport(member.uid, member.name.ifBlank { member.email }) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(member.name)
                                Text(member.email, style = MaterialTheme.typography.bodySmall)
                                if (member.mobile.isNotBlank()) {
                                    Text(member.mobile, style = MaterialTheme.typography.bodySmall)
                                }
                                if (!member.isActive) {
                                    Text(
                                        Strings.deactivatedLabel(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                                if (isAdmin) {
                                    Text(
                                        Strings.viewReportHint(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Icon(
                                if (member.role == "admin") Icons.Filled.AdminPanelSettings else Icons.Filled.Person,
                                contentDescription = member.role
                            )
                        }

                        // Admin actions — never shown for the admin's own row.
                        if (isAdmin && member.uid != myUid) {
                            Spacer(Modifier.height(10.dp))
                            val busy = state.memberActionInProgressUid == member.uid
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { viewModel.setMemberActive(team, member.uid, !member.isActive, myUid) },
                                    enabled = !busy
                                ) {
                                    Text(if (member.isActive) Strings.deactivateAction() else Strings.activateAction())
                                }
                                OutlinedButton(
                                    onClick = { viewModel.removeMember(team, member.uid, myUid) },
                                    enabled = !busy
                                ) {
                                    Text(Strings.removeAction())
                                }
                                OutlinedButton(
                                    onClick = { memberPendingDelete = member },
                                    enabled = !busy,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text(Strings.deleteAction())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    memberPendingDelete?.let { member ->
        AlertDialog(
            onDismissRequest = { memberPendingDelete = null },
            title = { Text(Strings.deleteConfirmTitle()) },
            text = { Text(Strings.deleteConfirmMessage(member.name.ifBlank { member.email })) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteMember(team, member.uid, myUid)
                    memberPendingDelete = null
                }) { Text(Strings.deleteAction()) }
            },
            dismissButton = {
                TextButton(onClick = { memberPendingDelete = null }) { Text(Strings.cancel()) }
            }
        )
    }
}
