package com.example.workpilotmini.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workpilotmini.data.AttendanceRepository
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.AttendanceRecord
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.VisitEntry
import com.example.workpilotmini.ui.theme.AccentBlue
import com.example.workpilotmini.ui.theme.AccentBlueBg
import com.example.workpilotmini.ui.theme.AccentGreen
import com.example.workpilotmini.ui.theme.AccentGreenBg
import com.example.workpilotmini.ui.theme.AccentOrange
import com.example.workpilotmini.ui.theme.AccentOrangeBg
import com.example.workpilotmini.ui.theme.AccentViolet
import com.example.workpilotmini.ui.theme.AccentVioletBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// There's no real "goal" field stored anywhere yet, so these are reasonable defaults
// used only to size the progress bars / fractions below. Easy to tune from one place
// once real targets exist.
private const val ATTENDANCE_TARGET_PERCENT = 90
private const val VISIT_TARGET_PERCENT = 85
private const val REMINDER_TARGET_PERCENT = 75
private const val VISIT_DAILY_TARGET = 1 // assumed visits/working-day goal

private fun workingDaysFor(period: ReportPeriod): Int = when (period) {
    ReportPeriod.DAILY -> 1
    ReportPeriod.WEEKLY -> 7
    ReportPeriod.MONTHLY -> Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
}

