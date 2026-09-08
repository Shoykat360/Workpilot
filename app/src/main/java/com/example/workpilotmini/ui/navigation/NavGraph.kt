package com.example.workpilotmini.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.ui.attendance.AttendanceScreen
import com.example.workpilotmini.ui.attendance.AttendanceViewModel
import com.example.workpilotmini.ui.auth.AuthViewModel
import com.example.workpilotmini.ui.auth.LoginScreen
import com.example.workpilotmini.ui.auth.SignUpScreen
import com.example.workpilotmini.ui.dashboard.DashboardScreen
import com.example.workpilotmini.ui.notification.NotificationScreen
import com.example.workpilotmini.ui.profile.ProfileScreen
import com.example.workpilotmini.ui.report.ReportScreen
import com.example.workpilotmini.ui.report.ReportViewModel
import com.example.workpilotmini.ui.team.TeamManageScreen
import com.example.workpilotmini.ui.team.TeamSetupScreen
import com.example.workpilotmini.ui.team.TeamViewModel
import com.example.workpilotmini.ui.visit.VisitEntryScreen
import com.example.workpilotmini.ui.visit.VisitHistoryScreen
import com.example.workpilotmini.ui.visit.VisitViewModel

/** Attendance/visit data lives under "teams/{teamId}" normally, or under a
 *  "soloData/{uid}" root for a solo-mode user who has no team yet. */
private fun dataOwnerId(profile: com.example.workpilotmini.model.UserProfile?): String =
    if (profile?.teamId.isNullOrBlank()) profile?.uid.orEmpty() else profile!!.teamId

private fun isSoloData(profile: com.example.workpilotmini.model.UserProfile?): Boolean =
    profile?.teamId.isNullOrBlank()

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PROFILE_COMPLETE = "profile_complete"
    const val TEAM_SETUP = "team_setup"
    const val DASHBOARD = "dashboard"
    const val ATTENDANCE = "attendance"
    const val VISIT_ENTRY = "visit_entry"
    // The actual "add a visit" form — reached from the Visit tab's "Add Visit" FAB.
    // Kept as its own route (instead of being the tab itself) so the tab can be the
    // stats/history dashboard while the form still gets its own back-stack entry.
    const val VISIT_ADD = "visit_add"
    const val REPORT = "report"
    const val MEMBER_REPORT = "member_report"
    const val NOTIFICATIONS = "notifications"
    const val TEAM_MANAGE = "team_manage"
    const val PROFILE = "profile"
}

private val BOTTOM_BAR_ROUTES = setOf(Routes.DASHBOARD, Routes.ATTENDANCE, Routes.VISIT_ENTRY, Routes.REPORT)

/** Switches to one of the 4 bottom-nav tabs, reusing the standard single-top/save-state
 *  pattern so switching tabs back and forth doesn't pile up duplicate back-stack entries
 *  or lose each tab's scroll position. */
private fun switchTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun WorkPilotBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.DASHBOARD,
            onClick = { switchTab(navController, Routes.DASHBOARD) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(Strings.navHome()) }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ATTENDANCE,
            onClick = { switchTab(navController, Routes.ATTENDANCE) },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
            label = { Text(Strings.navAttendance()) }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.VISIT_ENTRY,
            onClick = { switchTab(navController, Routes.VISIT_ENTRY) },
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            label = { Text(Strings.navVisit()) }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.REPORT,
            onClick = { switchTab(navController, Routes.REPORT) },
            icon = { Icon(Icons.Filled.Assessment, contentDescription = null) },
            label = { Text(Strings.navReports()) }
        )
    }
}

