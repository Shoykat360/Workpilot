package com.example.workpilotmini.ui.attendance

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// How long the user must hold the fingerprint circle before it fires (matches a
// real fingerprint-scan feel, and prevents accidental taps from checking someone in/out).
private const val HOLD_DURATION_MS = 2000

// Safety net: stop showing the loader after this long even if no data ever arrives.
private const val LOAD_TIMEOUT_MS = 6000L

// The loading screen is always shown at least this long, so it never flashes for a few ms.
private const val MIN_LOADING_MS = 1000L

// Shared corner radius for the card-style surfaces below.
private val CardShape = RoundedCornerShape(20.dp)

// Content never gets wider than this — on tablets / landscape / foldables the column
// stays centered and readable instead of stretching edge to edge.
private val MaxContentWidth = 640.dp

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

    LaunchedEffect(ownerId, team?.teamId, isAdmin) {
        viewModel.start(
            ownerId,
            isSolo,
            uid,
            isAdmin,
            team
        )
    }
    LaunchedEffect(Unit) { viewModel.captureLocation(context) }

    // Loading = real data flag from the ViewModel, but never shorter than MIN_LOADING_MS
    // and never longer than LOAD_TIMEOUT_MS (safety net if the listener never answers).
    var minElapsed by remember { mutableStateOf(false) }
    var timedOut by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(MIN_LOADING_MS)
        minElapsed = true
    }
    LaunchedEffect(Unit) {
        delay(LOAD_TIMEOUT_MS)
        timedOut = true
    }
    val isLoadingScreen = (!minElapsed || state.isLoadingMyRecord) && !timedOut

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight

        // Side padding scales with the screen: tight on small phones, roomy on tablets.
        val horizontalPadding = when {
            screenWidth < 360.dp -> 14.dp
            screenWidth < 600.dp -> 20.dp
            else -> 32.dp
        }
        // Fingerprint circle scales with width, but is also capped by height so it never
        // eats the whole screen in landscape or on very short devices.
        val circleSize = minOf(screenWidth * 0.55f, screenHeight * 0.38f).coerceIn(140.dp, 240.dp)

        // While loading, ONLY the loader is composed (centered on screen) — the heavy
        // content isn't built until the data is ready, then it fades in.
        Crossfade(
            targetState = isLoadingScreen,
            animationSpec = tween(250),
            label = "attendanceLoadCrossfade"
        ) { loading ->
            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    AttendanceLoadingState(size = circleSize)
                }
            } else
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = MaxContentWidth)
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = horizontalPadding, vertical = 20.dp)
                    ) {
                        // ---- Header ----
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    Strings.attendanceHeader(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CalendarToday,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        dateFormat.format(Date()),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isAdmin) {
                                Spacer(Modifier.width(8.dp))
                                AdminBadge()
                            }
                        }
                        Spacer(Modifier.height(20.dp))

                        // ---- Self check-in / check-out — same fingerprint-hold flow for everyone ----
                        SelfCheckInOutSection(
                            state = state,
                            timeFormat = timeFormat,
                            coordFormat = coordFormat,
                            circleSize = circleSize,
                            onRetryLocation = { viewModel.captureLocation(context) },
                            onCheckIn = {
                                viewModel.checkIn(
                                    context,
                                    ownerId,
                                    isSolo,
                                    uid,
                                    userName
                                )
                            },
                            onCheckOut = { viewModel.checkOut(context, ownerId, isSolo, uid) }
                        )

                        state.errorMessage?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        // ---- Admin: search/filter + member list ----
                        if (isAdmin) {
                            /*Spacer(Modifier.height(28.dp))
                            AdminStatsGrid(state = state)*/

                            // (date now lives in the header for everyone, so TodayDatePill is not shown here)
                            Spacer(Modifier.height(28.dp))
                            SearchField(
                                query = state.searchQuery,
                                onQueryChange = { viewModel.setSearchQuery(it) }
                            )

                            Spacer(Modifier.height(12.dp))
                            FilterChipsRow(
                                state = state,
                                onFilterChange = { viewModel.setFilter(it) })

                            Spacer(Modifier.height(16.dp))
                            when {
                                state.isLoadingTeam -> Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
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
                                        MemberAttendanceCard(
                                            row = row,
                                            timeFormat = timeFormat,
                                            coordFormat = coordFormat
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
        }
    }
}

// ============================================================================
// Self check-in / check-out — one shared flow for member and admin alike
// ============================================================================

