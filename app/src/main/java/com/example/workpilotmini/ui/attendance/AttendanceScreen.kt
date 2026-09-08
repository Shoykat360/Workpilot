package com.example.workpilotmini.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.AttendanceRecord
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.UserProfile
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
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AttendanceScreen(
    viewModel: AttendanceViewModel,
    ownerId: String,
    isSolo: Boolean,
    uid: String,
    userName: String,
    isAdmin: Boolean,
    team: Team? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val coordFormat = remember { DecimalFormat("0.00000") }

    LaunchedEffect(ownerId, team?.teamId, isAdmin) { viewModel.start(ownerId, isSolo, uid, isAdmin, team) }
    LaunchedEffect(Unit) { viewModel.captureLocation(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        // ---- Header ----
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column {
                Text(Strings.attendanceHeader(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    if (isAdmin) Strings.attendanceSubtitleAdmin() else Strings.attendanceSubtitleMember(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isAdmin) AdminBadge()
        }
        Spacer(Modifier.height(20.dp))

        // ---- Self check-in / check-out (everyone — admin included) ----
        SelfCheckInOutSection(
            state = state,
            isAdmin = isAdmin,
            timeFormat = timeFormat,
            coordFormat = coordFormat,
            onRetryLocation = { viewModel.captureLocation(context) },
            onCheckIn = { viewModel.checkIn(context, ownerId, isSolo, uid, userName) },
            onCheckOut = { viewModel.checkOut(context, ownerId, isSolo, uid) }
        )

        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // ---- Admin: team stats + search/filter + member list ----
        if (isAdmin) {
            Spacer(Modifier.height(28.dp))
            AdminStatsGrid(state = state)

            Spacer(Modifier.height(16.dp))
            TodayDatePill(dateFormat = dateFormat)

            Spacer(Modifier.height(12.dp))
            SearchField(
                query = state.searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) }
            )

            Spacer(Modifier.height(12.dp))
            FilterChipsRow(state = state, onFilterChange = { viewModel.setFilter(it) })

            Spacer(Modifier.height(16.dp))
            when {
                state.isLoadingTeam -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.visibleRows.isEmpty() -> Text(
                    Strings.noMembersFound(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.visibleRows.forEach { row ->
                        MemberAttendanceCard(row = row, timeFormat = timeFormat, coordFormat = coordFormat)
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ============================================================================
// Self check-in / check-out
// ============================================================================

@Composable
private fun SelfCheckInOutSection(
    state: AttendanceUiState,
    isAdmin: Boolean,
    timeFormat: SimpleDateFormat,
    coordFormat: DecimalFormat,
    onRetryLocation: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit
) {
    if (isAdmin) {
        AdminSelfStatusCard(
            state = state,
            timeFormat = timeFormat,
            onCheckIn = onCheckIn,
            onCheckOut = onCheckOut
        )
        return
    }

    // ---- Full member-facing design (fingerprint circle, location, summary, activity) ----
    if (!state.checkedInToday || !state.checkedOutToday) {
        LocationStatusCard(state = state, onRetry = onRetryLocation)
        Spacer(Modifier.height(16.dp))
    }

    when {
        !state.checkedInToday -> BigActionCircle(
            icon = Icons.Filled.Fingerprint,
            isLoading = state.isLoading,
            enabled = !state.isLoading,
            onClick = onCheckIn
        )
        !state.checkedOutToday -> BigActionCircle(
            icon = Icons.Filled.Logout,
            isLoading = state.isCheckingOut,
            enabled = !state.isCheckingOut,
            onClick = onCheckOut
        )
        else -> DoneBanner()
    }

    if (!state.checkedInToday || !state.checkedOutToday) {
        Spacer(Modifier.height(10.dp))
        Text(
            Strings.tapToCheckInOutHint(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }

    Spacer(Modifier.height(20.dp))
    AttendanceSummaryRow(record = state.myRecord, timeFormat = timeFormat)

    Spacer(Modifier.height(16.dp))
    AttendanceMotivationBanner()

    if (state.myRecord != null) {
        Spacer(Modifier.height(16.dp))
        TodaysActivityCard(record = state.myRecord!!, timeFormat = timeFormat)
    }
}

@Composable
private fun LocationStatusCard(state: AttendanceUiState, onRetry: () -> Unit) {
    val coordFormat = remember { DecimalFormat("0.00000") }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(Strings.currentLocationLabel(), style = MaterialTheme.typography.labelMedium)
                when {
                    state.isLocating -> Text(Strings.locating(), style = MaterialTheme.typography.bodyMedium)
                    state.capturedLat != null && state.capturedLng != null -> Text(
                        "${coordFormat.format(state.capturedLat)}, ${coordFormat.format(state.capturedLng)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    state.locationError != null -> Text(
                        state.locationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(Strings.locationNotFound(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (state.isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRetry) { Text(Strings.retry()) }
            }
        }
    }
}

/** The big tappable circle from the reference design — used for both check-in and
 *  check-out (icon changes: fingerprint to check in, logout arrow to check out). */
@Composable
private fun BigActionCircle(icon: ImageVector, isLoading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .clip(CircleShape)
                .background(BrandGradientStart.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
                    .clickable(enabled = enabled, onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
                } else {
                    Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(Strings.tapToCheckInOut(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DoneBanner() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(AccentGreenBg, shape = MaterialTheme.shapes.medium)
            .padding(16.dp)
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentGreen)
        Spacer(Modifier.width(8.dp))
        Text(Strings.checkOutDone())
    }
}

@Composable
private fun AttendanceSummaryRow(record: AttendanceRecord?, timeFormat: SimpleDateFormat) {
    val (statusText, statusColor) = when {
        record?.checkOutTime != null -> Strings.statusCheckedOut() to AccentBlue
        record != null -> Strings.statusCheckedIn() to AccentGreen
        else -> Strings.statusNotCheckedIn() to AccentOrange
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(vertical = 14.dp)
    ) {
        SummaryColumn(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.HourglassEmpty,
            label = Strings.lastCheckInLabel(),
            value = record?.let { timeFormat.format(Date(it.checkInTime)) } ?: Strings.notYet()
        )
        SummaryColumn(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LocationOn,
            label = Strings.locationSummaryLabel(),
            value = if (record?.lat != null) Strings.statusCheckedIn() else Strings.notYet()
        )
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Strings.statusSummaryLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SummaryColumn(modifier: Modifier = Modifier, icon: ImageVector, label: String, value: String) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AttendanceMotivationBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AccentBlueBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = AccentBlue)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(Strings.attendanceBannerTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(Strings.attendanceBannerSubtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodaysActivityCard(record: AttendanceRecord, timeFormat: SimpleDateFormat) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Strings.todaysActivityHeader(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            ActivityRow(dotColor = AccentGreen, label = Strings.checkInActivityLabel(), time = timeFormat.format(Date(record.checkInTime)))
            record.checkOutTime?.let {
                Spacer(Modifier.height(8.dp))
                ActivityRow(dotColor = AccentBlue, label = Strings.checkOutActivityLabel(), time = timeFormat.format(Date(it)))
            }
        }
    }
}

@Composable
private fun ActivityRow(dotColor: Color, label: String, time: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============================================================================
// Admin: own status (compact) + stats + search/filter + member list
// ============================================================================

@Composable
private fun AdminBadge() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Workspaces, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(Strings.adminBadge(), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** Compact self check-in/out card shown to the admin — same underlying action as the
 *  member's big circle, just condensed so it doesn't crowd out the team stats below. */
@Composable
private fun AdminSelfStatusCard(
    state: AttendanceUiState,
    timeFormat: SimpleDateFormat,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit
) {
    val (statusText, statusColor) = when {
        state.checkedOutToday -> Strings.statusCheckedOut() to AccentBlue
        state.checkedInToday -> Strings.statusCheckedIn() to AccentGreen
        else -> Strings.statusNotCheckedIn() to AccentOrange
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(Strings.myStatusHeader(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                    state.myRecord?.let { record ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            Strings.checkInTimeInline(timeFormat.format(Date(record.checkInTime))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        record.checkOutTime?.let {
                            Text(
                                "  " + Strings.checkOutTimeInline(timeFormat.format(Date(it))),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            when {
                !state.checkedInToday -> Button(onClick = onCheckIn, enabled = !state.isLoading) {
                    if (state.isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                    else Text(Strings.checkInNow())
                }
                !state.checkedOutToday -> OutlinedButton(onClick = onCheckOut, enabled = !state.isCheckingOut) {
                    if (state.isCheckingOut) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(Strings.checkOutNow())
                }
                else -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentGreen)
            }
        }
    }
}

@Composable
private fun AdminStatsGrid(state: AttendanceUiState) {
    val checkedInPercent = if (state.totalTeamMembers > 0) (state.checkedInCount * 100 / state.totalTeamMembers) else 0
    val notCheckedInPercent = if (state.totalTeamMembers > 0) (state.notCheckedInCount * 100 / state.totalTeamMembers) else 0

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Group,
            iconColor = AccentGreen,
            bgColor = AccentGreenBg,
            value = state.totalTeamMembers.toString(),
            label = Strings.statTotalTeamLabel(),
            sub = Strings.statActiveInline(state.activeTeamMembers)
        )
        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.CheckCircle,
            iconColor = AccentBlue,
            bgColor = AccentBlueBg,
            value = state.checkedInCount.toString(),
            label = Strings.statCheckedInAdminLabel(),
            sub = Strings.percentOfTotal(checkedInPercent)
        )
    }
    Spacer(Modifier.height(10.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.HourglassEmpty,
            iconColor = AccentOrange,
            bgColor = AccentOrangeBg,
            value = state.notCheckedInCount.toString(),
            label = Strings.statNotCheckedInLabel(),
            sub = Strings.percentOfTotal(notCheckedInPercent)
        )
        AttendanceStatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.LocationOn,
            iconColor = AccentViolet,
            bgColor = AccentVioletBg,
            value = state.todayVisitCount.toString(),
            label = Strings.statTodaysVisitsLabel(),
            sub = null
        )
    }
}

@Composable
private fun AttendanceStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color,
    bgColor: Color,
    value: String,
    label: String,
    sub: String?
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .padding(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = iconColor)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        sub?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TodayDatePill(dateFormat: SimpleDateFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(dateFormat.format(Date()), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(Strings.searchMembersPlaceholder(), style = MaterialTheme.typography.bodySmall) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = Strings.clear())
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun FilterChipsRow(state: AttendanceUiState, onFilterChange: (AttendanceFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        FilterChip(
            selected = state.filter == AttendanceFilter.ALL,
            onClick = { onFilterChange(AttendanceFilter.ALL) },
            label = { Text(Strings.filterAllCount(state.totalTeamMembers)) }
        )
        FilterChip(
            selected = state.filter == AttendanceFilter.CHECKED_IN,
            onClick = { onFilterChange(AttendanceFilter.CHECKED_IN) },
            label = { Text(Strings.filterCheckedInCount(state.checkedInCount)) }
        )
        FilterChip(
            selected = state.filter == AttendanceFilter.NOT_CHECKED_IN,
            onClick = { onFilterChange(AttendanceFilter.NOT_CHECKED_IN) },
            label = { Text(Strings.filterNotCheckedInCount(state.notCheckedInCount)) }
        )
    }
}

@Composable
private fun MemberAttendanceCard(row: MemberAttendanceRow, timeFormat: SimpleDateFormat, coordFormat: DecimalFormat) {
    val record = row.record
    val (statusText, statusColor) = if (record != null) {
        if (record.checkOutTime != null) Strings.statusCheckedOut() to AccentBlue
        else Strings.statusCheckedIn() to AccentGreen
    } else {
        Strings.statusNotCheckedIn() to AccentOrange
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(BrandGradientStart.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(row.profile.name), fontWeight = FontWeight.Bold, color = BrandGradientStart)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(row.profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (row.profile.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(2.dp))
                        Text(row.profile.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (row.profile.mobile.isNotBlank()) {
                    Text(row.profile.mobile, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(statusColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.SemiBold)
                }
                if (record != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        Strings.checkInTimeInline(timeFormat.format(Date(record.checkInTime))),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    record.checkOutTime?.let {
                        Text(
                            Strings.checkOutTimeInline(timeFormat.format(Date(it))),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "?" }
