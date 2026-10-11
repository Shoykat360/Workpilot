package com.example.workpilotmini.ui.report

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workpilotmini.data.AttendanceRepository
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.localization.ReportStrings
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
import com.example.workpilotmini.ui.visit.openInMaps
import com.example.workpilotmini.ui.visit.shortLocation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// There's no real "goal" field stored anywhere yet, so these are reasonable defaults
// used only to size the progress bars below. Easy to tune from one place.
private const val ATTENDANCE_TARGET_PERCENT = 90
private const val VISIT_TARGET_PERCENT = 85
private const val REMINDER_TARGET_PERCENT = 75

private val NegativeRed = Color(0xFFD32F2F)

private enum class MemberSort { VISITS, ATTENDANCE, HOURS }

private enum class ReportTab { OVERVIEW, DAILY, TEAM, VISITS }

/**
 * Report dashboard.
 *  - Any day / range from the last 3 months (presets + custom range picker), with clear errors.
 *  - Comparison with the previous period of the same length.
 *  - Everyone: stats, work hours, late check-ins, distance, streak, activity chart + calendar,
 *    business-type breakdown, reminder follow-up, searchable visit list with details (call / map),
 *    share report.
 *  - Admin (team-wide): absent / late / inactive lists, sortable leaderboard with per-member
 *    route on Google Maps, plus today's snapshot.
 *
 * When [focusUid] is set the whole screen scopes down to that one member.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val isTeamWide = focusUid == null && isAdmin && !isSolo

    var showRangePicker by remember { mutableStateOf(false) }
    var selectedVisit by remember { mutableStateOf<VisitEntry?>(null) }
    var visitSearch by remember { mutableStateOf("") }
    var showAllVisits by remember { mutableStateOf(false) }
    var showAllDays by remember { mutableStateOf(false) }
    var memberSort by remember { mutableStateOf(MemberSort.VISITS) }

    // Current roster (uid -> name), fetched once per team.
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

    // Reload whenever the scope, the date range or the roster changes.
    LaunchedEffect(ownerId, uid, focusUid, state.rangeStart, state.rangeEnd, teamMemberPairs) {
        viewModel.load(ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMemberPairs)
    }

    // Admin-only "right now" snapshot for the whole team — always today.
    val attendanceRepo = remember { AttendanceRepository() }
    val visitRepoForSnapshot = remember { VisitRepository() }
    var totalTeamCount by remember { mutableStateOf(0) }
    var checkedInTodayCount by remember { mutableStateOf(0) }
    var checkedOutTodayCount by remember { mutableStateOf(0) }
    var todaysVisitsCount by remember { mutableStateOf(0) }
    LaunchedEffect(team?.teamId, isTeamWide) {
        if (isTeamWide && team != null) {
            val todayStart = attendanceRepo.startOfTodayMillis()
            val attendanceToday = attendanceRepo.getAttendanceSince(ownerId, isSolo, todayStart, null).getOrDefault(emptyList())
            val visitsToday = visitRepoForSnapshot.getVisitsSince(ownerId, isSolo, todayStart, null).getOrDefault(emptyList())
            totalTeamCount = team.memberUids.size
            checkedInTodayCount = attendanceToday.map { it.uid }.filter { it in team.memberUids }.distinct().size
            checkedOutTodayCount = attendanceToday.filter { it.checkOutTime != null }.map { it.uid }.filter { it in team.memberUids }.distinct().size
            todaysVisitsCount = visitsToday.size
        }
    }

    val reminderPercent = if (state.reminderCount > 0)
        ((state.handledReminders * 100) / state.reminderCount).coerceIn(0, 100) else 0
    val visitPercent = ((state.visitCount * 100) / state.visitTarget.coerceAtLeast(1)).coerceIn(0, 100)

    val filteredVisits = remember(state.visits, visitSearch) {
        if (visitSearch.isBlank()) state.visits
        else state.visits.filter {
            it.leadName.contains(visitSearch, true) || it.address.contains(visitSearch, true) ||
                    it.userName.contains(visitSearch, true) || it.businessType.contains(visitSearch, true)
        }
    }
    val shownVisits = if (showAllVisits) filteredVisits else filteredVisits.take(5)

    val sortedMembers = remember(state.memberBreakdown, memberSort) {
        when (memberSort) {
            MemberSort.VISITS -> state.memberBreakdown.sortedByDescending { it.visitCount }
            MemberSort.ATTENDANCE -> state.memberBreakdown.sortedByDescending { it.attendanceCount }
            MemberSort.HOURS -> state.memberBreakdown.sortedByDescending { it.avgHours }
        }
    }
    val recentActivity = remember(state.recentAttendance, state.recentVisits) {
        buildRecentActivity(state.recentAttendance, state.recentVisits)
    }

    val rangeText = remember(state.rangeStart, state.rangeEnd) {
        val a = dateFormat.format(Date(state.rangeStart))
        val b = dateFormat.format(Date(state.rangeEnd))
        if (a == b) a else "$a – $b"
    }
    val columns = if (wide) 3 else 2

    val tabs = remember(isTeamWide) {
        buildList<ReportTab> {
            add(ReportTab.OVERVIEW)
            add(ReportTab.DAILY)
            if (isTeamWide) add(ReportTab.TEAM)
            add(ReportTab.VISITS)
        }
    }
    var currentTab by remember { mutableStateOf(ReportTab.OVERVIEW) }
    val activeTab = if (currentTab in tabs) currentTab else ReportTab.OVERVIEW
    val scrollState = rememberScrollState()
    LaunchedEffect(activeTab) { scrollState.scrollTo(0) }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxSize()) {

            // ───── Fixed top: header + date range ─────
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(38.dp).clip(CircleShape).background(AccentVioletBg),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Assessment, contentDescription = null, tint = AccentViolet) }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                focusName ?: Strings.reportTileTitle(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    focusName != null -> Strings.reportsSubtitleForMember()
                                    isTeamWide -> Strings.reportsSubtitleAdmin()
                                    else -> Strings.reportsSubtitleSelf()
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isTeamWide) {
                            AssistChip(onClick = {}, enabled = false, label = { Text(Strings.adminBadge()) })
                        }
                        IconButton(onClick = {
                            shareText(
                                context,
                                buildShareText(
                                    title = focusName ?: Strings.reportTileTitle(),
                                    rangeText = rangeText,
                                    state = state,
                                    members = sortedMembers,
                                    isTeamWide = isTeamWide
                                )
                            )
                        }) { Icon(Icons.Filled.Share, contentDescription = ReportStrings.shareReport()) }
                        if (onOpenNotifications != null) {
                            IconButton(onClick = onOpenNotifications) {
                                Icon(Icons.Filled.Notifications, contentDescription = Strings.notificationsHeader())
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        RangePreset.TODAY to ReportStrings.presetToday(),
                        RangePreset.LAST_7 to ReportStrings.preset7(),
                        RangePreset.LAST_30 to ReportStrings.preset30(),
                        RangePreset.THIS_MONTH to ReportStrings.presetThisMonth(),
                        RangePreset.LAST_MONTH to ReportStrings.presetLastMonth(),
                        RangePreset.LAST_90 to ReportStrings.preset3Months()
                    ).forEach { (preset, label) ->
                        FilterChip(
                            selected = state.preset == preset,
                            onClick = { viewModel.setPreset(preset) },
                            label = { Text(label) }
                        )
                    }
                    FilterChip(
                        selected = state.preset == RangePreset.CUSTOM,
                        onClick = { showRangePicker = true },
                        leadingIcon = { Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(ReportStrings.presetCustom()) }
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(rangeText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(4.dp))

            // ───── Tabs ─────
            TabRow(selectedTabIndex = tabs.indexOf(activeTab)) {
                tabs.forEach { tab ->
                    Tab(
                        selected = tab == activeTab,
                        onClick = { currentTab = tab },
                        text = {
                            Text(
                                when (tab) {
                                    ReportTab.OVERVIEW -> ReportStrings.tabOverview()
                                    ReportTab.DAILY -> ReportStrings.tabDaily()
                                    ReportTab.TEAM -> ReportStrings.tabTeam()
                                    ReportTab.VISITS -> ReportStrings.tabVisits()
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }
            if (state.isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            // ───── Scrolling content of the selected tab ─────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                state.rangeError?.let { msg ->
                    MessageCard(text = msg, actionLabel = Strings.ok(), onAction = { viewModel.clearRangeError() })
                }
                state.errorMessage?.let { msg ->
                    MessageCard(
                        text = msg,
                        actionLabel = Strings.retry(),
                        onAction = { viewModel.load(ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMemberPairs) }
                    )
                }

                when (activeTab) {

                    // ═════════ OVERVIEW: the essentials on one screen ═════════
                    ReportTab.OVERVIEW -> {
                        val teamInPct = if (state.teamSize > 0) state.peopleCheckedIn * 100 / state.teamSize else 0
                        val teamOutPct = if (state.teamSize > 0) state.peopleCheckedOut * 100 / state.teamSize else 0
                        val mainCards = buildList<@Composable (Modifier) -> Unit> {
                            add { m ->
                                CompactStatCard(
                                    modifier = m, icon = Icons.Filled.CheckCircle, accent = AccentBlue, accentBg = AccentBlueBg,
                                    label = Strings.checkInActivityLabel(),
                                    value = if (isTeamWide) "${state.peopleCheckedIn}/${state.teamSize}" else state.attendanceCount.toString(),
                                    footer = if (isTeamWide) ReportStrings.teamPercentInline(teamInPct)
                                    else Strings.reportStatInline(state.attendanceRatePercent)
                                )
                            }
                            add { m ->
                                CompactStatCard(
                                    modifier = m, icon = Icons.Filled.Schedule, accent = AccentGreen, accentBg = AccentGreenBg,
                                    label = Strings.checkOutActivityLabel(),
                                    value = if (isTeamWide) "${state.peopleCheckedOut}/${state.teamSize}" else state.checkOutCount.toString(),
                                    footer = if (isTeamWide) ReportStrings.teamPercentInline(teamOutPct) else null
                                )
                            }
                            add { m ->
                                CompactStatCard(
                                    modifier = m, icon = Icons.Filled.LocationOn, accent = AccentOrange, accentBg = AccentOrangeBg,
                                    label = Strings.reportVisitLabel(),
                                    value = state.visitCount.toString(),
                                    footer = null
                                )
                            }
                            add { m ->
                                CompactStatCard(
                                    modifier = m, icon = Icons.Filled.Notifications, accent = AccentViolet, accentBg = AccentVioletBg,
                                    label = Strings.reportReminderLabel(),
                                    value = state.reminderCount.toString(),
                                    footer = ReportStrings.remindersDueInline(state.dueRemindersInRange)
                                )
                            }
                        }
                        StatGrid(columns = if (wide) 4 else 2, items = mainCards)

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                        ) {
                            MiniStat(
                                Modifier.weight(1f).fillMaxHeight(), Icons.Filled.Schedule, AccentBlue,
                                if (state.avgHours > 0) fmt1(state.avgHours) else "—",
                                ReportStrings.avgHoursLabel()
                            )
                            MiniStat(
                                Modifier.weight(1f).fillMaxHeight(), Icons.Filled.Warning, AccentOrange,
                                state.lateCount.toString(), ReportStrings.lateLabel()
                            )
                            if (!isTeamWide) {
                                MiniStat(
                                    Modifier.weight(1f).fillMaxHeight(), Icons.Filled.LocalFireDepartment, AccentViolet,
                                    state.currentStreak.toString(), ReportStrings.streakLabel()
                                )
                            }
                        }

                        if (isTeamWide) {
                            val notIn = (totalTeamCount - checkedInTodayCount).coerceAtLeast(0)
                            TodayCard(
                                checkedIn = checkedInTodayCount,
                                checkedOut = checkedOutTodayCount,
                                notIn = notIn,
                                visits = todaysVisitsCount,
                                total = totalTeamCount
                            )
                        }

                        SummaryBarsCard(
                            listOf(
                                SummaryBar(Strings.attendanceRateLabel(), "${state.attendanceRatePercent}%", state.attendanceRatePercent, AccentBlue),
                                SummaryBar(Strings.visitCompletionLabel(), Strings.fractionInline(state.visitCount, state.visitTarget), visitPercent, AccentGreen),
                                SummaryBar(Strings.reminderCompletionLabel(), Strings.fractionInline(state.handledReminders, state.reminderCount), reminderPercent, AccentViolet)
                            )
                        )

                        if (isTeamWide && state.memberBreakdown.isNotEmpty()) {
                            val rows = state.memberBreakdown
                            val absent = rows.filter { it.absentDays > 0 }
                            val late = rows.filter { it.lateCount > 0 }
                            val inactive = rows.filter { it.daysSinceLastVisit == null || it.daysSinceLastVisit >= INACTIVE_DAYS }
                            AttentionSummary(
                                absentLines = absent.map { ReportStrings.absentInline(it.name.ifBlank { Strings.reportUnknownMember() }, it.absentDays) },
                                lateLines = late.map { ReportStrings.lateInline(it.name.ifBlank { Strings.reportUnknownMember() }, it.lateCount) },
                                inactiveLines = inactive.map {
                                    val n = it.name.ifBlank { Strings.reportUnknownMember() }
                                    val d = it.daysSinceLastVisit
                                    if (d == null) ReportStrings.neverVisitedInline(n) else ReportStrings.inactiveInline(n, d)
                                }
                            )
                        }
                    }

                    // ═════════ DAILY: chart, day table, monthly, calendar ═════════
                    ReportTab.DAILY -> {
                        MutedText(ReportStrings.workingDaysInline(state.workingDays))

                        SectionTitle(ReportStrings.activityChartHeader())
                        if (state.buckets.all { it.attendance == 0 && it.visits == 0 && it.reminders == 0 }) {
                            MutedText(ReportStrings.noDataInRange())
                        } else {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 1.dp,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(Modifier.padding(12.dp)) {
                                    ActivityChart(buckets = state.buckets, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }

                        if (state.dailyStats.isNotEmpty()) {
                            SectionTitle(ReportStrings.dailyHeader())
                            val daysNewestFirst = remember(state.dailyStats) { state.dailyStats.reversed() }
                            val shownDays = if (showAllDays) daysNewestFirst else daysNewestFirst.take(7)
                            DailyTable(days = shownDays, teamSize = state.teamSize, isTeamWide = isTeamWide)
                            if (daysNewestFirst.size > 7) {
                                TextButton(
                                    onClick = { showAllDays = !showAllDays },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) { Text(if (showAllDays) ReportStrings.showLess() else ReportStrings.showAllDays(daysNewestFirst.size)) }
                            }
                        }

                        if (state.monthRows.isNotEmpty() && state.dailyStats.size > 1) {
                            SectionTitle(ReportStrings.monthlyHeader())
                            state.monthRows.forEach { MonthSummaryCard(it, state.teamSize, isTeamWide) }
                        }

                        if (state.dailyStats.size > 1) {
                            SectionTitle(ReportStrings.calendarHeader())
                            ActivityCalendar(days = state.dailyStats)
                        }
                    }

                    // ═════════ TEAM (admin): leaderboard ═════════
                    ReportTab.TEAM -> {
                        SectionTitle(ReportStrings.leaderboardHeader())
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                MemberSort.VISITS to ReportStrings.sortVisits(),
                                MemberSort.ATTENDANCE to ReportStrings.sortAttendance(),
                                MemberSort.HOURS to ReportStrings.sortHours()
                            ).forEach { (sort, label) ->
                                FilterChip(selected = memberSort == sort, onClick = { memberSort = sort }, label = { Text(label) })
                            }
                        }
                        if (sortedMembers.isEmpty()) {
                            MutedText(ReportStrings.noDataInRange())
                        } else {
                            sortedMembers.forEachIndexed { index, row ->
                                MemberRow(
                                    rank = index + 1,
                                    row = row,
                                    roleLabel = if (row.uid == team?.adminUid) Strings.teamAdmin() else Strings.teamMember(),
                                    workingDays = state.workingDays,
                                    onClick = { viewModel.openMemberWeek(ownerId, isSolo, row.uid, row.name) },
                                    onRoute = { openRoute(context, row.routeVisits) }
                                )
                            }
                        }
                    }

                    // ═════════ VISITS: follow-up, business type, list ═════════
                    ReportTab.VISITS -> {
                        SectionTitle(ReportStrings.followUpHeader())
                        FollowUpCard(state.followUp)

                        if (state.businessTypes.isNotEmpty()) {
                            SectionTitle(ReportStrings.businessHeader())
                            BusinessTypeBars(state.businessTypes)
                        }

                        SectionTitle(ReportStrings.visitsListHeader(filteredVisits.size))
                        OutlinedTextField(
                            value = visitSearch,
                            onValueChange = { visitSearch = it },
                            placeholder = { Text(Strings.searchVisitsPlaceholder()) },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (shownVisits.isEmpty()) {
                            MutedText(ReportStrings.noVisitsInRange())
                        } else {
                            shownVisits.forEach { v ->
                                VisitListItem(
                                    visit = v,
                                    showUser = isTeamWide,
                                    dateFormat = dateFormat,
                                    timeFormat = timeFormat,
                                    onClick = { selectedVisit = v }
                                )
                            }
                            if (filteredVisits.size > 5) {
                                TextButton(
                                    onClick = { showAllVisits = !showAllVisits },
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                ) { Text(if (showAllVisits) ReportStrings.showLess() else ReportStrings.showMore()) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showRangePicker) {
        CustomRangeDialog(
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                showRangePicker = false
                viewModel.setCustomRange(start, end)
            }
        )
    }
    if (state.memberWeekOpen) {
        MemberWeekSheet(
            name = state.memberWeekName,
            detail = state.memberWeek,
            loading = state.memberWeekLoading,
            error = state.memberWeekError,
            onDismiss = { viewModel.closeMemberWeek() },
            onVisitClick = { selectedVisit = it },
            onRetry = {
                val m = state.memberBreakdown.firstOrNull { it.name == state.memberWeekName }
                if (m != null) viewModel.openMemberWeek(ownerId, isSolo, m.uid, m.name)
            }
        )
    }
    selectedVisit?.let { v ->
        VisitDetailDialog(
            visit = v,
            showUser = isTeamWide,
            dateTimeFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) },
            onDismiss = { selectedVisit = null }
        )
    }
}

// ───────────────────────── helpers ─────────────────────────

private fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

private fun lateAfterText(): String {
    val h12 = if (LATE_AFTER_HOUR % 12 == 0) 12 else LATE_AFTER_HOUR % 12
    val ampm = if (LATE_AFTER_HOUR < 12) "AM" else "PM"
    return String.format(Locale.US, "%d:%02d %s", h12, LATE_AFTER_MINUTE, ampm)
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        context.startActivity(Intent.createChooser(intent, ReportStrings.shareChooserTitle()))
    } catch (e: ActivityNotFoundException) {
        // No app can handle sharing — nothing more to do.
    }
}

private fun buildShareText(
    title: String,
    rangeText: String,
    state: ReportUiState,
    members: List<MemberReportRow>,
    isTeamWide: Boolean
): String = buildString {
    appendLine("📊 $title")
    appendLine(rangeText)
    appendLine()
    if (isTeamWide) {
        appendLine("${Strings.checkInActivityLabel()}: ${state.peopleCheckedIn}/${state.teamSize} ${ReportStrings.peopleUnit()} (${ReportStrings.totalCheckInsInline(state.attendanceCount)})")
        appendLine("${Strings.checkOutActivityLabel()}: ${state.peopleCheckedOut}/${state.teamSize} ${ReportStrings.peopleUnit()} (${ReportStrings.totalCheckOutsInline(state.checkOutCount)})")
    } else {
        appendLine("${Strings.checkInActivityLabel()}: ${state.attendanceCount} (${state.attendanceRatePercent}%)")
        appendLine("${Strings.checkOutActivityLabel()}: ${state.checkOutCount}")
    }
    appendLine("${Strings.reportVisitLabel()}: ${state.visitCount}")
    appendLine("${Strings.reportReminderLabel()}: ${ReportStrings.monthReminders(state.reminderCount, state.dueRemindersInRange)}")
    if (state.avgHours > 0) appendLine("${ReportStrings.avgHoursLabel()}: ${ReportStrings.hoursPerDayInline(fmt1(state.avgHours))}")
    appendLine("${ReportStrings.lateLabel()}: ${state.lateCount}")
    if (members.isNotEmpty()) {
        appendLine()
        appendLine(ReportStrings.leaderboardHeader())
        members.forEachIndexed { i, m ->
            appendLine(
                "${i + 1}. ${m.name.ifBlank { Strings.reportUnknownMember() }} — " +
                        "${Strings.visitsCountInline(m.visitCount)}, " +
                        "${ReportStrings.presentInline(m.presentDays, state.workingDays)}, " +
                        ReportStrings.absentCountInline(m.absentDays)
            )
        }
    }
}

/** Opens Google Maps with the member's day route (works without any API key). */
private fun openRoute(context: Context, visits: List<VisitEntry>) {
    val points = visits.mapNotNull { v ->
        val la = v.lat
        val ln = v.lng
        if (la != null && ln != null) "$la,$ln" else null
    }.take(11)
    if (points.isEmpty()) return
    val uri = if (points.size == 1) {
        Uri.parse("geo:${points[0]}?q=${points[0]}")
    } else {
        val waypoints = points.drop(1).dropLast(1).joinToString("%7C")
        Uri.parse(
            "https://www.google.com/maps/dir/?api=1&origin=${points.first()}&destination=${points.last()}&travelmode=driving" +
                    if (waypoints.isNotEmpty()) "&waypoints=$waypoints" else ""
        )
    }
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        // No maps app / browser available.
    }
}

