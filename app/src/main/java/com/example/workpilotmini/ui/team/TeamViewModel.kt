package com.example.workpilotmini.ui.team

import com.example.workpilotmini.data.AppError
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val team: Team? = null,
    val done: Boolean = false,
    // Team-management screen state (add-by-email + member list) — separate flags so
    // this loading spinner doesn't fight with create/join's.
    val members: List<UserProfile> = emptyList(),
    val isAddingMember: Boolean = false,
    val addMemberSuccessMessage: String? = null,
    // Per-member admin action state (activate/deactivate, remove, delete).
    val memberActionInProgressUid: String? = null,
    val memberActionMessage: String? = null
)

class TeamViewModel : ViewModel() {
    private val repo = TeamRepository()

    private val _state = MutableStateFlow(TeamUiState())
    val state: StateFlow<TeamUiState> = _state.asStateFlow()

    fun createTeam(name: String, maxSize: Int, adminUid: String, companyName: String = "", location: String = "") {
        if (name.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "টিমের নাম দিন")
            return
        }
        if (maxSize < Team.MIN_SIZE || maxSize > Team.MAX_SIZE) {
            _state.value = _state.value.copy(
                errorMessage = "টিম সাইজ ${Team.MIN_SIZE} থেকে ${Team.MAX_SIZE} জনের মধ্যে হতে হবে"
            )
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.createTeam(name.trim(), maxSize, adminUid, companyName.trim(), location.trim())
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false, team = it, done = true)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it))
            }
        }
    }

    fun joinTeam(code: String, uid: String) {
        if (code.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "টিমের ইনভাইট কোড দিন")
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.joinTeamByCode(code.trim(), uid)
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false, team = it, done = true)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /** Loads member profiles (name/email) for the team management screen. */
    fun loadMembers(team: Team) {
        viewModelScope.launch {
            val result = repo.getTeamMembers(team.teamId, team.memberUids)
            result.onSuccess {
                _state.value = _state.value.copy(members = it)
            }.onFailure {
                _state.value = _state.value.copy(errorMessage = AppError.message(it))
            }
        }
    }

    /** Admin-only: adds a user straight to the team by email, no invite code needed. */
    fun addMemberByEmail(team: Team, email: String, adminUid: String) {
        if (email.isBlank()) {
            _state.value = _state.value.copy(errorMessage = "ইমেইল দিন")
            return
        }
        _state.value = _state.value.copy(isAddingMember = true, errorMessage = null, addMemberSuccessMessage = null)
        viewModelScope.launch {
            val result = repo.addMemberByEmail(team, email.trim(), adminUid)
            result.onSuccess { added ->
                _state.value = _state.value.copy(
                    isAddingMember = false,
                    addMemberSuccessMessage = "${added.name.ifBlank { added.email }} কে টিমে যোগ করা হয়েছে"
                )
                loadMembers(team.copy(memberUids = team.memberUids + added.uid))
            }.onFailure {
                _state.value = _state.value.copy(isAddingMember = false, errorMessage = AppError.message(it))
            }
        }
    }

    fun clearAddMemberSuccess() {
        _state.value = _state.value.copy(addMemberSuccessMessage = null)
    }

    // "Create member account directly" (admin sets email + password) is disabled —
    // see the matching commented-out repo function in TeamRepository.kt.

    /** Admin-only: activate/deactivate a member. */
    fun setMemberActive(team: Team, targetUid: String, isActive: Boolean, adminUid: String) {
        _state.value = _state.value.copy(memberActionInProgressUid = targetUid, errorMessage = null, memberActionMessage = null)
        viewModelScope.launch {
            val result = repo.setMemberActive(team, targetUid, isActive, adminUid)
            result.onSuccess {
                _state.value = _state.value.copy(memberActionInProgressUid = null, memberActionMessage = "স্ট্যাটাস আপডেট হয়েছে")
                loadMembers(team)
            }.onFailure {
                _state.value = _state.value.copy(memberActionInProgressUid = null, errorMessage = AppError.message(it))
            }
        }
    }

    /** Admin-only: removes a member from the team (their account stays intact). */
    fun removeMember(team: Team, targetUid: String, adminUid: String) {
        _state.value = _state.value.copy(memberActionInProgressUid = targetUid, errorMessage = null, memberActionMessage = null)
        viewModelScope.launch {
            val result = repo.removeMember(team, targetUid, adminUid)
            result.onSuccess {
                _state.value = _state.value.copy(memberActionInProgressUid = null, memberActionMessage = "মেম্বার টিম থেকে সরানো হয়েছে")
                loadMembers(team.copy(memberUids = team.memberUids - targetUid))
            }.onFailure {
                _state.value = _state.value.copy(memberActionInProgressUid = null, errorMessage = AppError.message(it))
            }
        }
    }

    /** Admin-only: removes a member from the team and deletes their profile document. */
    fun deleteMember(team: Team, targetUid: String, adminUid: String) {
        _state.value = _state.value.copy(memberActionInProgressUid = targetUid, errorMessage = null, memberActionMessage = null)
        viewModelScope.launch {
            val result = repo.deleteMember(team, targetUid, adminUid)
            result.onSuccess {
                _state.value = _state.value.copy(memberActionInProgressUid = null, memberActionMessage = "মেম্বার ডিলিট করা হয়েছে")
                loadMembers(team.copy(memberUids = team.memberUids - targetUid))
            }.onFailure {
                _state.value = _state.value.copy(memberActionInProgressUid = null, errorMessage = AppError.message(it))
            }
        }
    }

    fun clearMemberActionMessage() {
        _state.value = _state.value.copy(memberActionMessage = null)
    }
}