@Composable
fun WorkPilotNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.state.collectAsState()

    // While we're still checking for a persisted Firebase session, show a blank
    // loading screen instead of mounting the NavHost at all. This is what stops a
    // logged-in user from ever seeing a flash of the Login screen on app start —
    // we simply don't decide (or render) a start destination until we know for sure.
    if (authState.isCheckingAuth) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val teamRepo = remember { TeamRepository() }
    var team by remember { mutableStateOf<Team?>(null) }

    // Which member's row was tapped on the Team/Members screen, so the Member Report
    // route (which takes no nav arguments) knows who to show. Cleared implicitly when a
    // new member is tapped; the admin can only reach this route via that tap.
    var selectedMemberUid by remember { mutableStateOf<String?>(null) }
    var selectedMemberName by remember { mutableStateOf<String?>(null) }

    // Whenever we have a logged-in profile with a teamId, keep the team object in sync live.
    DisposableEffect(authState.profile?.teamId) {
        val teamId = authState.profile?.teamId
        if (teamId.isNullOrBlank()) {
            team = null
            onDispose { }
        } else {
            val registration = teamRepo.listenToTeam(teamId) { updatedTeam ->
                team = updatedTeam
            }
            onDispose { registration.remove() }
        }
    }

    val startDestination = if (authState.isLoggedIn) {
        val teamless = authState.profile?.teamId.isNullOrBlank()
        val solo = authState.profile?.soloMode == true
        if (teamless && !solo) Routes.TEAM_SETUP else Routes.DASHBOARD
    } else Routes.LOGIN

    // The 4 top-level tabs get a persistent bottom bar (Home / Attendance / Visit /
    // Reports), visible to every logged-in user regardless of admin/member role — this
    // is what keeps Attendance always one tap away instead of buried behind the
    // dashboard tile only.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                WorkPilotBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding).fillMaxWidth()
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoggedIn = { navigateAfterAuth(navController, authState.profile?.teamId, authState.profile?.soloMode == true) },
                    onGoToSignUp = { navController.navigate(Routes.SIGNUP) }
                )
            }
            composable(Routes.SIGNUP) {
                SignUpScreen(
                    viewModel = authViewModel,
                    // After a successful sign-up we always ask the person to complete their
                    // profile (mobile/address are optional, but we give them the chance) before
                    // moving on to team setup.
                    onSignedUp = { navController.navigate(Routes.PROFILE_COMPLETE) { popUpToLogin(navController) } },
                    onGoToLogin = { navController.navigate(Routes.LOGIN) }
                )
            }
            composable(Routes.PROFILE_COMPLETE) {
                val profile = authState.profile
                if (profile != null) {
                    ProfileScreen(
                        viewModel = authViewModel,
                        profile = profile,
                        isPostSignupPrompt = true,
                        onDone = {
                            val teamless = authState.profile?.teamId.isNullOrBlank()
                            val solo = authState.profile?.soloMode == true
                            val destination = if (teamless && !solo) Routes.TEAM_SETUP else Routes.DASHBOARD
                            navController.navigate(destination) {
                                popUpTo(Routes.PROFILE_COMPLETE) { inclusive = true }
                            }
                        },
                        onGoToTeamSetup = { /* not shown while inside the post-signup prompt */ }
                    )
                }
            }
            composable(Routes.TEAM_SETUP) {
                val teamViewModel: TeamViewModel = viewModel()
                val uid = authState.profile?.uid.orEmpty()
                TeamSetupScreen(
                    viewModel = teamViewModel,
                    myUid = uid,
                    onTeamReady = {
                        authViewModel.refreshProfile()
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.TEAM_SETUP) { inclusive = true }
                        }
                    },
                    onUseIndividually = {
                        authViewModel.setSoloMode {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.TEAM_SETUP) { inclusive = true }
                            }
                        }
                    },
                    isSoloProcessing = authState.isLoading,
                    soloErrorMessage = authState.errorMessage
                )
            }
            composable(Routes.PROFILE) {
                val profile = authState.profile
                if (profile != null) {
                    ProfileScreen(
                        viewModel = authViewModel,
                        profile = profile,
                        isPostSignupPrompt = false,
                        onDone = { navController.popBackStack() },
                        onGoToTeamSetup = { navController.navigate(Routes.TEAM_SETUP) }
                    )
                }
            }
            composable(Routes.DASHBOARD) {
                val profile = authState.profile
                if (profile != null) {
                    DashboardScreen(
                        profile = profile,
                        team = team,
                        onOpenAttendance = { switchTab(navController, Routes.ATTENDANCE) },
                        onOpenVisitEntry = { switchTab(navController, Routes.VISIT_ENTRY) },
                        onOpenReport = { switchTab(navController, Routes.REPORT) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onOpenMembers = { navController.navigate(Routes.TEAM_MANAGE) },
                        onOpenTeam = { navController.navigate(Routes.TEAM_MANAGE) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                        onLogout = {
                            authViewModel.logout()
                            navController.navigate(Routes.LOGIN) { popUpTo(0) }
                        }
                    )
                }
            }
            composable(Routes.ATTENDANCE) {
                val attendanceViewModel: AttendanceViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    AttendanceScreen(
                        viewModel = attendanceViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        userName = profile.name,
                        isAdmin = profile.role == "admin",
                        team = team
                    )
                }
            }
            composable(Routes.TEAM_MANAGE) {
                val teamManageViewModel: TeamViewModel = viewModel()
                val profile = authState.profile
                if (profile != null && team != null) {
                    TeamManageScreen(
                        viewModel = teamManageViewModel,
                        team = team!!,
                        myUid = profile.uid,
                        isAdmin = profile.role == "admin",
                        onOpenMemberReport = { uid, name ->
                            selectedMemberUid = uid
                            selectedMemberName = name
                            navController.navigate(Routes.MEMBER_REPORT)
                        }
                    )
                }
            }
            // "Visit" bottom-nav tab — now the stats/search/filter dashboard
            // (VisitHistoryScreen) instead of the raw entry form. Admin sees the whole
            // team's visits, a regular member sees only their own — both cases are
            // already handled by VisitViewModel.startListening below. Both roles can
            // add a new visit (with an optional reminder) via the FAB, which pushes the
            // separate VISIT_ADD route.
            composable(Routes.VISIT_ENTRY) {
                val visitViewModel: VisitViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    val isAdmin = profile.role == "admin"
                    LaunchedEffect(profile.uid, team?.teamId) {
                        visitViewModel.startListening(dataOwnerId(profile), isSoloData(profile), profile.uid, isAdmin)
                    }
                    VisitHistoryScreen(
                        viewModel = visitViewModel,
                        isAdmin = isAdmin,
                        onAddVisit = { navController.navigate(Routes.VISIT_ADD) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) }
                    )
                }
            }
            // The actual "add a visit" form, reached from VisitHistoryScreen's FAB.
            // Available to both admin and member — same screen, same reminder feature.
            composable(Routes.VISIT_ADD) {
                val visitViewModel: VisitViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    VisitEntryScreen(
                        viewModel = visitViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        userName = profile.name,
                        onSaved = { navController.popBackStack() }
                    )
                }
            }
            composable(Routes.REPORT) {
                val reportViewModel: ReportViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    ReportScreen(
                        viewModel = reportViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        isAdmin = profile.role == "admin",
                        // Passed so the whole-team breakdown can include every current member
                        // (even ones with zero activity) and exclude anyone already removed
                        // from the team. See ReportScreen/ReportViewModel.
                        team = team
                    )
                }
            }
            composable(Routes.MEMBER_REPORT) {
                val reportViewModel: ReportViewModel = viewModel()
                val profile = authState.profile
                val focusUid = selectedMemberUid
                if (profile != null && team != null && focusUid != null) {
                    ReportScreen(
                        viewModel = reportViewModel,
                        ownerId = team!!.teamId,
                        isSolo = false,
                        uid = profile.uid,
                        isAdmin = profile.role == "admin",
                        focusUid = focusUid,
                        focusName = selectedMemberName,
                        team = team
                    )
                }
            }
            composable(Routes.NOTIFICATIONS) {
                val visitViewModel: VisitViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    NotificationScreen(
                        viewModel = visitViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        isAdmin = profile.role == "admin"
                    )
                }
            }
        }
    }
}

