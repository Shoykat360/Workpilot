package com.example.workpilotmini.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.workpilotmini.localization.AppLanguage
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.UserProfile
import com.example.workpilotmini.ui.attendance.AttendanceViewModel
import com.example.workpilotmini.ui.notification.nearReminderCount
import com.example.workpilotmini.ui.theme.AccentBlue
import com.example.workpilotmini.ui.theme.AccentBlueBg
import com.example.workpilotmini.ui.theme.AccentGreen
import com.example.workpilotmini.ui.theme.AccentGreenBg
import com.example.workpilotmini.ui.theme.AccentOrange
import com.example.workpilotmini.ui.theme.AccentOrangeBg
import com.example.workpilotmini.ui.theme.AccentViolet
import com.example.workpilotmini.ui.theme.AccentVioletBg
import com.example.workpilotmini.ui.theme.BrandGradientEnd
import com.example.workpilotmini.ui.theme.BrandGradientStart
import com.example.workpilotmini.ui.theme.KeepGoingBg
import com.example.workpilotmini.ui.theme.TargetRed
import com.example.workpilotmini.ui.visit.VisitViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    profile: UserProfile,
    team: Team?,
    onOpenAttendance: () -> Unit,
    onOpenVisitEntry: () -> Unit,
    onOpenReport: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenMembers: () -> Unit,
    onOpenTeam: () -> Unit,
    onOpenProfile: () -> Unit,
    onLogout: () -> Unit
) {
    val isAdmin = profile.role == "admin"
    val dataOwnerId = if (team == null) profile.uid else team.teamId
    val isSoloData = team == null

    // Controls the "are you sure?" confirmation shown before actually logging out —
    // the bell/profile icons act immediately, but logout is destructive to the current
    // session so it gets a confirm step first.
    var showLogoutDialog by remember { mutableStateOf(false) }

    // Drives the notification bell's badge count, and doubles as the source for the
    // "Visit Today" quick-stat below (both need the same live visit list, so one
    // VisitViewModel instance covers both instead of listening to Firestore twice).
    val notifPeekViewModel: VisitViewModel = viewModel()
    val notifState by notifPeekViewModel.state.collectAsState()
    LaunchedEffect(dataOwnerId, profile.uid) {
        notifPeekViewModel.startListening(dataOwnerId, isSoloData, profile.uid, isAdmin)
    }
    val nearCount = nearReminderCount(notifState.upcomingReminders)

    // Feeds the "Check-in x/y" and "Productivity" quick-stats. For an admin on a real
    // team this is the whole team's today-list (matches the Attendance screen's own
    // admin view); for a member or a solo user it only ever contains their own record.
    val attendanceStatsViewModel: AttendanceViewModel = viewModel()
    val attendanceState by attendanceStatsViewModel.state.collectAsState()
    LaunchedEffect(dataOwnerId, profile.uid) {
        attendanceStatsViewModel.start(dataOwnerId, isSoloData, profile.uid, isAdmin, team)
    }

    val totalMembers = team?.memberUids?.size?.coerceAtLeast(1) ?: 1
    val checkedInCount = if (isAdmin && team != null) {
        // Only count check-ins from people who are still on the team right now, and
        // count each person once — a removed member's old record, or a rare duplicate
        // check-in document, should never inflate this number past the team size.
        attendanceState.todayList
            .filter { it.uid in team.memberUids }
            .map { it.uid }
            .distinct()
            .size
    } else {
        if (attendanceState.checkedInToday) 1 else 0
    }
    val checkInDenominator = if (isAdmin && team != null) totalMembers else 1
    val visitsToday = remember(notifState.visits) { notifState.visits.count { isToday(it.visitTime) } }
    val productivityPercent = if (checkInDenominator > 0) {
        ((checkedInCount * 100f) / checkInDenominator).roundToInt().coerceIn(0, 100)
    } else 0

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("লগ আউট করবেন?") },
            text = { Text("আপনি কি নিশ্চিত লগ আউট করতে চান?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout()
                }) { Text(Strings.logout()) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text(Strings.cancel()) }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // ---- Header: greeting + language toggle + notification bell + profile ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    Strings.hi(profile.name) + " \uD83D\uDC4B",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (isAdmin) Strings.teamAdmin() else Strings.teamMember(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AssistChip(onClick = { AppLanguage.toggle() }, label = { Text(Strings.languageToggleLabel()) })
                CircleIconButton(onClick = onOpenNotifications, badgeCount = nearCount) {
                    Icon(Icons.Filled.Notifications, contentDescription = Strings.notificationsHeader())
                }
                CircleIconButton(onClick = onOpenProfile) {
                    Icon(Icons.Filled.AccountCircle, contentDescription = Strings.profileTitle())
                }
                CircleIconButton(onClick = { showLogoutDialog = true }) {
                    Icon(Icons.Filled.ExitToApp, contentDescription = Strings.logout())
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- Team / solo gradient hero card ----
        team?.let {
            GradientTeamCard(team = it, isAdmin = isAdmin, onClick = onOpenTeam)
            Spacer(Modifier.height(16.dp))
        }

        if (team == null && profile.soloMode) {
            ElevatedCard(
                onClick = onOpenProfile,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text(Strings.soloModeTileTitle(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(Strings.soloModeTileSubtitle(), style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ---- Action tiles ----
        DashboardTile(
            icon = Icons.Filled.CheckCircle,
            title = Strings.attendanceTileTitle(),
            subtitle = Strings.attendanceTileSubtitle(),
            iconColor = AccentGreen,
            iconBg = AccentGreenBg,
            onClick = onOpenAttendance
        )
        Spacer(Modifier.height(12.dp))
        DashboardTile(
            icon = Icons.Filled.LocationOn,
            title = Strings.visitEntryTileTitle(),
            subtitle = Strings.visitEntryTileSubtitle(),
            iconColor = AccentBlue,
            iconBg = AccentBlueBg,
            onClick = onOpenVisitEntry
        )
        Spacer(Modifier.height(12.dp))
        DashboardTile(
            icon = Icons.Filled.Assessment,
            title = Strings.reportTileTitle(),
            subtitle = if (isAdmin && team != null) Strings.reportTileSubtitleAdmin() else Strings.reportTileSubtitleSelf(),
            iconColor = AccentViolet,
            iconBg = AccentVioletBg,
            onClick = onOpenReport
        )

        // Members section: only the real team admin (the person who created the team)
        // gets a browsable member list with per-member full reports + remove/delete —
        // a regular member or a solo user never sees anyone else's data, so this tile
        // is hidden for them.
        if (isAdmin && team != null) {
            Spacer(Modifier.height(12.dp))
            DashboardTile(
                icon = Icons.Filled.Group,
                title = Strings.membersTileTitle(),
                subtitle = Strings.membersTileSubtitle(team.memberUids.size),
                iconColor = AccentOrange,
                iconBg = AccentOrangeBg,
                onClick = onOpenMembers
            )
        }

        Spacer(Modifier.height(20.dp))

        // ---- Keep Going summary + quick stats ----
        KeepGoingCard(
            subtitle = if (team != null) Strings.keepGoingSubtitle() else Strings.keepGoingSubtitleSolo(),
            checkInCount = checkedInCount,
            checkInTotal = checkInDenominator,
            visitsToday = visitsToday,
            teamMembers = totalMembers,
            productivityPercent = productivityPercent
        )

        Spacer(Modifier.height(20.dp))
    }
}

// Plain (non-@Composable) helper, so it can't use remember{} — SimpleDateFormat is cheap
// enough to allocate per-call here since this only runs over a handful of visits.
private fun isToday(millis: Long): Boolean {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(Date(millis)) == fmt.format(Date())
}

@Composable
private fun CircleIconButton(
    onClick: () -> Unit,
    badgeCount: Int = 0,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick) { content() }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(TargetRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    badgeCount.toString(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun GradientTeamCard(team: Team, isAdmin: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                if (team.companyName.isNotBlank()) {
                    Text(team.companyName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    Strings.memberCount(team.memberUids.size, team.maxSize) + "   |   " + Strings.inviteCodeInline(team.inviteCode),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
        if (isAdmin) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Text("\uD83D\uDC51")
            }
        }
    }
}

@Composable
private fun DashboardTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    iconColor: Color,
    iconBg: Color,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = iconBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(iconColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun KeepGoingCard(
    subtitle: String,
    checkInCount: Int,
    checkInTotal: Int,
    visitsToday: Int,
    teamMembers: Int,
    productivityPercent: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(KeepGoingBg)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(Strings.keepGoingTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.GpsFixed, contentDescription = null, tint = TargetRed, modifier = Modifier.size(30.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CheckCircle,
                iconColor = AccentGreen,
                value = "$checkInCount/$checkInTotal",
                label = Strings.statCheckInLabel()
            )
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LocationOn,
                iconColor = AccentBlue,
                value = visitsToday.toString(),
                label = Strings.statVisitTodayLabel()
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Group,
                iconColor = AccentViolet,
                value = teamMembers.toString(),
                label = Strings.statTeamMembersLabel()
            )
            StatMiniCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.BarChart,
                iconColor = AccentOrange,
                value = "$productivityPercent%",
                label = Strings.statProductivityLabel()
            )
        }
    }
}

@Composable
private fun StatMiniCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    value: String,
    label: String
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}