@Composable
private fun SelfCheckInOutSection(
    state: AttendanceUiState,
    timeFormat: SimpleDateFormat,
    coordFormat: DecimalFormat,
    circleSize: Dp,
    onRetryLocation: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit
) {
    var showConfirmCheckIn by remember { mutableStateOf(false) }
    var showConfirmCheckOut by remember { mutableStateOf(false) }

    if (showConfirmCheckIn) {
        LocationConfirmDialog(
            title = Strings.confirmCheckInTitle(),
            message = Strings.confirmCheckInMessage(),
            lat = state.capturedLat,
            lng = state.capturedLng,
            address = state.capturedAddress,
            isResolvingAddress = state.isResolvingAddress,
            coordFormat = coordFormat,
            onConfirm = { showConfirmCheckIn = false; onCheckIn() },
            onDismiss = { showConfirmCheckIn = false }
        )
    }
    if (showConfirmCheckOut) {
        LocationConfirmDialog(
            title = Strings.confirmCheckOutTitle(),
            message = Strings.confirmCheckOutMessage(),
            lat = state.capturedLat,
            lng = state.capturedLng,
            address = state.capturedAddress,
            isResolvingAddress = state.isResolvingAddress,
            coordFormat = coordFormat,
            onConfirm = { showConfirmCheckOut = false; onCheckOut() },
            onDismiss = { showConfirmCheckOut = false }
        )
    }

    if (!state.checkedInToday || !state.checkedOutToday) {
        LocationStatusCard(state = state, coordFormat = coordFormat, onRetry = onRetryLocation)
        Spacer(Modifier.height(20.dp))
    }

    when {
        !state.checkedInToday -> HoldToConfirmCircle(
            icon = Icons.Filled.Fingerprint,
            size = circleSize,
            isLoading = state.isLoading,
            enabled = !state.isLoading,
            onHoldComplete = { showConfirmCheckIn = true }
        )

        !state.checkedOutToday -> HoldToConfirmCircle(
            icon = Icons.Filled.Logout,
            size = circleSize,
            isLoading = state.isCheckingOut,
            enabled = !state.isCheckingOut,
            onHoldComplete = { showConfirmCheckOut = true }
        )
        /*else -> DoneBanner()*/
    }

    Spacer(Modifier.height(20.dp))
    AttendanceSummaryRow(record = state.myRecord, timeFormat = timeFormat)

    if (state.myRecord != null) {
        Spacer(Modifier.height(16.dp))
        TodaysActivityCard(
            record = state.myRecord!!,
            timeFormat = timeFormat,
            coordFormat = coordFormat
        )
    }
}

/** Shown right before a check-in/check-out actually commits — lets the person see
 *  (and, if they want, verify on a map) the exact location that will be saved. */
@Composable
private fun LocationConfirmDialog(
    title: String,
    message: String,
    lat: Double?,
    lng: Double?,
    address: String?,
    isResolvingAddress: Boolean,
    coordFormat: DecimalFormat,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (lat != null && lng != null) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentBlueBg)
                            .clickable {
                                uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lng")
                            }
                            .padding(14.dp)
                    ) {
                        LocationBadgeIcon()
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                Strings.capturedLocationLabel(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                address ?: "${coordFormat.format(lat)}, ${coordFormat.format(lng)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = AccentBlue,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (address != null) {
                                Text(
                                    "${coordFormat.format(lat)}, ${coordFormat.format(lng)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Strings.locationNotFound(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    Strings.confirm(),
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.cancel()) }
        }
    )
}

/** Small circular colored icon badge — reused wherever a location row needs a leading icon. */
@Composable
private fun LocationBadgeIcon(size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(AccentBlue.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = AccentBlue,
            modifier = Modifier.size(size * 0.55f)
        )
    }
}