private fun navigateAfterAuth(navController: NavHostController, teamId: String?, soloMode: Boolean) {
    val destination = if (teamId.isNullOrBlank() && !soloMode) Routes.TEAM_SETUP else Routes.DASHBOARD
    navController.navigate(destination) { popUpToLogin(navController) }
}

private fun androidx.navigation.NavOptionsBuilder.popUpToLogin(navController: NavHostController) {
    popUpTo(Routes.LOGIN) { inclusive = true }
}











/*
package com.example.workpilotmini.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.ui.attendance.AttendanceScreen
import com.example.workpilotmini.ui.attendance.AttendanceViewModel
import com.example.workpilotmini.ui.auth.AuthViewModel
import com.example.workpilotmini.ui.auth.LoginScreen
import com.example.workpilotmini.ui.auth.SignUpScreen
import com.example.workpilotmini.ui.dashboard.DashboardScreen
import com.example.workpilotmini.ui.notification.NotificationScreen
import com.example.workpilotmini.ui.profile.ProfileScreen
import com.example.workpilotmini.ui.report.ReportScreen
import com.example.workpilotmini.ui.report.ReportViewModel
import com.example.workpilotmini.ui.team.TeamManageScreen
import com.example.workpilotmini.ui.team.TeamSetupScreen
import com.example.workpilotmini.ui.team.TeamViewModel
import com.example.workpilotmini.ui.visit.VisitEntryScreen
import com.example.workpilotmini.ui.visit.VisitViewModel

*/
/** Attendance/visit data lives under "teams/{teamId}" normally, or under a
 *  "soloData/{uid}" root for a solo-mode user who has no team yet. *//*

private fun dataOwnerId(profile: com.example.workpilotmini.model.UserProfile?): String =
    if (profile?.teamId.isNullOrBlank()) profile?.uid.orEmpty() else profile!!.teamId

private fun isSoloData(profile: com.example.workpilotmini.model.UserProfile?): Boolean =
    profile?.teamId.isNullOrBlank()

private object Routes {
    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val PROFILE_COMPLETE = "profile_complete"
    const val TEAM_SETUP = "team_setup"
    const val DASHBOARD = "dashboard"
    const val ATTENDANCE = "attendance"
    const val VISIT_ENTRY = "visit_entry"
    const val REPORT = "report"
    const val MEMBER_REPORT = "member_report"
    const val NOTIFICATIONS = "notifications"
    const val TEAM_MANAGE = "team_manage"
    const val PROFILE = "profile"
}

private val BOTTOM_BAR_ROUTES = setOf(Routes.DASHBOARD, Routes.ATTENDANCE, Routes.VISIT_ENTRY, Routes.REPORT)

*/
/** Switches to one of the 4 bottom-nav tabs, reusing the standard single-top/save-state
 *  pattern so switching tabs back and forth doesn't pile up duplicate back-stack entries
 *  or lose each tab's scroll position. *//*

private fun switchTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(Routes.DASHBOARD) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun WorkPilotBottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == Routes.DASHBOARD,
            onClick = { switchTab(navController, Routes.DASHBOARD) },
            icon = { Icon(Icons.Filled.Home, contentDescription = null) },
            label = { Text(Strings.navHome()) }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.ATTENDANCE,
            onClick = { switchTab(navController, Routes.ATTENDANCE) },
            icon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
            label = { Text(Strings.navAttendance()) }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.VISIT_ENTRY,
            onClick = { switchTab(navController, Routes.VISIT_ENTRY) },
            icon = { Icon(Icons.Filled.LocationOn, contentDescription = null) },
            label = { Text(Strings.navVisit()) }
        )
        NavigationBarItem(
            selected = currentRoute == Routes.REPORT,
            onClick = { switchTab(navController, Routes.REPORT) },
            icon = { Icon(Icons.Filled.Assessment, contentDescription = null) },
            label = { Text(Strings.navReports()) }
        )
    }
}

@Composable
fun WorkPilotNavGraph(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.state.collectAsState()

    // While we're still checking for a persisted Firebase session, show a blank
    // loading screen instead of mounting the NavHost at all. This is what stops a
    // logged-in user from ever seeing a flash of the Login screen on app start —
    // we simply don't decide (or render) a start destination until we know for sure.
    if (authState.isCheckingAuth) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val teamRepo = remember { TeamRepository() }
    var team by remember { mutableStateOf<Team?>(null) }

    // Which member's row was tapped on the Team/Members screen, so the Member Report
    // route (which takes no nav arguments) knows who to show. Cleared implicitly when a
    // new member is tapped; the admin can only reach this route via that tap.
    var selectedMemberUid by remember { mutableStateOf<String?>(null) }
    var selectedMemberName by remember { mutableStateOf<String?>(null) }

    // Whenever we have a logged-in profile with a teamId, keep the team object in sync live.
    DisposableEffect(authState.profile?.teamId) {
        val teamId = authState.profile?.teamId
        if (teamId.isNullOrBlank()) {
            team = null
            onDispose { }
        } else {
            val registration = teamRepo.listenToTeam(teamId) { updatedTeam ->
                team = updatedTeam
            }
            onDispose { registration.remove() }
        }
    }

    val startDestination = if (authState.isLoggedIn) {
        val teamless = authState.profile?.teamId.isNullOrBlank()
        val solo = authState.profile?.soloMode == true
        if (teamless && !solo) Routes.TEAM_SETUP else Routes.DASHBOARD
    } else Routes.LOGIN

    // The 4 top-level tabs get a persistent bottom bar (Home / Attendance / Visit /
    // Reports), visible to every logged-in user regardless of admin/member role — this
    // is what keeps Attendance always one tap away instead of buried behind the
    // dashboard tile only.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in BOTTOM_BAR_ROUTES

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                WorkPilotBottomBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding).fillMaxWidth()
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    onLoggedIn = { navigateAfterAuth(navController, authState.profile?.teamId, authState.profile?.soloMode == true) },
                    onGoToSignUp = { navController.navigate(Routes.SIGNUP) }
                )
            }
            composable(Routes.SIGNUP) {
                SignUpScreen(
                    viewModel = authViewModel,
                    // After a successful sign-up we always ask the person to complete their
                    // profile (mobile/address are optional, but we give them the chance) before
                    // moving on to team setup.
                    onSignedUp = { navController.navigate(Routes.PROFILE_COMPLETE) { popUpToLogin(navController) } },
                    onGoToLogin = { navController.navigate(Routes.LOGIN) }
                )
            }
            composable(Routes.PROFILE_COMPLETE) {
                val profile = authState.profile
                if (profile != null) {
                    ProfileScreen(
                        viewModel = authViewModel,
                        profile = profile,
                        isPostSignupPrompt = true,
                        onDone = {
                            val teamless = authState.profile?.teamId.isNullOrBlank()
                            val solo = authState.profile?.soloMode == true
                            val destination = if (teamless && !solo) Routes.TEAM_SETUP else Routes.DASHBOARD
                            navController.navigate(destination) {
                                popUpTo(Routes.PROFILE_COMPLETE) { inclusive = true }
                            }
                        },
                        onGoToTeamSetup = { */