/**
 * Replaces the old separate Reminders + Visit History tiles with one dashboard-style
 * report: daily / weekly / monthly stat cards + progress-bar summary, a fixed "this
 * month's activity" chart, a recent-activity feed, and — for the real team admin
 * viewing the whole team — a today snapshot row + top-performers list.
 *
 * When [focusUid] is set (opened from the Members section) the whole screen scopes down
 * to just that one member's numbers, regardless of who's viewing it.
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
    team: Team? = null,
    onOpenNotifications: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isTeamWide = focusUid == null && isAdmin && !isSolo

    // Current roster names (uid -> name), fetched once per team so the whole-team
    // breakdown table always reflects who's actually on the team right now.
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

    // Fixed monthly view (independent of the Daily/Weekly/Monthly tab) — feeds the
    // chart and the recent-activity feed.
    LaunchedEffect(ownerId, uid, focusUid) {
        viewModel.loadMonthlyBreakdown(ownerId, isSolo, uid, isAdmin, focusUid)
    }

    // Admin-only "right now" snapshot for the whole team — always today, regardless of
    // the selected period tab.
    val attendanceRepo = remember { AttendanceRepository() }
    val visitRepoForSnapshot = remember { VisitRepository() }
    var totalTeamCount by remember { mutableStateOf(0) }
    var checkedInTodayCount by remember { mutableStateOf(0) }
    var todaysVisitsCount by remember { mutableStateOf(0) }
    LaunchedEffect(team?.teamId, isTeamWide) {
        if (isTeamWide && team != null) {
            val todayStart = attendanceRepo.startOfTodayMillis()
            val attendanceToday = attendanceRepo.getAttendanceSince(ownerId, isSolo, todayStart, null).getOrDefault(emptyList())
            val visitsToday = visitRepoForSnapshot.getVisitsSince(ownerId, isSolo, todayStart, null).getOrDefault(emptyList())
            totalTeamCount = team.memberUids.size
            checkedInTodayCount = attendanceToday.map { it.uid }.filter { it in team.memberUids }.distinct().size
            todaysVisitsCount = visitsToday.size
        }
    }

    val workingDays = workingDaysFor(state.period)
    val attendanceRatePercent = if (workingDays > 0) ((state.attendanceCount * 100) / workingDays).coerceIn(0, 100) else 0
    val visitTarget = workingDays.coerceAtLeast(1) * VISIT_DAILY_TARGET
    val visitPercent = if (visitTarget > 0) ((state.visitCount * 100) / visitTarget).coerceIn(0, 100) else 0
    val now = System.currentTimeMillis()
    val handledReminders = state.visits.count { it.nextVisitDate != null && it.nextVisitDate < now }
    val reminderPercent = if (state.reminderCount > 0) ((handledReminders * 100) / state.reminderCount).coerceIn(0, 100) else 0

    val recentActivity = remember(state.recentAttendance, state.recentVisits) {
        buildRecentActivity(state.recentAttendance, state.recentVisits)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ---- Header ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentVioletBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Assessment, contentDescription = null, tint = AccentViolet)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        focusName ?: Strings.reportTileTitle(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            focusName != null -> Strings.reportsSubtitleForMember()
                            isTeamWide -> Strings.reportsSubtitleAdmin()
                            else -> Strings.reportsSubtitleSelf()
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isTeamWide) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(Strings.adminBadge()) })
                    Spacer(Modifier.width(6.dp))
                }
                if (onOpenNotifications != null) {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = Strings.notificationsHeader())
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        PeriodTabs(
            selected = state.period,
            onSelect = { viewModel.setPeriod(it, ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMemberPairs) }
        )

        Spacer(Modifier.height(16.dp))

        if (state.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        state.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }

        // ---- Main stat cards ----
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ReportStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.CheckCircle,
                accent = AccentBlue,
                accentBg = AccentBlueBg,
                label = Strings.reportAttendanceLabel(),
                value = state.attendanceCount.toString(),
                footer = Strings.reportStatInline(attendanceRatePercent)
            )
            ReportStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LocationOn,
                accent = AccentGreen,
                accentBg = AccentGreenBg,
                label = Strings.reportVisitLabel(),
                value = state.visitCount.toString(),
                footer = Strings.fractionInline(state.visitCount, visitTarget)
            )
            ReportStatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Notifications,
                accent = AccentViolet,
                accentBg = AccentVioletBg,
                label = Strings.reportReminderLabel(),
                value = state.reminderCount.toString(),
                footer = Strings.fractionInline(handledReminders, state.reminderCount)
            )
        }

        // ---- Admin-only: today snapshot ----
        if (isTeamWide) {
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ReportStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Group,
                    accent = AccentGreen,
                    accentBg = AccentGreenBg,
                    label = Strings.totalTeamLabel(),
                    value = totalTeamCount.toString(),
                    footer = Strings.activeInline(totalTeamCount)
                )
                ReportStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.CheckCircle,
                    accent = AccentBlue,
                    accentBg = AccentBlueBg,
                    label = Strings.checkedInStatLabel(),
                    value = checkedInTodayCount.toString(),
                    footer = Strings.percentInline(
                        if (totalTeamCount > 0) (checkedInTodayCount * 100 / totalTeamCount) else 0
                    )
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                ReportStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Cancel,
                    accent = AccentOrange,
                    accentBg = AccentOrangeBg,
                    label = Strings.notCheckedInStatLabel(),
                    value = (totalTeamCount - checkedInTodayCount).coerceAtLeast(0).toString(),
                    footer = Strings.percentInline(
                        if (totalTeamCount > 0) ((totalTeamCount - checkedInTodayCount).coerceAtLeast(0) * 100 / totalTeamCount) else 0
                    )
                )
                ReportStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Place,
                    accent = AccentViolet,
                    accentBg = AccentVioletBg,
                    label = Strings.visitStatTodayLabel(),
                    value = todaysVisitsCount.toString(),
                    footer = null
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        // ---- Summary progress bars ----
        Text(Strings.reportSummaryHeader(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        SummaryProgressRow(
            label = Strings.attendanceRateLabel(),
            valueText = "$attendanceRatePercent%",
            percent = attendanceRatePercent,
            color = AccentBlue,
            targetText = Strings.targetInline(ATTENDANCE_TARGET_PERCENT)
        )
        Spacer(Modifier.height(10.dp))
        SummaryProgressRow(
            label = Strings.visitCompletionLabel(),
            valueText = Strings.fractionInline(state.visitCount, visitTarget),
            percent = visitPercent,
            color = AccentGreen,
            targetText = Strings.targetInline(VISIT_TARGET_PERCENT)
        )
        Spacer(Modifier.height(10.dp))
        SummaryProgressRow(
            label = Strings.reminderCompletionLabel(),
            valueText = Strings.fractionInline(handledReminders, state.reminderCount),
            percent = reminderPercent,
            color = AccentViolet,
            targetText = Strings.targetInline(REMINDER_TARGET_PERCENT)
        )

        Spacer(Modifier.height(22.dp))

        // ---- This month's activity chart ----
        Text(Strings.monthlyActivityHeader(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        MonthlyActivityChart(weekly = state.weeklyBreakdown, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(22.dp))

        // ---- Admin-only: top performers ----
        if (isTeamWide && state.memberBreakdown.isNotEmpty()) {
            Text(Strings.topPerformersHeader(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            state.memberBreakdown.take(3).forEach { row ->
                TopPerformerRow(
                    name = row.name.ifBlank { Strings.reportUnknownMember() },
                    roleLabel = if (row.uid == team?.adminUid) Strings.teamAdmin() else Strings.teamMember(),
                    checkInCurrent = row.attendanceCount,
                    checkInTotal = workingDays,
                    visitCount = row.visitCount
                )
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---- Recent activity ----
        Text(Strings.recentActivityHeader(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (recentActivity.isEmpty()) {
            Text(
                Strings.noRecentActivity(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                recentActivity.take(5).forEach { item ->
                    RecentActivityRow(item = item, dateFormat = dateFormat, timeFormat = timeFormat)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun PeriodTabs(selected: ReportPeriod, onSelect: (ReportPeriod) -> Unit) {
    val options = listOf(
        ReportPeriod.DAILY to Strings.reportPeriodDaily(),
        ReportPeriod.WEEKLY to Strings.reportPeriodWeekly(),
        ReportPeriod.MONTHLY to Strings.reportPeriodMonthly()
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (period, label) ->
            val isSelected = selected == period
            Surface(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)),
                color = if (isSelected) AccentBlue else Color.Transparent,
                onClick = { onSelect(period) }
            ) {
                Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                    Text(
                        label,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    accentBg: Color,
    label: String,
    value: String,
    footer: String?
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accentBg) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
            Text(label, style = MaterialTheme.typography.labelMedium)
            if (footer != null) {
                Spacer(Modifier.height(2.dp))
                Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SummaryProgressRow(
    label: String,
    valueText: String,
    percent: Int,
    color: Color,
    targetText: String
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = color, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(valueText, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = color.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(4.dp))
            Text(targetText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MonthlyActivityChart(weekly: List<WeeklyActivity>, modifier: Modifier = Modifier) {
    if (weekly.isEmpty()) return
    val maxRaw = weekly.flatMap { listOf(it.attendanceCount, it.visitCount, it.reminderCount) }.maxOrNull() ?: 0
    val niceMax = (((maxRaw + 4) / 5) * 5).coerceAtLeast(5)

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(AccentBlue, Strings.chartLegendAttendance())
            LegendDot(AccentGreen, Strings.chartLegendVisits())
            LegendDot(AccentViolet, Strings.chartLegendReminders())
        }
        Spacer(Modifier.height(10.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val segmentWidth = size.width / weekly.size
            val barGroupWidth = segmentWidth * 0.6f
            val barWidth = barGroupWidth / 3f
            val bottom = size.height

            // Horizontal grid lines (0/25/50/75/100% of the scale).
            for (i in 0..4) {
                val y = bottom - (bottom * i / 4f)
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
            }

            val linePoints = mutableListOf<Offset>()
            weekly.forEachIndexed { index, week ->
                val segStart = index * segmentWidth
                val groupStart = segStart + (segmentWidth - barGroupWidth) / 2f
                val values = listOf(week.attendanceCount, week.visitCount, week.reminderCount)
                val colors = listOf(AccentBlue, AccentGreen, AccentViolet)
                values.forEachIndexed { bIdx, value ->
                    val barHeight = if (niceMax > 0) (value.toFloat() / niceMax) * bottom else 0f
                    val x = groupStart + bIdx * barWidth
                    drawRoundRect(
                        color = colors[bIdx],
                        topLeft = Offset(x, bottom - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
                val attendanceHeight = if (niceMax > 0) (week.attendanceCount.toFloat() / niceMax) * bottom else 0f
                val pointX = groupStart + barWidth * 0.4f
                linePoints.add(Offset(pointX, bottom - attendanceHeight))
            }

            for (i in 0 until linePoints.size - 1) {
                drawLine(color = AccentBlue, start = linePoints[i], end = linePoints[i + 1], strokeWidth = 3f)
            }
            linePoints.forEach { p ->
                drawCircle(color = Color.White, radius = 5f, center = p)
                drawCircle(color = AccentBlue, radius = 5f, center = p, style = Stroke(width = 2f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            weekly.forEach { week ->
                Text(
                    Strings.weekRangeLabel(week.weekIndex + 1, week.startDay, week.endDay),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun TopPerformerRow(
    name: String,
    roleLabel: String,
    checkInCurrent: Int,
    checkInTotal: Int,
    visitCount: Int
) {
    val percent = if (checkInTotal > 0) ((checkInCurrent * 100) / checkInTotal).coerceIn(0, 100) else 0
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(CircleShape).background(AccentBlueBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        color = AccentBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(roleLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Strings.checkInFractionInline(checkInCurrent, checkInTotal), style = MaterialTheme.typography.bodySmall)
                Text(Strings.visitsCountInline(visitCount), style = MaterialTheme.typography.bodySmall)
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text("$percent%", style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(disabledLabelColor = AccentGreen, disabledContainerColor = AccentGreenBg)
                )
            }
        }
    }
}

// ---- Recent activity ----

private enum class ActivityType { CHECK_IN, VISIT, REMINDER }

private data class RecentActivityItem(
    val type: ActivityType,
    val time: Long,
    val primary: String,
    val secondary: String
)

private data class ActivityVisual(
    val icon: ImageVector,
    val accent: Color,
    val accentBg: Color,
    val title: String
)

private fun buildRecentActivity(
    attendance: List<AttendanceRecord>,
    visits: List<VisitEntry>
): List<RecentActivityItem> {
    val fromAttendance = attendance.map {
        RecentActivityItem(ActivityType.CHECK_IN, it.checkInTime, it.userName, "")
    }
    val fromVisits = visits.map {
        val type = if (it.nextVisitDate != null) ActivityType.REMINDER else ActivityType.VISIT
        RecentActivityItem(type, it.visitTime, it.userName, it.leadName)
    }
    return (fromAttendance + fromVisits).sortedByDescending { it.time }
}

@Composable
private fun RecentActivityRow(item: RecentActivityItem, dateFormat: SimpleDateFormat, timeFormat: SimpleDateFormat) {
    val visual = when (item.type) {
        ActivityType.CHECK_IN -> ActivityVisual(Icons.Filled.Person, AccentBlue, AccentBlueBg, Strings.checkInActivityTitle())
        ActivityType.VISIT -> ActivityVisual(Icons.Filled.Place, AccentGreen, AccentGreenBg, Strings.visitActivityTitle())
        ActivityType.REMINDER -> ActivityVisual(Icons.Filled.Notifications, AccentViolet, AccentVioletBg, Strings.reminderActivityTitle())
    }
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(visual.accentBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(visual.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val secondaryLine = if (item.secondary.isNotBlank()) {
                    "${item.primary} • ${item.secondary}"
                } else item.primary
                Text(secondaryLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${dateFormat.format(Date(item.time))}  •  ${timeFormat.format(Date(item.time))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(Strings.doneChip(), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}