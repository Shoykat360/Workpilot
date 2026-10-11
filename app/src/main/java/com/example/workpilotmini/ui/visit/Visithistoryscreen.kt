package com.example.workpilotmini.ui.visit

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
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
import java.util.Date
import java.util.Locale

/** A team member shown in the admin's daily monitor (so we can tell who has NOT visited). */
data class TeamMemberInfo(val uid: String, val name: String)

private enum class AdminUserFilter { ALL, VISITED, NOT_VISITED }

/** One person's activity for today — used by the admin monitor. */
private data class UserDayRow(val uid: String, val name: String, val visits: List<VisitEntry>)

/**
 * Visit tab landing screen.
 *
 *  - Member: sees ONLY today's own visits + can add a new visit.
 *  - Admin : sees ONLY today's team activity — who visited, who hasn't, filter by user,
 *            location of each visit (opens Google Maps) + can also add a visit.
 *
 * [members] = the team roster (admin + members). Needed so the admin can see who has
 * NOT given a visit today. If empty, only people who have visits are listed.
 *
 * Scoping of data (team-wide vs own) still happens in [VisitViewModel.startListening].
 */
@Composable
fun VisitHistoryScreen(
    viewModel: VisitViewModel,
    isAdmin: Boolean,
    onAddVisit: () -> Unit,
    onOpenNotifications: (() -> Unit)? = null,
    members: List<TeamMemberInfo> = emptyList()
) {
    val state by viewModel.state.collectAsState()
    val todayVisits = remember(state.visits) {
        state.visits.filter { isToday(it.visitTime) }.sortedByDescending { it.visitTime }
    }

    Scaffold(
        // The parent screen already handles the status-bar inset; without this the inner
        // Scaffold adds it a second time and pushes the header too far down.
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddVisit,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(Strings.addVisitFabLabel()) }
            )
        }
    ) { innerPadding ->
        val modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(innerPadding)
        if (isAdmin) {
            AdminTodayContent(modifier, todayVisits, members.ifEmpty { state.members }, state.upcomingReminders.size, onOpenNotifications)
        } else {
            MemberTodayContent(modifier, todayVisits, state.upcomingReminders.size, onOpenNotifications)
        }
    }
}

// ───────────────────────────── Member ─────────────────────────────