@Composable
private fun LocationStatusCard(
    state: AttendanceUiState,
    coordFormat: DecimalFormat,
    onRetry: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val hasCoords = state.capturedLat != null && state.capturedLng != null
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, AccentBlue.copy(alpha = 0.18f), CardShape),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .let {
                    if (hasCoords) it.clickable {
                        uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=${state.capturedLat},${state.capturedLng}")
                    } else it
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LocationBadgeIcon(size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Strings.currentLocationLabel(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                when {
                    state.isLocating -> Text(
                        Strings.locating(),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    hasCoords -> Column {
                        Text(
                            state.capturedAddress ?: "${coordFormat.format(state.capturedLat)}, ${
                                coordFormat.format(
                                    state.capturedLng
                                )
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (state.capturedAddress != null) {
                            Text(
                                "${coordFormat.format(state.capturedLat)}, ${
                                    coordFormat.format(
                                        state.capturedLng
                                    )
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    state.locationError != null -> Text(
                        state.locationError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    else -> Text(
                        Strings.locationNotFound(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            if (state.isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onRetry) {
                    Text(
                        Strings.retry(),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** The big fingerprint-style circle. The user must press and hold for [HOLD_DURATION_MS];
 *  a ring fills around the icon while held, a haptic pulse confirms completion, and only
 *  then does it hand off to the confirmation dialog. Releasing early cancels and resets.
 *  While idle, a soft pulse ripples outward so it's obvious the circle is interactive.
 *  All dimensions derive from [size] so it scales with the device. */
@Composable
private fun HoldToConfirmCircle(
    icon: ImageVector,
    size: Dp,
    isLoading: Boolean,
    enabled: Boolean,
    onHoldComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(150),
        label = "holdScale"
    )

    val innerSize = size * 0.73f
    val ringSize = size * 0.91f

    // Idle ripple
    val pulse = rememberInfiniteTransition(label = "idlePulse")
    val pulseScale = pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha = pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing),
            RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )
    val showPulse = enabled && !isPressed && !isLoading

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            BrandGradientStart.copy(alpha = 0.20f),
                            BrandGradientStart.copy(alpha = 0.06f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (showPulse) {
                Box(
                    modifier = Modifier
                        .size(innerSize)
                        .graphicsLayer {
                            scaleX = pulseScale.value
                            scaleY = pulseScale.value
                            alpha = pulseAlpha.value
                        }
                        .background(BrandGradientStart, CircleShape)
                )
            }
            if (isPressed) {
                CircularProgressIndicator(
                    progress = progress.value,
                    modifier = Modifier.size(ringSize),
                    strokeWidth = 5.dp,
                    color = BrandGradientEnd,
                    trackColor = Color.Transparent
                )
            }
            Box(
                modifier = Modifier
                    .size(innerSize)
                    .scale(scale)
                    .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(BrandGradientStart, BrandGradientEnd)))
                    .pointerInput(enabled) {
                        if (!enabled) return@pointerInput
                        detectTapGestures(
                            onPress = {
                                isPressed = true
                                val holdJob = scope.launch {
                                    progress.snapTo(0f)
                                    progress.animateTo(
                                        1f,
                                        animationSpec = tween(
                                            HOLD_DURATION_MS,
                                            easing = LinearEasing
                                        )
                                    )
                                    // Only reached if the press was held for the full duration.
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onHoldComplete()
                                }
                                tryAwaitRelease()
                                holdJob.cancel()
                                isPressed = false
                                scope.launch { progress.animateTo(0f, animationSpec = tween(200)) }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(innerSize * 0.22f)
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(innerSize * 0.4f)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/*@Composable
private fun DoneBanner() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AccentGreenBg)
            .padding(18.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(AccentGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Text(Strings.checkOutDone(), fontWeight = FontWeight.Medium)
    }
}*/

/** Full-screen loader shown (centered) while today's attendance state loads. Every
 *  animated value is read inside graphicsLayer, so animating only redraws — it never
 *  triggers recomposition (cheaper than reading the values in the composable body). */
@Composable
private fun AttendanceLoadingState(size: Dp) {
    val transition = rememberInfiniteTransition(label = "attendanceLoading")
    val blink = transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "blink"
    )
    val breathe = transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            tween(800, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "breathe"
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        BrandGradientStart.copy(alpha = 0.20f),
                        BrandGradientStart.copy(alpha = 0.06f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(size * 0.91f),
            strokeWidth = 4.dp,
            color = BrandGradientEnd,
            trackColor = BrandGradientStart.copy(alpha = 0.12f)
        )
        Icon(
            Icons.Filled.Fingerprint,
            contentDescription = null,
            tint = BrandGradientStart,
            modifier = Modifier
                .size(size * 0.3f)
                .graphicsLayer {
                    alpha = blink.value
                    scaleX = breathe.value
                    scaleY = breathe.value
                }
        )
    }
}

/** Rounded status pill with a small leading dot — shared by the summary strip and member cards. */
@Composable
private fun StatusPill(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AttendanceSummaryRow(record: AttendanceRecord?, timeFormat: SimpleDateFormat) {
    val uriHandler = LocalUriHandler.current
    val (statusText, statusColor) = when {
        record?.checkOutTime != null -> Strings.statusCheckedOut() to AccentBlue
        record != null -> Strings.statusCheckedIn() to AccentGreen
        else -> Strings.statusNotCheckedIn() to AccentOrange
    }
    val hasLocation = record?.lat != null && record.lng != null

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryColumn(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.HourglassEmpty,
                iconColor = AccentGreen,
                label = Strings.lastCheckInLabel(),
                value = record?.let { timeFormat.format(Date(it.checkInTime)) } ?: Strings.notYet()
            )
            SummaryDivider()
            SummaryColumn(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.LocationOn,
                iconColor = AccentBlue,
                label = Strings.locationSummaryLabel(),
                value = if (hasLocation) (record?.address
                    ?: Strings.viewOnMap()) else Strings.notYet(),
                valueColor = if (hasLocation) AccentBlue else null,
                onClick = if (hasLocation) {
                    { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=${record!!.lat},${record.lng}") }
                } else null
            )
            SummaryDivider()
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    Strings.statusSummaryLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                StatusPill(text = statusText, color = statusColor)
            }
        }
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .height(38.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

@Composable
private fun SummaryColumn(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    label: String,
    value: String,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .let {
                if (onClick != null) it
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onClick) else it
            }
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TodaysActivityCard(
    record: AttendanceRecord,
    timeFormat: SimpleDateFormat,
    coordFormat: DecimalFormat
) {
    val uriHandler = LocalUriHandler.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(AccentViolet.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = AccentViolet,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    Strings.todaysActivityHeader(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(14.dp))

            Row {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimelineDot(AccentGreen)
                    if (record.checkOutTime != null) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(if (record.lat != null) 46.dp else 24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        )
                        TimelineDot(AccentBlue)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    ActivityRow(
                        label = Strings.checkInActivityLabel(),
                        time = timeFormat.format(Date(record.checkInTime))
                    )
                    if (record.lat != null && record.lng != null) {
                        Spacer(Modifier.height(4.dp))
                        MapLinkRow(
                            lat = record.lat,
                            lng = record.lng,
                            address = record.address,
                            coordFormat = coordFormat,
                            uriHandler = uriHandler
                        )
                    }
                    record.checkOutTime?.let {
                        Spacer(Modifier.height(14.dp))
                        ActivityRow(
                            label = Strings.checkOutActivityLabel(),
                            time = timeFormat.format(Date(it))
                        )
                        if (record.outLat != null && record.outLng != null) {
                            Spacer(Modifier.height(4.dp))
                            MapLinkRow(
                                lat = record.outLat,
                                lng = record.outLng,
                                address = record.outAddress,
                                coordFormat = coordFormat,
                                uriHandler = uriHandler
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineDot(color: Color) {
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(12.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ActivityRow(label: String, time: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Text(
            time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================================================
// Admin: badge + search/filter + member list
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
        Icon(
            Icons.Filled.Workspaces,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            Strings.adminBadge(),
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun AdminStatsGrid(state: AttendanceUiState) {
    val checkedInPercent =
        if (state.totalTeamMembers > 0) (state.checkedInCount * 100 / state.totalTeamMembers) else 0
    val notCheckedInPercent =
        if (state.totalTeamMembers > 0) (state.notCheckedInCount * 100 / state.totalTeamMembers) else 0

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
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = iconColor
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        sub?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Kept for later use; the date is now shown in the screen header.
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
        Icon(
            Icons.Filled.CalendarToday,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            dateFormat.format(Date()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                Strings.searchMembersPlaceholder(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
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

/** Filter chips: content-sized, tinted per status. If all three fit, the leftover space is
 *  shared equally so they fill the row; if they don't fit, the row scrolls horizontally.
 *  Text never truncates, and bigger counts simply widen the chip. */
@Composable
private fun FilterChipsRow(
    state: AttendanceUiState,
    onFilterChange: (AttendanceFilter) -> Unit
) {
    val filters = listOf(
        Triple(AttendanceFilter.ALL, Strings.filterAllCount(state.totalTeamMembers), AccentViolet),
        Triple(
            AttendanceFilter.CHECKED_IN,
            Strings.filterCheckedInCount(state.checkedInCount),
            AccentGreen
        ),
        Triple(
            AttendanceFilter.NOT_CHECKED_IN,
            Strings.filterNotCheckedInCount(state.notCheckedInCount),
            AccentOrange
        ),
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val containerWidth = maxWidth

        AdaptiveChipLayout(
            containerWidth = containerWidth,
            spacing = 8.dp,
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            filters.forEach { (filter, label, color) ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { onFilterChange(filter) },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = color.copy(alpha = 0.18f),
                        selectedLabelColor = color
                    ),
                    label = {
                        Text(
                            text = label,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            fontWeight = if (state.filter == filter) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun AdaptiveChipLayout(
    containerWidth: Dp,
    spacing: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, _ ->
        val spacingPx = spacing.roundToPx()
        val containerPx = containerWidth.roundToPx()

        // 1. Each chip's natural (content-based) width
        val naturalWidths = measurables.map { it.maxIntrinsicWidth(Int.MAX_VALUE) }
        val totalSpacing = spacingPx * (measurables.size - 1).coerceAtLeast(0)
        val naturalTotal = naturalWidths.sum() + totalSpacing

        // 2. Spare room is split equally; otherwise natural width (row scrolls)
        val extraPerChip =
            if (naturalTotal < containerPx) (containerPx - naturalTotal) / measurables.size else 0

        val placeables = measurables.mapIndexed { i, m ->
            val w = naturalWidths[i] + extraPerChip
            m.measure(Constraints(minWidth = w, maxWidth = w))
        }

        val width = placeables.sumOf { it.width } + totalSpacing
        val height = placeables.maxOfOrNull { it.height } ?: 0

        layout(width, height) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + spacingPx
            }
        }
    }
}

@Composable
private fun MemberAttendanceCard(
    row: MemberAttendanceRow,
    timeFormat: SimpleDateFormat,
    coordFormat: DecimalFormat
) {
    val uriHandler = LocalUriHandler.current
    val record = row.record
    val (statusText, statusColor) = if (record != null) {
        if (record.checkOutTime != null) Strings.statusCheckedOut() to AccentBlue
        else Strings.statusCheckedIn() to AccentGreen
    } else {
        Strings.statusNotCheckedIn() to AccentOrange
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        // Thin colored strip on the left = status at a glance while scrolling the list.
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(statusColor)
            )
            Column(Modifier
                .weight(1f)
                .padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        BrandGradientStart.copy(alpha = 0.22f),
                                        BrandGradientEnd.copy(alpha = 0.22f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initials(row.profile.name),
                            fontWeight = FontWeight.Bold,
                            color = BrandGradientStart
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            row.profile.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (row.profile.address.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    row.profile.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else if (row.profile.mobile.isNotBlank()) {
                            Text(
                                row.profile.mobile,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        StatusPill(text = statusText, color = statusColor)
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

                // Tap either recorded GPS point to open it directly in Google Maps.
                // `record` is null-checked first so Kotlin smart-casts it below.
                if (record != null) {
                    if (record.lat != null && record.lng != null) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.height(8.dp))
                        MapLinkRow(
                            label = Strings.checkInActivityLabel(),
                            lat = record.lat,
                            lng = record.lng,
                            address = record.address,
                            coordFormat = coordFormat,
                            uriHandler = uriHandler
                        )
                    }
                    if (record.outLat != null && record.outLng != null) {
                        Spacer(Modifier.height(6.dp))
                        MapLinkRow(
                            label = Strings.checkOutActivityLabel(),
                            lat = record.outLat,
                            lng = record.outLng,
                            address = record.outAddress,
                            coordFormat = coordFormat,
                            uriHandler = uriHandler
                        )
                    }
                }
            }
        }
    }
}

/** One tappable "address (or lat, lng)" row, reused for check-in and check-out locations.
 *  Prefers the resolved [address]; falls back to raw coordinates. Text truncates with an
 *  ellipsis instead of wrapping on narrow screens. */
@Composable
private fun MapLinkRow(
    lat: Double,
    lng: Double,
    coordFormat: DecimalFormat,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    address: String? = null,
    label: String? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { uriHandler.openUri("https://www.google.com/maps/search/?api=1&query=$lat,$lng") }
            .padding(vertical = 4.dp)
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = AccentBlue
        )
        Spacer(Modifier.width(4.dp))
        Text(
            (if (label != null) "$label: " else "") + (address
                ?: "${coordFormat.format(lat)}, ${coordFormat.format(lng)}"),
            style = MaterialTheme.typography.labelSmall,
            color = AccentBlue,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(6.dp))
    }
}

private fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercaseChar().toString() }.ifBlank { "?" }