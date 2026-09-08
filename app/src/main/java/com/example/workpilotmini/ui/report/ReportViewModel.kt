package com.example.workpilotmini.ui.report

import com.example.workpilotmini.data.AppError
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.AttendanceRepository
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.model.VisitEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

enum class ReportPeriod { DAILY, WEEKLY, MONTHLY }

/** One row of the admin's "who did what" breakdown table. */
data class MemberReportRow(
    val uid: String,
    val name: String,
    val attendanceCount: Int,
    val visitCount: Int,
    val reminderCount: Int
)

data class ReportUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val period: ReportPeriod = ReportPeriod.DAILY,
    val attendanceCount: Int = 0,
    val visitCount: Int = 0,
    val reminderCount: Int = 0,
    val visits: List<VisitEntry> = emptyList(),
    // Only populated for the admin's whole-team report (not a single-member report).
    val memberBreakdown: List<MemberReportRow> = emptyList()
)

/**
 * Powers the Report screen (replaces the old separate Reminders + Visit History tiles).
 * Same scope rule used everywhere else in the app: the real team admin viewing the
 * whole-team report (no [focusUid]) sees everyone's numbers; a regular member, a solo
 * user, or anyone viewing a single member's report (via [focusUid], reachable from the
 * Members section — admin only) only ever sees that one person's numbers.
 */
class ReportViewModel : ViewModel() {
    private val attendanceRepo = AttendanceRepository()
    private val visitRepo = VisitRepository()

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun setPeriod(
        period: ReportPeriod,
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        isAdmin: Boolean,
        focusUid: String? = null,
        focusName: String? = null,
        // Current team roster as (uid, name) pairs. Only used for the whole-team
        // breakdown (see [load]); pass the team's member list here so the table always
        // matches who's actually on the team right now.
        teamMembers: List<Pair<String, String>> = emptyList()
    ) {
        _state.value = _state.value.copy(period = period)
        load(ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMembers)
    }

    fun load(
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        isAdmin: Boolean,
        focusUid: String? = null,
        focusName: String? = null,
        teamMembers: List<Pair<String, String>> = emptyList()
    ) {
        val since = periodStartMillis(_state.value.period)
        val teamWide = focusUid == null && isAdmin && !isSolo
        val scopeUid = focusUid ?: if (teamWide) null else uid

        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val attendanceResult = attendanceRepo.getAttendanceSince(ownerId, isSolo, since, scopeUid)
            val visitsResult = visitRepo.getVisitsSince(ownerId, isSolo, since, scopeUid)

            if (attendanceResult.isFailure || visitsResult.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = AppError.message(attendanceResult.exceptionOrNull() ?: visitsResult.exceptionOrNull()!!)
                )
                return@launch
            }

            val attendance = attendanceResult.getOrDefault(emptyList())
            val visits = visitsResult.getOrDefault(emptyList())
            val reminderCount = visits.count { it.nextVisitDate != null }

            var breakdown = emptyList<MemberReportRow>()
            if (teamWide) {
                val names = mutableMapOf<String, String>()
                if (teamMembers.isNotEmpty()) {
                    // Preferred path: seed the table with the CURRENT roster first, so
                    // 1) a member who did nothing this period still gets a 0/0/0 row
                    //    instead of silently disappearing from the table, and
                    // 2) someone the admin already removed from the team never shows up
                    //    here just because their old attendance/visit records are still
                    //    sitting in Firestore.
                    teamMembers.forEach { (memberUid, memberName) -> names[memberUid] = memberName }
                } else {
                    // Fallback for callers that don't pass the roster in: same behavior
                    // as before (only members with at least one record show up).
                    attendance.forEach { names[it.uid] = it.userName }
                    visits.forEach { if (it.uid !in names) names[it.uid] = it.userName }
                }
                breakdown = names.keys.map { memberUid ->
                    MemberReportRow(
                        uid = memberUid,
                        name = names[memberUid].orEmpty(),
                        attendanceCount = attendance.count { it.uid == memberUid },
                        visitCount = visits.count { it.uid == memberUid },
                        reminderCount = visits.count { it.uid == memberUid && it.nextVisitDate != null }
                    )
                }.sortedByDescending { it.visitCount + it.attendanceCount }
            }

            _state.value = _state.value.copy(
                isLoading = false,
                attendanceCount = attendance.size,
                visitCount = visits.size,
                reminderCount = reminderCount,
                visits = visits.sortedByDescending { it.visitTime },
                memberBreakdown = breakdown
            )
        }
    }

    private fun periodStartMillis(period: ReportPeriod): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        when (period) {
            ReportPeriod.DAILY -> { /* today's midnight, nothing more to do */ }
            ReportPeriod.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, -6)
            ReportPeriod.MONTHLY -> cal.set(Calendar.DAY_OF_MONTH, 1)
        }
        return cal.timeInMillis
    }
}