@Composable
private fun MemberTodayContent(
    modifier: Modifier,
    todayVisits: List<VisitEntry>,
    reminderCount: Int,
    onOpenNotifications: (() -> Unit)?
) {
    var search by remember { mutableStateOf("") }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val visible = remember(todayVisits, search) {
        if (search.isBlank()) todayVisits
        else todayVisits.filter {
            it.leadName.contains(search, true) || it.address.contains(search, true) ||
                    it.businessType.contains(search, true)
        }
    }

    ResponsiveList(modifier) {
        item { VisitHeader(isAdmin = false, onOpenNotifications = onOpenNotifications) }
        item {
            Text(Strings.myVisitsTitle(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle, iconColor = AccentGreen, iconBg = AccentGreenBg,
                    value = todayVisits.size.toString(), label = Strings.visitStatTodayLabel(), footer = Strings.visitCompletedFooter()
                )
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Notifications, iconColor = AccentOrange, iconBg = AccentOrangeBg,
                    value = reminderCount.toString(), label = Strings.visitStatRemindersLabel(), footer = Strings.reminderScheduledFooter()
                )
            }
        }
        item { SearchField(search) { search = it } }
        item { Text(Strings.todayLabel(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }

        if (visible.isEmpty()) {
            item { EmptyHint(Strings.noVisitsForFilter()) }
        } else {
            items(visible, key = { it.id.ifBlank { it.visitTime.toString() } }) { v ->
                VisitRow(v, showUserName = false, timeFmt = timeFmt, dateFmt = dateFmt)
            }
        }
    }
}

// ───────────────────────────── Admin ─────────────────────────────

@Composable
private fun AdminTodayContent(
    modifier: Modifier,
    todayVisits: List<VisitEntry>,
    members: List<TeamMemberInfo>,
    reminderCount: Int,
    onOpenNotifications: (() -> Unit)?
) {
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AdminUserFilter.ALL) }
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // One row per person: everyone on the roster, plus anyone who has a visit but isn't on it.
    val rows = remember(todayVisits, members) {
        val byUid = todayVisits.groupBy { it.uid }
        val fromRoster = members.map { m -> UserDayRow(m.uid, m.name, byUid[m.uid].orEmpty()) }
        val rosterUids = members.map { it.uid }.toSet()
        val extras = byUid.filterKeys { it !in rosterUids }.map { (uid, list) ->
            UserDayRow(uid, list.firstOrNull()?.userName.orEmpty().ifBlank { Strings.reportUnknownMember() }, list)
        }
        (fromRoster + extras).sortedWith(
            compareByDescending<UserDayRow> { it.visits.isNotEmpty() }.thenBy { it.name.lowercase() }
        )
    }
    val visitedCount = rows.count { it.visits.isNotEmpty() }
    val notVisitedCount = rows.size - visitedCount

    val visibleRows = remember(rows, filter, search) {
        rows.filter { r ->
            when (filter) {
                AdminUserFilter.ALL -> true
                AdminUserFilter.VISITED -> r.visits.isNotEmpty()
                AdminUserFilter.NOT_VISITED -> r.visits.isEmpty()
            }
        }.filter { r ->
            search.isBlank() || r.name.contains(search, true) ||
                    r.visits.any { it.leadName.contains(search, true) || it.address.contains(search, true) }
        }
    }

    ResponsiveList(modifier) {
        item { VisitHeader(isAdmin = true, onOpenNotifications = onOpenNotifications) }
        item { Text(Strings.reportSummaryHeader(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Place, iconColor = AccentBlue, iconBg = AccentBlueBg,
                    value = todayVisits.size.toString(), label = Strings.totalVisitsTodayLabel()
                )
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Notifications, iconColor = AccentViolet, iconBg = AccentVioletBg,
                    value = reminderCount.toString(), label = Strings.visitStatRemindersLabel(), footer = Strings.reminderScheduledFooter()
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CheckCircle, iconColor = AccentGreen, iconBg = AccentGreenBg,
                    value = visitedCount.toString(), label = Strings.gaveVisitLabel(), footer = Strings.ofPeopleInline(rows.size)
                )
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Cancel, iconColor = AccentOrange, iconBg = AccentOrangeBg,
                    value = notVisitedCount.toString(), label = Strings.noVisitYetLabel(), footer = Strings.todayLabel()
                )
            }
        }

        item {
            Text(Strings.teamActivityTodayHeader(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item { SearchField(search, placeholder = Strings.searchTeamVisitsPlaceholder()) { search = it } }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = filter == AdminUserFilter.ALL,
                    onClick = { filter = AdminUserFilter.ALL },
                    label = { Text(Strings.filterAllCount(rows.size)) }
                )
                FilterChip(
                    selected = filter == AdminUserFilter.VISITED,
                    onClick = { filter = AdminUserFilter.VISITED },
                    label = { Text(Strings.filterVisitedCount(visitedCount)) }
                )
                FilterChip(
                    selected = filter == AdminUserFilter.NOT_VISITED,
                    onClick = { filter = AdminUserFilter.NOT_VISITED },
                    label = { Text(Strings.filterNotYetCount(notVisitedCount)) }
                )
            }
        }

        if (visibleRows.isEmpty()) {
            item { EmptyHint(Strings.noVisitsForFilter()) }
        } else {
            items(visibleRows, key = { it.uid }) { row ->
                UserActivityCard(row, timeFmt, dateFmt)
            }
        }
    }
}

