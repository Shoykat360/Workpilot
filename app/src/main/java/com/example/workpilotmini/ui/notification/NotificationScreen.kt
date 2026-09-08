package com.example.workpilotmini.ui.notification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.ui.visit.VisitViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Reminders due within the next 2 days only — "who, when, where" — opened from the
 *  bell icon on the Dashboard. Admin sees the whole team's near-due reminders; a
 *  regular member or solo user only ever sees their own. */
private const val NEAR_WINDOW_MILLIS = 2 * 24 * 60 * 60 * 1000L

@Composable
fun NotificationScreen(
    viewModel: VisitViewModel,
    ownerId: String,
    isSolo: Boolean,
    uid: String,
    isAdmin: Boolean
) {
    val state by viewModel.state.collectAsState()
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    LaunchedEffect(ownerId, uid) { viewModel.startListening(ownerId, isSolo, uid, isAdmin) }

    val nearReminders = remember(state.upcomingReminders) {
        val cutoff = System.currentTimeMillis() + NEAR_WINDOW_MILLIS
        state.upcomingReminders.filter { it.nextVisitDate != null && it.nextVisitDate <= cutoff }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Text(
            Strings.notificationsHeader(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(Strings.notificationsSubtitle(), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        if (nearReminders.isEmpty()) {
            Text(Strings.noNearReminders())
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(nearReminders) { visit ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(visit.leadName, style = MaterialTheme.typography.titleMedium)
                                if (isAdmin) {
                                    Text(
                                        visit.userName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                visit.nextVisitDate?.let {
                                    Text(
                                        Strings.nextVisitLabel(dateFormat.format(Date(it))),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                val addressOrCoords = visit.address.ifBlank {
                                    if (visit.lat != null && visit.lng != null) "${visit.lat}, ${visit.lng}" else ""
                                }
                                if (addressOrCoords.isNotBlank()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Filled.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.secondary
                                        )
                                        Spacer(Modifier.width(2.dp))
                                        Text(addressOrCoords, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Just the count, for the bell icon's badge on the Dashboard — no UI, pure derivation. */
fun nearReminderCount(upcomingReminders: List<com.example.workpilotmini.model.VisitEntry>): Int {
    val cutoff = System.currentTimeMillis() + NEAR_WINDOW_MILLIS
    return upcomingReminders.count { it.nextVisitDate != null && it.nextVisitDate <= cutoff }
}
