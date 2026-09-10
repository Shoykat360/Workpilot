package com.example.workpilotmini.ui.team

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.data.AttendanceRepository
import com.example.workpilotmini.localization.Strings
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Team management screen. Admin sees a stats row (Total/Checked-in today/Not checked-in
 * today/Invite code+Share), search, an "Add Member" button that opens a bottom sheet, and
 * per-member kebab-menu actions (activate/deactivate, remove, delete). A regular member
 * sees just the invite-code card (with Share) and a locked "you can't add members" notice
 * instead of any add controls.
 *
 * "Checked in today" is read live from [AttendanceRepository] — team.teamId is always the
 * data owner here (this screen only exists once a team exists, never for solo users).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamManageScreen(
    viewModel: TeamViewModel,
    team: Team,
    myUid: String,
    isAdmin: Boolean,
    onOpenMemberReport: (uid: String, name: String) -> Unit = { _, _ -> },
    onOpenNotifications: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val joinedDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val checkInTimeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }

    var searchQuery by remember { mutableStateOf("") }
    var showAddMemberSheet by remember { mutableStateOf(false) }
    var memberPendingDelete by remember { mutableStateOf<UserProfile?>(null) }
    var email by remember { mutableStateOf("") }

    LaunchedEffect(team.teamId) { viewModel.loadMembers(team) }

    // Live "checked in today" status per member — admin only.
    val attendanceRepo = remember { AttendanceRepository() }
    var todayCheckIns by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    DisposableEffect(team.teamId, isAdmin) {
        if (isAdmin) {
            val registration = attendanceRepo.listenToTodayAttendance(team.teamId, false, myUid, true) { list ->
                todayCheckIns = list.associate { it.uid to it.checkInTime }
            }
            onDispose { registration.remove() }
        } else {
            onDispose { }
        }
    }
    val checkedInCount = team.memberUids.count { it in todayCheckIns.keys }
    val notCheckedInCount = (team.memberUids.size - checkedInCount).coerceAtLeast(0)

    val visibleMembers = remember(state.members, searchQuery) {
        if (searchQuery.isBlank()) state.members
        else state.members.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.email.contains(searchQuery, ignoreCase = true)
        }
    }

    fun shareInviteCode() {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, Strings.shareInviteCodeMessage(team.name, team.inviteCode))
        }
        context.startActivity(Intent.createChooser(sendIntent, null))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                // ---- Header ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(team.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (isAdmin) Strings.teamOverviewSubtitleAdmin() else Strings.memberCount(team.memberUids.size, team.maxSize),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isAdmin) {
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

                if (isAdmin) {
                    // ---- Admin stat row ----
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TeamStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Group,
                            accent = AccentBlue,
                            accentBg = AccentBlueBg,
                            value = team.memberUids.size.toString(),
                            label = Strings.totalMembersLabel(),
                            footer = Strings.viewAllInline()
                        )
                        TeamStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.CheckCircle,
                            accent = AccentGreen,
                            accentBg = AccentGreenBg,
                            value = checkedInCount.toString(),
                            label = Strings.checkedInStatLabel(),
                            footer = Strings.percentInline(
                                if (team.memberUids.isNotEmpty()) (checkedInCount * 100 / team.memberUids.size) else 0
                            )
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        TeamStatCard(
                            modifier = Modifier.weight(1f),
                            icon = Icons.Filled.Cancel,
                            accent = AccentOrange,
                            accentBg = AccentOrangeBg,
                            value = notCheckedInCount.toString(),
                            label = Strings.notCheckedInStatLabel(),
                            footer = Strings.percentInline(
                                if (team.memberUids.isNotEmpty()) (notCheckedInCount * 100 / team.memberUids.size) else 0
                            )
                        )
                        InviteCodeStatCard(
                            modifier = Modifier.weight(1f),
                            code = team.inviteCode,
                            onShare = ::shareInviteCode
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(Strings.searchMembersPlaceholder()) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Strings.teamMembersHeaderCount(team.memberUids.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Button(onClick = { showAddMemberSheet = true }, shape = RoundedCornerShape(10.dp)) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(Strings.addMemberButtonLabel())
                        }
                    }

                    state.addMemberSuccessMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    state.memberActionMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    state.errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    // ---- Member view: just the invite-code card ----
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(Strings.inviteCodeManageTitle(), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    team.inviteCode,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = ::shareInviteCode, shape = RoundedCornerShape(10.dp)) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(Strings.shareAction())
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(Strings.inviteCodeShareHint(), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (team.location.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(Strings.teamLocationInline(team.location), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(Strings.membersHeader(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                }
            }

            items(visibleMembers, key = { it.uid }) { member ->
                Spacer(Modifier.height(10.dp))
                MemberRow(
                    member = member,
                    isAdmin = isAdmin,
                    isSelf = member.uid == myUid,
                    isOwner = member.uid == team.adminUid,
                    joinedDateText = joinedDateFormat.format(Date(member.createdAt)),
                    checkInTimeText = todayCheckIns[member.uid]?.let { checkInTimeFormat.format(Date(it)) },
                    busy = state.memberActionInProgressUid == member.uid,
                    onOpenReport = { if (isAdmin) onOpenMemberReport(member.uid, member.name.ifBlank { member.email }) },
                    onToggleActive = { viewModel.setMemberActive(team, member.uid, !member.isActive, myUid) },
                    onRemove = { viewModel.removeMember(team, member.uid, myUid) },
                    onDeleteRequest = { memberPendingDelete = member }
                )
            }

            if (isAdmin && visibleMembers.isEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        Strings.noMembersFound(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isAdmin) {
                item {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentVioletBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Lock, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(Strings.cannotAddMembersTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(Strings.cannotAddMembersSubtitle(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    // ---- Add Member bottom sheet (admin only) ----
    if (showAddMemberSheet) {
        ModalBottomSheet(onDismissRequest = { showAddMemberSheet = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(Strings.addMemberSheetTitle(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(Strings.addByEmailSectionTitle(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(Strings.addByEmailSectionHint(), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("member@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.addMemberByEmail(team, email, myUid) },
                    enabled = !state.isAddingMember,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isAddingMember) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(Strings.add())
                    }
                }
                state.addMemberSuccessMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.primary)
                    LaunchedEffect(it) {
                        email = ""
                        showAddMemberSheet = false
                    }
                }
                state.errorMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(16.dp))
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

@Composable
private fun TeamStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    accentBg: Color,
    value: String,
    label: String,
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
private fun InviteCodeStatCard(modifier: Modifier = Modifier, code: String, onShare: () -> Unit) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = AccentVioletBg) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(32.dp).clip(CircleShape).background(AccentViolet.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = AccentViolet, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = AccentViolet)
            Text(Strings.inviteCodeManageTitle(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onShare, contentPadding = PaddingValues(0.dp)) {
                Text(Strings.shareAction(), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun MemberRow(
    member: UserProfile,
    isAdmin: Boolean,
    isSelf: Boolean,
    isOwner: Boolean,
    joinedDateText: String,
    checkInTimeText: String?,
    busy: Boolean,
    onOpenReport: () -> Unit,
    onToggleActive: () -> Unit,
    onRemove: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        onClick = onOpenReport,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(42.dp).clip(CircleShape).background(AccentBlueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (member.role == "admin") Icons.Filled.AdminPanelSettings else Icons.Filled.Person,
                    contentDescription = member.role,
                    tint = AccentBlue
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.name.ifBlank { member.email }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (isOwner) {
                        Spacer(Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = AccentVioletBg) {
                            Text(
                                Strings.teamOwnerTag(),
                                style = MaterialTheme.typography.labelSmall,
                                color = AccentViolet,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(member.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Strings.joinedInline(joinedDateText), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!member.isActive) {
                    Text(Strings.deactivatedLabel(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (isAdmin) {
                Column(horizontalAlignment = Alignment.End) {
                    val checkedIn = checkInTimeText != null
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (checkedIn) AccentGreenBg else AccentOrangeBg
                    ) {
                        Text(
                            if (checkedIn) Strings.checkedInStatLabel() else Strings.notCheckedInStatLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (checkedIn) AccentGreen else AccentOrange,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(checkInTimeText ?: "--:--", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Kebab menu — hidden entirely for the admin's own row (can't act on self)
            // and for non-admin viewers (they get no actions at all).
            if (isAdmin && !isSelf) {
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = !busy) {
                        if (busy) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.MoreVert, contentDescription = Strings.memberActionsDescription())
                        }
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(if (member.isActive) Strings.deactivateAction() else Strings.activateAction()) },
                            onClick = { menuExpanded = false; onToggleActive() }
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.removeAction()) },
                            onClick = { menuExpanded = false; onRemove() }
                        )
                        DropdownMenuItem(
                            text = { Text(Strings.deleteAction(), color = MaterialTheme.colorScheme.error) },
                            onClick = { menuExpanded = false; onDeleteRequest() }
                        )
                    }
                }
            }
        }
    }
}