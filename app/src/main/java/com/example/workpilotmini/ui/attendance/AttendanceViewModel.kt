package com.example.workpilotmini.ui.attendance

import com.example.workpilotmini.data.AppError
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.AttendanceRepository
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.location.LocationHelper
import com.example.workpilotmini.model.AttendanceRecord
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.UserProfile
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Admin-only filter for the team member list. */
enum class AttendanceFilter { ALL, CHECKED_IN, NOT_CHECKED_IN }

/** One row in the admin's team list: a team member profile joined with today's
 *  attendance record for them, if any (null = hasn't checked in today). */
data class MemberAttendanceRow(
    val profile: UserProfile,
    val record: AttendanceRecord?
)

data class AttendanceUiState(
    val isLoading: Boolean = false,
    val isCheckingOut: Boolean = false,
    val errorMessage: String? = null,
    val checkedInToday: Boolean = false,
    val checkedOutToday: Boolean = false,
    val myRecord: AttendanceRecord? = null,
    val todayList: List<AttendanceRecord> = emptyList(),
    // GPS capture for the check-in/out button, shown live — same reasoning as visit entry:
    // silently fetching in the background gave no feedback when it failed.
    val isLocating: Boolean = false,
    val capturedLat: Double? = null,
    val capturedLng: Double? = null,
    val locationError: String? = null,
    // Admin-only extras (dashboard-style stats + search/filter over the team list)
    val isLoadingTeam: Boolean = false,
    val totalTeamMembers: Int = 0,
    val activeTeamMembers: Int = 0,
    val todayVisitCount: Int = 0,
    val allMembers: List<UserProfile> = emptyList(),
    val searchQuery: String = "",
    val filter: AttendanceFilter = AttendanceFilter.ALL
) {
    /** Team roster joined with today's attendance, newest check-in first, then
     *  search + filter applied — what the admin list actually renders. */
    val visibleRows: List<MemberAttendanceRow>
        get() {
            val recordsByUid = todayList.associateBy { it.uid }
            val q = searchQuery.trim().lowercase()
            return allMembers
                .map { MemberAttendanceRow(it, recordsByUid[it.uid]) }
                .filter { row ->
                    when (filter) {
                        AttendanceFilter.ALL -> true
                        AttendanceFilter.CHECKED_IN -> row.record != null
                        AttendanceFilter.NOT_CHECKED_IN -> row.record == null
                    }
                }
                .filter { row ->
                    q.isBlank() ||
                            row.profile.name.lowercase().contains(q) ||
                            row.profile.mobile.lowercase().contains(q) ||
                            row.profile.address.lowercase().contains(q)
                }
                .sortedWith(
                    compareByDescending<MemberAttendanceRow> { it.record != null }
                        .thenByDescending { it.record?.checkInTime ?: 0L }
                )
        }

    val checkedInCount: Int get() = todayList.size
    val notCheckedInCount: Int get() = (totalTeamMembers - checkedInCount).coerceAtLeast(0)
}

class AttendanceViewModel : ViewModel() {
    private val repo = AttendanceRepository()
    private val teamRepo = TeamRepository()
    private val visitRepo = VisitRepository()
    private var listener: ListenerRegistration? = null

    private val _state = MutableStateFlow(AttendanceUiState())
    val state: StateFlow<AttendanceUiState> = _state.asStateFlow()

    fun start(ownerId: String, isSolo: Boolean, uid: String, isAdmin: Boolean, team: Team? = null) {
        listener?.remove()
        listener = repo.listenToTodayAttendance(ownerId, isSolo, uid, isAdmin) { list ->
            val mine = list.find { it.uid == uid }
            _state.value = _state.value.copy(
                todayList = list,
                checkedInToday = mine != null,
                checkedOutToday = mine?.checkOutTime != null,
                myRecord = mine
            )
        }

        if (isAdmin && !isSolo && team != null) {
            _state.value = _state.value.copy(
                isLoadingTeam = true,
                totalTeamMembers = team.memberUids.size
            )
            viewModelScope.launch {
                teamRepo.getTeamMembers(team.teamId, team.memberUids).onSuccess { members ->
                    _state.value = _state.value.copy(
                        allMembers = members,
                        totalTeamMembers = members.size,
                        activeTeamMembers = members.count { it.isActive },
                        isLoadingTeam = false
                    )
                }.onFailure {
                    _state.value = _state.value.copy(isLoadingTeam = false)
                }
                visitRepo.getVisitsSince(ownerId, isSolo, repo.startOfTodayMillis()).onSuccess { visits ->
                    _state.value = _state.value.copy(todayVisitCount = visits.size)
                }
            }
        }
    }

    /** Fetches a fresh GPS fix and shows it above the check-in button, so the user can see
     *  the exact coordinates that will be saved with today's check-in/out. */
    fun captureLocation(context: Context) {
        _state.value = _state.value.copy(isLocating = true, locationError = null)
        viewModelScope.launch {
            val helper = LocationHelper(context)
            if (!helper.hasLocationPermission()) {
                _state.value = _state.value.copy(
                    isLocating = false,
                    capturedLat = null,
                    capturedLng = null,
                    locationError = "লোকেশন পারমিশন দেওয়া নেই। Settings থেকে App permission এ গিয়ে Location অন করুন।"
                )
                return@launch
            }
            val location = helper.getCurrentLocation()
            if (location == null) {
                _state.value = _state.value.copy(
                    isLocating = false,
                    capturedLat = null,
                    capturedLng = null,
                    locationError = "GPS লোকেশন পাওয়া যায়নি। ফোনের GPS অন আছে কিনা দেখে আবার চেষ্টা করুন।"
                )
            } else {
                _state.value = _state.value.copy(
                    isLocating = false,
                    capturedLat = location.latitude,
                    capturedLng = location.longitude,
                    locationError = null
                )
            }
        }
    }

    fun checkIn(context: Context, ownerId: String, isSolo: Boolean, uid: String, userName: String) {
        val lat = _state.value.capturedLat
        val lng = _state.value.capturedLng
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.checkIn(ownerId, isSolo, uid, userName, lat, lng)
            result.onSuccess {
                // checkedInToday/myRecord টাচ করছি না — listenToTodayAttendance listener-ই
                // একমাত্র জায়গা যেটা এই state আপডেট করবে, নাহলে দুই জায়গা থেকে race করে
                // UI blink করে (এক frame-এ checked-in দেখায়, পরের frame-এ আগের data ফিরে আসে)।
                _state.value = _state.value.copy(isLoading = false)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it))
            }
        }
    }

    /** Check-out — same button flow as check-in, works identically for admin and member. */
    fun checkOut(context: Context, ownerId: String, isSolo: Boolean, uid: String) {
        val lat = _state.value.capturedLat
        val lng = _state.value.capturedLng
        _state.value = _state.value.copy(isCheckingOut = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.checkOut(ownerId, isSolo, uid, lat, lng)
            result.onSuccess {
                _state.value = _state.value.copy(isCheckingOut = false)
            }.onFailure {
                _state.value = _state.value.copy(isCheckingOut = false, errorMessage = AppError.message(it))
            }
        }
    }

    fun setSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
    }

    fun setFilter(filter: AttendanceFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}