/* not shown while inside the post-signup prompt *//*
 }
                    )
                }
            }
            composable(Routes.TEAM_SETUP) {
                val teamViewModel: TeamViewModel = viewModel()
                val uid = authState.profile?.uid.orEmpty()
                TeamSetupScreen(
                    viewModel = teamViewModel,
                    myUid = uid,
                    onTeamReady = {
                        authViewModel.refreshProfile()
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.TEAM_SETUP) { inclusive = true }
                        }
                    },
                    onUseIndividually = {
                        authViewModel.setSoloMode {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.TEAM_SETUP) { inclusive = true }
                            }
                        }
                    },
                    isSoloProcessing = authState.isLoading,
                    soloErrorMessage = authState.errorMessage
                )
            }
            composable(Routes.PROFILE) {
                val profile = authState.profile
                if (profile != null) {
                    ProfileScreen(
                        viewModel = authViewModel,
                        profile = profile,
                        isPostSignupPrompt = false,
                        onDone = { navController.popBackStack() },
                        onGoToTeamSetup = { navController.navigate(Routes.TEAM_SETUP) }
                    )
                }
            }
            composable(Routes.DASHBOARD) {
                val profile = authState.profile
                if (profile != null) {
                    DashboardScreen(
                        profile = profile,
                        team = team,
                        onOpenAttendance = { switchTab(navController, Routes.ATTENDANCE) },
                        onOpenVisitEntry = { switchTab(navController, Routes.VISIT_ENTRY) },
                        onOpenReport = { switchTab(navController, Routes.REPORT) },
                        onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                        onOpenMembers = { navController.navigate(Routes.TEAM_MANAGE) },
                        onOpenTeam = { navController.navigate(Routes.TEAM_MANAGE) },
                        onOpenProfile = { navController.navigate(Routes.PROFILE) },
                        onLogout = {
                            authViewModel.logout()
                            navController.navigate(Routes.LOGIN) { popUpTo(0) }
                        }
                    )
                }
            }
            composable(Routes.ATTENDANCE) {
                val attendanceViewModel: AttendanceViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    AttendanceScreen(
                        viewModel = attendanceViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        userName = profile.name,
                        isAdmin = profile.role == "admin",
                        team = team
                    )
                }
            }
            composable(Routes.TEAM_MANAGE) {
                val teamManageViewModel: TeamViewModel = viewModel()
                val profile = authState.profile
                if (profile != null && team != null) {
                    TeamManageScreen(
                        viewModel = teamManageViewModel,
                        team = team!!,
                        myUid = profile.uid,
                        isAdmin = profile.role == "admin",
                        onOpenMemberReport = { uid, name ->
                            selectedMemberUid = uid
                            selectedMemberName = name
                            navController.navigate(Routes.MEMBER_REPORT)
                        }
                    )
                }
            }
            composable(Routes.VISIT_ENTRY) {
                val visitViewModel: VisitViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    VisitEntryScreen(
                        viewModel = visitViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        userName = profile.name,
                        onSaved = { navController.popBackStack() }
                    )
                }
            }
            composable(Routes.REPORT) {
                val reportViewModel: ReportViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    ReportScreen(
                        viewModel = reportViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        isAdmin = profile.role == "admin",
                        // Passed so the whole-team breakdown can include every current member
                        // (even ones with zero activity) and exclude anyone already removed
                        // from the team. See ReportScreen/ReportViewModel.
                        team = team
                    )
                }
            }
            composable(Routes.MEMBER_REPORT) {
                val reportViewModel: ReportViewModel = viewModel()
                val profile = authState.profile
                val focusUid = selectedMemberUid
                if (profile != null && team != null && focusUid != null) {
                    ReportScreen(
                        viewModel = reportViewModel,
                        ownerId = team!!.teamId,
                        isSolo = false,
                        uid = profile.uid,
                        isAdmin = profile.role == "admin",
                        focusUid = focusUid,
                        focusName = selectedMemberName,
                        team = team
                    )
                }
            }
            composable(Routes.NOTIFICATIONS) {
                val visitViewModel: VisitViewModel = viewModel()
                val profile = authState.profile
                if (profile != null) {
                    NotificationScreen(
                        viewModel = visitViewModel,
                        ownerId = dataOwnerId(profile),
                        isSolo = isSoloData(profile),
                        uid = profile.uid,
                        isAdmin = profile.role == "admin"
                    )
                }
            }
        }
    }
}

private fun navigateAfterAuth(navController: NavHostController, teamId: String?, soloMode: Boolean) {
    val destination = if (teamId.isNullOrBlank() && !soloMode) Routes.TEAM_SETUP else Routes.DASHBOARD
    navController.navigate(destination) { popUpToLogin(navController) }
}

private fun androidx.navigation.NavOptionsBuilder.popUpToLogin(navController: NavHostController) {
    popUpTo(Routes.LOGIN) { inclusive = true }
}*/