@Composable
private fun UserActivityCard(row: UserDayRow, timeFmt: SimpleDateFormat, dateFmt: SimpleDateFormat) {
    var expanded by remember { mutableStateOf(false) }
    val hasVisits = row.visits.isNotEmpty()
    val last = row.visits.firstOrNull() // list is already newest-first

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = hasVisits) { expanded = !expanded }
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                        .background(if (hasVisits) AccentGreenBg else MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (row.name.trim().firstOrNull() ?: '?').uppercaseChar().toString(),
                        color = if (hasVisits) AccentGreen else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(row.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (hasVisits && last != null) {
                        Text(
                            Strings.visitsLastAtInline(row.visits.size, timeFmt.format(Date(last.visitTime))),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            Strings.noVisitToday(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (hasVisits) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.visits.forEach { v ->
                        VisitRow(v, showUserName = false, timeFmt = timeFmt, dateFmt = dateFmt, flat = true)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── Shared pieces ─────────────────────────────

/** Scrolling list that fills phones edge-to-edge but stays centered (max 720dp) on tablets. */
@Composable
private fun ResponsiveList(modifier: Modifier, content: LazyListScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 0.dp, bottom = 96.dp),
            content = content
        )
    }
}

@Composable
private fun VisitHeader(isAdmin: Boolean, onOpenNotifications: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentBlueBg),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Place, contentDescription = null, tint = AccentBlue) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(Strings.visitEntryHeader(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (isAdmin) Strings.visitHistoryHeaderAdmin() else Strings.visitHistoryHeaderMember(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isAdmin) AssistChip(onClick = {}, enabled = false, label = { Text(Strings.adminBadge()) })
        if (onOpenNotifications != null) {
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Default.Notifications, contentDescription = Strings.notificationsHeader())
            }
        }
    }
}

@Composable
private fun SearchField(value: String, placeholder: String? = null, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder ?: Strings.searchVisitsPlaceholder()) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun EmptyHint(text: String) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VisitStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    iconBg: Color,
    value: String,
    label: String,
    footer: String? = null
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = iconBg) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (footer != null) {
                Spacer(Modifier.height(2.dp))
                Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One visit. Tapping the pin icon opens the saved GPS location in Google Maps. */
@Composable
private fun VisitRow(
    visit: VisitEntry,
    showUserName: Boolean,
    timeFmt: SimpleDateFormat,
    dateFmt: SimpleDateFormat,
    flat: Boolean = false
) {
    val context = LocalContext.current
    val lat = visit.lat
    val lng = visit.lng

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (flat) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (flat) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                if (showUserName && visit.userName.isNotBlank()) {
                    Text(visit.userName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    visit.leadName.ifBlank { "-" } + if (visit.businessType.isNotBlank()) "  •  ${visit.businessType}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (visit.address.isNotBlank()) {
                    val canOpen = lat != null && lng != null
                    val linkColor = if (canOpen) AccentBlue else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (lat != null && lng != null)
                                    Modifier.clickable { openInMaps(context, lat, lng, visit.leadName) }
                                else Modifier
                            )
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Place, null, Modifier.size(14.dp).padding(top = 1.dp), tint = linkColor)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            shortLocation(visit.address),
                            style = MaterialTheme.typography.bodySmall,
                            color = linkColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (visit.nextVisitDate != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(13.dp), tint = AccentOrange)
                        Spacer(Modifier.width(3.dp))
                        Text(
                            Strings.reminderChipInline(dateFmt.format(Date(visit.nextVisitDate))),
                            style = MaterialTheme.typography.labelSmall,
                            color = AccentOrange
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(timeFmt.format(Date(visit.visitTime)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

internal fun openInMaps(context: Context, lat: Double, lng: Double, label: String) {
    val geo = Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
    } catch (e: ActivityNotFoundException) {
        // No maps app → open in the browser instead.
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$lat,$lng"))
            )
        } catch (e: ActivityNotFoundException) { }
    }
}

private fun isToday(millis: Long): Boolean {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(Date(millis)) == fmt.format(Date())
}

/** Shows at most [maxWords] words of a place name; longer names end with "…". */
internal fun shortLocation(text: String, maxWords: Int = 10): String {
    val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return if (words.size <= maxWords) words.joinToString(" ")
    else words.take(maxWords).joinToString(" ") + "…"
}