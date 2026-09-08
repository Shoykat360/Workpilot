package com.example.workpilotmini.ui.visit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class VisitFilterTab { ALL, TODAY, WEEK }

/**
 * Redesigned "Visit" tab landing screen. Shows a stats overview + searchable/filterable
 * list of visit entries. [isAdmin] only changes the header subtitle and whose visits are
 * shown — the actual scoping (team-wide vs own-only) already happens inside
 * [VisitViewModel.startListening], which must be called by the caller before this screen
 * is shown (same as the old history screen did).
 *
 * Both admin and member can tap the "Add Visit" button to open [VisitEntryScreen] and log
 * a new visit with an optional next-visit reminder — the admin is not restricted here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitHistoryScreen(
    viewModel: VisitViewModel,
    isAdmin: Boolean,
    onAddVisit: () -> Unit,
    onOpenNotifications: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(VisitFilterTab.ALL) }

    val dateTimeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val allVisits = state.visits
    val todayVisits = remember(allVisits) { allVisits.filter { isToday(it.visitTime) } }
    val weekVisits = remember(allVisits) { allVisits.filter { isWithinLastDays(it.visitTime, 7) } }
    val distinctLocations = remember(allVisits) {
        allVisits.map { it.address }.filter { it.isNotBlank() }.distinct().size
    }

    val tabScoped = when (selectedTab) {
        VisitFilterTab.ALL -> allVisits
        VisitFilterTab.TODAY -> todayVisits
        VisitFilterTab.WEEK -> weekVisits
    }
    val visibleVisits = remember(tabScoped, searchQuery) {
        if (searchQuery.isBlank()) tabScoped
        else tabScoped.filter { v ->
            v.leadName.contains(searchQuery, ignoreCase = true) ||
                    v.businessType.contains(searchQuery, ignoreCase = true) ||
                    v.address.contains(searchQuery, ignoreCase = true) ||
                    v.userName.contains(searchQuery, ignoreCase = true)
        }.sortedByDescending { it.visitTime }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddVisit,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(Strings.addVisitFabLabel()) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
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
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AccentBlueBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = AccentBlue)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            Strings.visitEntryHeader(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isAdmin) Strings.visitHistoryHeaderAdmin() else Strings.visitHistoryHeaderMember(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (isAdmin) {
                    AssistChip(onClick = {}, enabled = false, label = { Text(Strings.adminBadge()) })
                }
                if (onOpenNotifications != null) {
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Default.Notifications, contentDescription = Strings.notificationsHeader())
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- Stats row ----
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Place,
                    iconColor = AccentBlue,
                    iconBg = AccentBlueBg,
                    value = allVisits.size.toString(),
                    label = Strings.visitStatTotal(),
                    footer = Strings.visitStatTodayInline(todayVisits.size)
                )
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.CalendarMonth,
                    iconColor = AccentGreen,
                    iconBg = AccentGreenBg,
                    value = todayVisits.size.toString(),
                    label = Strings.visitStatTodayLabel()
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Notifications,
                    iconColor = AccentOrange,
                    iconBg = AccentOrangeBg,
                    value = state.upcomingReminders.size.toString(),
                    label = Strings.visitStatRemindersLabel()
                )
                VisitStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Place,
                    iconColor = AccentViolet,
                    iconBg = AccentVioletBg,
                    value = distinctLocations.toString(),
                    label = Strings.visitStatLocationsLabel()
                )
            }

            Spacer(Modifier.height(16.dp))

            // ---- Search ----
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(Strings.searchVisitsPlaceholder()) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // ---- Filter tabs ----
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == VisitFilterTab.ALL,
                    onClick = { selectedTab = VisitFilterTab.ALL },
                    label = { Text(Strings.filterAllVisitsCount(allVisits.size)) }
                )
                FilterChip(
                    selected = selectedTab == VisitFilterTab.TODAY,
                    onClick = { selectedTab = VisitFilterTab.TODAY },
                    label = { Text(Strings.filterTodayVisitsCount(todayVisits.size)) }
                )
                FilterChip(
                    selected = selectedTab == VisitFilterTab.WEEK,
                    onClick = { selectedTab = VisitFilterTab.WEEK },
                    label = { Text(Strings.filterWeekVisitsCount(weekVisits.size)) }
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- List ----
            if (visibleVisits.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        Strings.noVisitsForFilter(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(visibleVisits, key = { it.id.ifBlank { it.visitTime.toString() } }) { visit ->
                        VisitRow(visit = visit, isAdmin = isAdmin, dateTimeFormat = dateTimeFormat, dateFormat = dateFormat)
                    }
                }
            }
        }
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = iconBg
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
            if (footer != null) {
                Spacer(Modifier.height(2.dp))
                Text(footer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VisitRow(
    visit: VisitEntry,
    isAdmin: Boolean,
    dateTimeFormat: SimpleDateFormat,
    dateFormat: SimpleDateFormat
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentBlueBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (visit.userName.trim().firstOrNull() ?: visit.leadName.trim().firstOrNull() ?: '?')
                        .uppercaseChar().toString(),
                    color = AccentBlue,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                if (isAdmin && visit.userName.isNotBlank()) {
                    Text(visit.userName, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    visit.leadName.ifBlank { "-" } + if (visit.businessType.isNotBlank()) "  •  ${visit.businessType}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (visit.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(3.dp))
                        Text(visit.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(dateTimeFormat.format(Date(visit.visitTime)), style = MaterialTheme.typography.bodySmall)
                if (visit.nextVisitDate != null) {
                    Spacer(Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(Strings.reminderChipInline(dateFormat.format(Date(visit.nextVisitDate))), style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }
}

private fun isToday(millis: Long): Boolean {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return fmt.format(Date(millis)) == fmt.format(Date())
}

private fun isWithinLastDays(millis: Long, days: Int): Boolean {
    val cutoff = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
    }.timeInMillis
    return millis >= cutoff
}