// ───────────────────────── dialogs ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(onDismiss: () -> Unit, onConfirm: (Long, Long) -> Unit) {
    // Only the last 3 months up to today can be selected, so invalid picks are impossible.
    val earliestUtc = remember { localDayToUtcMillis(earliestAllowedStart()) }
    val todayUtc = remember { localDayToUtcMillis(System.currentTimeMillis()) }
    val pickerState = rememberDateRangePickerState(
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis in earliestUtc..todayUtc
        }
    )
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val startUtc = pickerState.selectedStartDateMillis
    val endUtc = pickerState.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = startUtc != null,
                onClick = {
                    if (startUtc != null) {
                        val s = utcDayToLocalStart(startUtc)
                        val e = utcDayToLocalStart(endUtc ?: startUtc)
                        onConfirm(s, e)
                    }
                }
            ) { Text(Strings.ok()) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel()) } }
    ) {
        DateRangePicker(
            state = pickerState,
            modifier = Modifier.height((screenHeight * 0.7f).coerceIn(360f, 560f).dp),
            title = {
                Column(modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)) {
                    Text(ReportStrings.customRangeTitle(), style = MaterialTheme.typography.titleMedium)
                    Text(
                        ReportStrings.rangeHint(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun VisitDetailDialog(
    visit: VisitEntry,
    showUser: Boolean,
    dateTimeFormat: SimpleDateFormat,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lat = visit.lat
    val lng = visit.lng
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(visit.leadName.ifBlank { "-" }, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailLine(ReportStrings.detailVisitedAt(), dateTimeFormat.format(Date(visit.visitTime)))
                if (showUser && visit.userName.isNotBlank()) DetailLine(ReportStrings.detailBy(), visit.userName)
                if (visit.businessType.isNotBlank()) DetailLine(ReportStrings.detailBusiness(), visit.businessType)
                if (visit.address.isNotBlank()) {
                    Column {
                        Text(ReportStrings.detailAddress(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            shortLocation(visit.address, 20),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (lat != null && lng != null) AccentBlue else MaterialTheme.colorScheme.onSurface,
                            modifier = if (lat != null && lng != null)
                                Modifier.clickable { openInMaps(context, lat, lng, visit.leadName) }
                            else Modifier
                        )
                    }
                }
                if (visit.contactPerson.isNotBlank()) DetailLine(ReportStrings.detailContact(), visit.contactPerson)
                if (visit.phoneNumber.isNotBlank()) DetailLine(ReportStrings.detailPhone(), visit.phoneNumber)
                if (visit.visitPurpose.isNotBlank()) DetailLine(ReportStrings.detailPurpose(), visit.visitPurpose)
                if (visit.notes.isNotBlank()) DetailLine(ReportStrings.detailNotes(), visit.notes)
                val next = visit.nextVisitDate
                if (next != null) {
                    DetailLine(
                        ReportStrings.detailNextVisit(),
                        SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(next))
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(ReportStrings.close()) } },
        dismissButton = {
            if (visit.phoneNumber.isNotBlank()) {
                TextButton(onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(visit.phoneNumber)}")))
                    } catch (e: ActivityNotFoundException) {
                        // No dialer available.
                    }
                }) {
                    Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(ReportStrings.callAction())
                }
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// ───────────────────────── daily / monthly summary ─────────────────────────

@Composable
private fun DailyTable(days: List<DayStat>, teamSize: Int, isTeamWide: Boolean) {
    val dayFormat = remember { SimpleDateFormat("EEE dd MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TableCell(ReportStrings.colDate(), 1.35f, header = true, align = TextAlign.Start)
                TableCell(Strings.checkInActivityLabel(), 1.05f, header = true)
                TableCell(Strings.checkOutActivityLabel(), 1.05f, header = true)
                TableCell(ReportStrings.colVisits(), 0.8f, header = true)
                TableCell(ReportStrings.colReminders(), 1.0f, header = true)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            days.forEach { d ->
                val weekend = isWeekend(d.dayStart)
                val nobody = d.checkedInPeople == 0
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    TableCell(
                        dayFormat.format(Date(d.dayStart)), 1.35f, align = TextAlign.Start,
                        color = if (weekend) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    TableCell(
                        if (isTeamWide) "${d.checkedInPeople}/$teamSize"
                        else d.firstCheckIn?.let { timeFormat.format(Date(it)) } ?: "—",
                        1.05f,
                        color = if (nobody && !weekend) NegativeRed else AccentBlue
                    )
                    TableCell(
                        if (isTeamWide) "${d.checkedOutPeople}/$teamSize"
                        else d.lastCheckOut?.let { timeFormat.format(Date(it)) } ?: "—",
                        1.05f,
                        color = AccentGreen
                    )
                    TableCell(d.visits.toString(), 0.8f, color = AccentOrange)
                    TableCell("${d.reminders}/${d.remindersDue}", 1.0f, color = AccentViolet)
                }
            }
        }
    }
}

@Composable
private fun RowScope.TableCell(
    text: String,
    weight: Float,
    header: Boolean = false,
    align: TextAlign = TextAlign.Center,
    color: Color = Color.Unspecified
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        textAlign = align,
        fontSize = if (header) 10.sp else 11.sp,
        lineHeight = if (header) 12.sp else 14.sp,
        fontWeight = if (header) FontWeight.SemiBold else FontWeight.Normal,
        color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else color,
        maxLines = if (header) 2 else 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun MonthSummaryCard(m: MonthSummary, teamSize: Int, isTeamWide: Boolean) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(m.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                if (isTeamWide)
                    ReportStrings.monthCheckInTeam(m.checkedInPeople, teamSize, m.checkedInPersonDays, m.workingDays * teamSize)
                else ReportStrings.monthPresentSelf(m.presentDays, m.workingDays),
                style = MaterialTheme.typography.bodySmall,
                color = AccentBlue
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(ReportStrings.monthCheckOut(m.checkedOutPersonDays), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AccentGreen)
                Text(ReportStrings.monthVisits(m.visits), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = AccentOrange)
            }
            Text(ReportStrings.monthReminders(m.remindersSet, m.remindersDue), style = MaterialTheme.typography.bodySmall, color = AccentViolet)
        }
    }
}

// ───────────────────────── member: last 7 days ─────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberWeekSheet(
    name: String,
    detail: MemberWeekDetail?,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onVisitClick: (VisitEntry) -> Unit,
    onRetry: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dayFormat = remember { SimpleDateFormat("EEE, dd MMM", Locale.getDefault()) }
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    ReportStrings.weekDetailTitle(name.ifBlank { Strings.reportUnknownMember() }),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (error != null) {
                    MessageCard(text = error, actionLabel = Strings.retry(), onAction = onRetry)
                }
                if (detail != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SummaryPill(ReportStrings.weekPresent(detail.presentDays, detail.workingDays), AccentBlue)
                        SummaryPill(ReportStrings.weekAbsent(detail.absentDays), AccentOrange)
                        SummaryPill(ReportStrings.weekLate(detail.lateCount), AccentViolet)
                        SummaryPill(ReportStrings.weekVisits(detail.visitCount), AccentGreen)
                        if (detail.avgHours > 0) {
                            SummaryPill(ReportStrings.weekAvgHours(fmt1(detail.avgHours)), AccentBlue)
                        }
                    }
                    detail.days.forEach { day ->
                        DayDetailCard(day, dayFormat, timeFormat, onVisitClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.12f)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun DayDetailCard(
    day: MemberDayDetail,
    dayFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    onVisitClick: (VisitEntry) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    dayFormat.format(Date(day.dayStart)),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (day.isAbsent) SummaryPill(ReportStrings.dayAbsent(), AccentOrange)
                    if (day.isWeekend && day.firstCheckIn == null) SummaryPill(ReportStrings.dayWeekend(), Color.Gray)
                    if (day.isLate) SummaryPill(ReportStrings.dayLate(), AccentViolet)
                }
            }
            if (day.firstCheckIn != null) {
                Spacer(Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(Strings.checkInActivityLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(timeFormat.format(Date(day.firstCheckIn)), style = MaterialTheme.typography.bodyMedium)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(Strings.checkOutActivityLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            day.lastCheckOut?.let { timeFormat.format(Date(it)) } ?: ReportStrings.dayNotCheckedOut(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    if (day.hours > 0) {
                        Column(Modifier.weight(1f)) {
                            Text(ReportStrings.avgHoursLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(ReportStrings.dayHours(fmt1(day.hours)), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            } else if (!day.isWeekend) {
                Spacer(Modifier.height(4.dp))
                MutedText(ReportStrings.dayNoCheckIn())
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            if (day.visits.isEmpty()) {
                MutedText(ReportStrings.dayNoVisits())
            } else {
                Text(
                    ReportStrings.dayVisitsHeader(day.visits.size),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    day.visits.forEach { v ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onVisitClick(v) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                            Spacer(Modifier.width(6.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    v.leadName.ifBlank { "-" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (v.address.isNotBlank()) {
                                    Text(
                                        shortLocation(v.address),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(timeFormat.format(Date(v.visitTime)), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── compact overview blocks ─────────────────────────

@Composable
private fun CompactStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accent: Color,
    accentBg: Color,
    label: String,
    value: String,
    footer: String?
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accentBg) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (footer != null) {
                    Text(
                        footer,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniStat(modifier: Modifier, icon: ImageVector, tint: Color, value: String, label: String) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Admin: today's team snapshot in one compact card. */
@Composable
private fun TodayCard(checkedIn: Int, checkedOut: Int, notIn: Int, visits: Int, total: Int) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Group, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${Strings.todaysActivityHeader()} • ${Strings.totalTeamLabel()} $total",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                NumberCol(checkedIn.toString(), Strings.checkedInStatLabel(), AccentBlue, Modifier.weight(1f))
                NumberCol(checkedOut.toString(), ReportStrings.checkedOutLabel(), AccentGreen, Modifier.weight(1f))
                NumberCol(notIn.toString(), Strings.notCheckedInStatLabel(), AccentOrange, Modifier.weight(1f))
                NumberCol(visits.toString(), Strings.visitStatTodayLabel(), AccentViolet, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NumberCol(value: String, label: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private data class SummaryBar(val label: String, val valueText: String, val percent: Int, val color: Color)

/** Three thin progress bars in a single card (replaces three big cards). */
@Composable
private fun SummaryBarsCard(bars: List<SummaryBar>) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            bars.forEach { bar ->
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = bar.color,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(bar.valueText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { bar.percent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = bar.color,
                        trackColor = bar.color.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}

/** Admin: one tappable card — counts always visible, names appear when expanded. */
@Composable
private fun AttentionSummary(absentLines: List<String>, lateLines: List<String>, inactiveLines: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val nothing = absentLines.isEmpty() && lateLines.isEmpty() && inactiveLines.isEmpty()
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = !nothing) { expanded = !expanded }
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = if (nothing) AccentGreen else AccentOrange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (nothing) ReportStrings.allGood() else ReportStrings.attentionHeader(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (!nothing) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }
            if (!nothing) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (absentLines.isNotEmpty()) SummaryPill(ReportStrings.absentHeader(absentLines.size), AccentOrange)
                    if (lateLines.isNotEmpty()) SummaryPill(ReportStrings.lateHeader(lateLines.size), AccentViolet)
                    if (inactiveLines.isNotEmpty()) SummaryPill(ReportStrings.inactiveHeader(inactiveLines.size), NegativeRed)
                }
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        (absentLines + lateLines + inactiveLines).forEach { line ->
                            Text("• $line", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── building blocks ─────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun MutedText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MessageCard(text: String, actionLabel: String, onAction: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Lays [items] out in rows of [columns] equal-width cards (so it adapts to screen width). */
@Composable
private fun StatGrid(columns: Int, items: List<@Composable (Modifier) -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
            ) {
                rowItems.forEach { item -> item(Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - rowItems.size) { Spacer(Modifier.weight(1f)) }
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
    footer: String?,
    delta: Pair<String, Color>? = null
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = accentBg) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
                if (delta != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        delta.first,
                        style = MaterialTheme.typography.labelSmall,
                        color = delta.second,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (footer != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    footer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SummaryProgressRow(label: String, valueText: String, percent: Int, color: Color, targetText: String) {
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
private fun ActivityChart(buckets: List<ActivityBucket>, modifier: Modifier = Modifier) {
    if (buckets.isEmpty()) return
    val maxRaw = buckets.flatMap { listOf(it.attendance, it.visits, it.reminders) }.maxOrNull() ?: 0
    val niceMax = (((maxRaw + 4) / 5) * 5).coerceAtLeast(5)

    Column(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LegendDot(AccentBlue, Strings.chartLegendAttendance())
            LegendDot(AccentGreen, Strings.chartLegendVisits())
            LegendDot(AccentViolet, Strings.chartLegendReminders())
        }
        Spacer(Modifier.height(10.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
            val segmentWidth = size.width / buckets.size
            val barGroupWidth = segmentWidth * 0.7f
            val barWidth = barGroupWidth / 3f
            val bottom = size.height

            for (i in 0..4) {
                val y = bottom - (bottom * i / 4f)
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f
                )
            }

            val linePoints = mutableListOf<Offset>()
            buckets.forEachIndexed { index, b ->
                val groupStart = index * segmentWidth + (segmentWidth - barGroupWidth) / 2f
                val values = listOf(b.attendance, b.visits, b.reminders)
                val colors = listOf(AccentBlue, AccentGreen, AccentViolet)
                values.forEachIndexed { bIdx, value ->
                    val barHeight = (value.toFloat() / niceMax) * bottom
                    drawRoundRect(
                        color = colors[bIdx],
                        topLeft = Offset(groupStart + bIdx * barWidth, bottom - barHeight),
                        size = androidx.compose.ui.geometry.Size(barWidth * 0.8f, barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
                val attendanceHeight = (b.attendance.toFloat() / niceMax) * bottom
                linePoints.add(Offset(groupStart + barWidth * 0.4f, bottom - attendanceHeight))
            }
            if (buckets.size > 1) {
                for (i in 0 until linePoints.size - 1) {
                    drawLine(color = AccentBlue, start = linePoints[i], end = linePoints[i + 1], strokeWidth = 3f)
                }
            }
            linePoints.forEach { p ->
                drawCircle(color = Color.White, radius = 5f, center = p)
                drawCircle(color = AccentBlue, radius = 5f, center = p, style = Stroke(width = 2f))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            buckets.forEach { b ->
                Text(
                    b.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = if (buckets.size > 8) 9.sp else 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1
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

/** Week-by-week calendar: green = had visits (darker = more), blue = attendance only. */
@Composable
private fun ActivityCalendar(days: List<DayStat>) {
    val first = days.first().dayStart
    val cal = Calendar.getInstance().apply { timeInMillis = first }
    val firstDow = cal.firstDayOfWeek
    val offset = (cal.get(Calendar.DAY_OF_WEEK) - firstDow + 7) % 7
    val cells: List<DayStat?> = List(offset) { null } + days
    val weekdayNames = remember(firstDow) {
        val fmt = SimpleDateFormat("EEEEE", Locale.getDefault())
        (0 until 7).map { i ->
            val c = Calendar.getInstance()
            c.set(Calendar.DAY_OF_WEEK, ((firstDow - 1 + i) % 7) + 1)
            fmt.format(c.time)
        }
    }

    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                weekdayNames.forEach { name ->
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        val color = when {
                            d == null -> Color.Transparent
                            d.visits > 0 -> AccentGreen.copy(alpha = (0.25f + 0.2f * minOf(d.visits, 4)).coerceAtMost(1f))
                            d.attendance > 0 -> AccentBlue.copy(alpha = 0.35f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(2.dp)
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(color),
                            contentAlignment = Alignment.Center
                        ) {
                            if (d != null) {
                                val dayNum = Calendar.getInstance().apply { timeInMillis = d.dayStart }.get(Calendar.DAY_OF_MONTH)
                                Text(dayNum.toString(), fontSize = 10.sp, maxLines = 1)
                            }
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendDot(AccentGreen, ReportStrings.heatVisits())
                LegendDot(AccentBlue, ReportStrings.heatAttendance())
                LegendDot(MaterialTheme.colorScheme.surfaceVariant, ReportStrings.heatNone())
            }
        }
    }
}

@Composable
private fun BusinessTypeBars(types: List<BusinessTypeStat>) {
    val maxCount = types.maxOf { it.count }.coerceAtLeast(1)
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            types.forEach { stat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stat.type.ifBlank { ReportStrings.businessOther() },
                        modifier = Modifier.weight(0.4f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(modifier = Modifier.weight(0.5f).padding(horizontal = 8.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(stat.count.toFloat() / maxCount)
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(AccentViolet)
                        )
                    }
                    Text(
                        stat.count.toString(),
                        modifier = Modifier.weight(0.1f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowUpCard(f: FollowUpSummary) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FollowUpNumber(f.pastDue, ReportStrings.followPastDue(), NegativeRed)
                FollowUpNumber(f.today, ReportStrings.followToday(), AccentOrange)
                FollowUpNumber(f.upcoming, ReportStrings.followUpcoming(), AccentGreen)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                ReportStrings.followUpNote(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FollowUpNumber(count: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AttentionCard(title: String, accent: Color, accentBg: Color, lines: List<String>) {
    Surface(shape = RoundedCornerShape(14.dp), color = accentBg, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.Bold)
            lines.forEach { line ->
                Text("• $line", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MemberRow(
    rank: Int,
    row: MemberReportRow,
    roleLabel: String,
    workingDays: Int,
    onClick: () -> Unit,
    onRoute: () -> Unit
) {
    val name = row.name.ifBlank { Strings.reportUnknownMember() }
    val summary = buildList {
        add(Strings.visitsCountInline(row.visitCount))
        add(ReportStrings.presentInline(row.presentDays, workingDays))
        if (row.avgHours > 0) add(ReportStrings.hoursPerDayInline(fmt1(row.avgHours)))
    }.joinToString(" • ")
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(34.dp).clip(CircleShape).background(AccentBlueBg),
                    contentAlignment = Alignment.Center
                ) { Text(rank.toString(), color = AccentBlue, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (row.routeVisits.isNotEmpty()) {
                    IconButton(onClick = onRoute) {
                        Icon(Icons.Filled.Directions, contentDescription = ReportStrings.routeButton(), tint = AccentBlue)
                    }
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val badges = buildList {
                if (row.absentDays > 0) add(ReportStrings.absentCountInline(row.absentDays) to AccentOrange)
                if (row.lateCount > 0) add(ReportStrings.lateCountInline(row.lateCount) to AccentViolet)
                if (row.pastDueFollowUps > 0) add(ReportStrings.pastDueInline(row.pastDueFollowUps) to NegativeRed)
                val d = row.daysSinceLastVisit
                if (d != null && d >= INACTIVE_DAYS) add(ReportStrings.noVisitInline(d) to NegativeRed)
            }
            if (badges.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    badges.forEach { (text, color) ->
                        Surface(shape = RoundedCornerShape(8.dp), color = color.copy(alpha = 0.12f)) {
                            Text(
                                text,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Metric(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun VisitListItem(
    visit: VisitEntry,
    showUser: Boolean,
    dateFormat: SimpleDateFormat,
    timeFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentGreenBg),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Place, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    visit.leadName.ifBlank { "-" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = buildList {
                    if (showUser && visit.userName.isNotBlank()) add(visit.userName)
                    if (visit.address.isNotBlank()) add(shortLocation(visit.address, 6))
                }.joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    "${dateFormat.format(Date(visit.visitTime))}  •  ${timeFormat.format(Date(visit.visitTime))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ───────────────────────── recent activity ─────────────────────────

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
            ) { Icon(visual.icon, contentDescription = null, tint = visual.accent, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(visual.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                val secondaryLine = if (item.secondary.isNotBlank()) "${item.primary} • ${item.secondary}" else item.primary
                Text(
                    secondaryLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${dateFormat.format(Date(item.time))}  •  ${timeFormat.format(Date(item.time))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(6.dp))
            AssistChip(
                onClick = {},
                enabled = false,
                label = { Text(Strings.doneChip(), style = MaterialTheme.typography.labelSmall) }
            )
        }
    }
}