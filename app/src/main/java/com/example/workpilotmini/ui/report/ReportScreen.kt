package com.example.workpilotmini.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.Team
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replaces the old separate Reminders + Visit History tiles with one report: daily /
 * weekly / monthly counts for attendance, visits and reminders, plus (for the real team
 * admin viewing the whole team) a per-member breakdown table.
 *
 * When [focusUid] is set (opened from the Members section) the whole screen scopes down
 * to just that one member's numbers, regardless of who's viewing it.
 *
 * [team] is optional and only used to build the whole-team breakdown table (see below);
 * pass null for solo users or when it isn't available yet.
 */
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    ownerId: String,
    isSolo: Boolean,
    uid: String,
    isAdmin: Boolean,
    focusUid: String? = null,
    focusName: String? = null,
    team: Team? = null
) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val isTeamWide = focusUid == null && isAdmin && !isSolo

    // Current roster names (uid -> name), fetched once per team so the whole-team
    // breakdown table always reflects who's actually on the team right now — see
    // ReportViewModel.load for why this matters (zero-activity members + removed
    // members).
    val teamRepo = remember { TeamRepository() }
    var teamMemberPairs by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(team?.teamId, team?.memberUids) {
        if (isTeamWide && team != null) {
            val result = teamRepo.getTeamMembers(team.teamId, team.memberUids)
            teamMemberPairs = result.getOrDefault(emptyList()).map { it.uid to it.name }
        } else {
            teamMemberPairs = emptyList()
        }
    }

    LaunchedEffect(ownerId, uid, focusUid, state.period, teamMemberPairs) {
        viewModel.load(ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMemberPairs)
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            focusName?.let { Strings.reportHeaderForMember(it) }
                ?: if (isTeamWide) Strings.reportHeaderAdmin() else Strings.reportHeaderSelf(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))

        SingleChoiceSegmented(
            selected = state.period,
            onSelect = { viewModel.setPeriod(it, ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMemberPairs) }
        )

        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ReportStatCard(
                icon = Icons.Filled.CheckCircle,
                label = Strings.reportAttendanceLabel(),
                value = state.attendanceCount,
                modifier = Modifier.weight(1f)
            )
            ReportStatCard(
                icon = Icons.Filled.LocationOn,
                label = Strings.reportVisitLabel(),
                value = state.visitCount,
                modifier = Modifier.weight(1f)
            )
            ReportStatCard(
                icon = Icons.Filled.Notifications,
                label = Strings.reportReminderLabel(),
                value = state.reminderCount,
                modifier = Modifier.weight(1f)
            )
        }

        if (isTeamWide && state.memberBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(Strings.reportBreakdownHeader(), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            state.memberBreakdown.forEach { row ->
                ElevatedCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(row.name.ifBlank { Strings.reportUnknownMember() }, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            Strings.reportBreakdownCounts(row.attendanceCount, row.visitCount, row.reminderCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(Strings.reportVisitListHeader(), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        if (state.visits.isEmpty() && !state.isLoading) {
            Text(Strings.noVisits())
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.visits) { visit ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text(visit.leadName, style = MaterialTheme.typography.titleMedium)
                            if (isTeamWide) {
                                Text(
                                    visit.userName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                            if (visit.notes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(visit.notes, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                Strings.visitedOn(dateFormat.format(Date(visit.visitTime))),
                                style = MaterialTheme.typography.bodySmall
                            )
                            visit.nextVisitDate?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Strings.nextVisitLabel(dateFormat.format(Date(it))),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SingleChoiceSegmented(selected: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    val options = listOf(
        ReportPeriod.DAILY to Strings.reportPeriodDaily(),
        ReportPeriod.WEEKLY to Strings.reportPeriodWeekly(),
        ReportPeriod.MONTHLY to Strings.reportPeriodMonthly()
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (period, label) ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(label) },
                leadingIcon = if (selected == period) {
                    { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }
    }
}

@Composable
private fun ReportStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: Int,
    modifier: Modifier = Modifier
) {
    ElevatedCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(4.dp))
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}