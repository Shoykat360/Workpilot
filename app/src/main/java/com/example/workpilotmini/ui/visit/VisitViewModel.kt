package com.example.workpilotmini.ui.visit

import android.content.Context
import android.location.Geocoder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.AppError
import com.example.workpilotmini.data.TeamRepository
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.localization.AppLanguage
import com.example.workpilotmini.localization.Language
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.location.LocationHelper
import com.example.workpilotmini.model.VisitEntry
import com.example.workpilotmini.notification.ReminderScheduler
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

data class VisitUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val visits: List<VisitEntry> = emptyList(),
    val upcomingReminders: List<VisitEntry> = emptyList(),
    // Team roster (admin only) — lets the admin screen show who has NOT visited today.
    val members: List<TeamMemberInfo> = emptyList(),
    // GPS capture for the visit-entry form.
    val isLocating: Boolean = false,
    val capturedLat: Double? = null,
    val capturedLng: Double? = null,
    // Human-readable place name for the captured GPS fix (what the user sees instead of lat/lng).
    val capturedAddress: String? = null,
    val locationError: String? = null
)

// ───────── Reminder date helpers ─────────
// Material3 DatePicker returns the picked day as *UTC midnight*. Scheduling an alarm with
// that raw value fires at a wrong local time (or in the past in UTC− time zones), which is
// why reminders looked "not working". We convert the picked day to 9:00 AM LOCAL time.

/** Earliest day the reminder date picker should allow (tomorrow), as UTC-midnight millis. */
fun earliestReminderUtcMillis(): Long {
    val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(tomorrow.get(Calendar.YEAR), tomorrow.get(Calendar.MONTH), tomorrow.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Picked day (UTC midnight from DatePicker) → 9:00 AM on that same calendar day, local time. */
fun reminderTriggerMillis(utcPickedMillis: Long): Long {
    val picked = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcPickedMillis }
    return Calendar.getInstance().apply {
        clear()
        set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH), 9, 0, 0)
    }.timeInMillis
}

class VisitViewModel : ViewModel() {
    private val repo = VisitRepository()
    private val teamRepo = TeamRepository()
    private var visitsListener: ListenerRegistration? = null
    private var reminderListener: ListenerRegistration? = null
    private var membersJob: Job? = null

    private val _state = MutableStateFlow(VisitUiState())
    val state: StateFlow<VisitUiState> = _state.asStateFlow()

    fun startListening(ownerId: String, isSolo: Boolean, uid: String, isAdmin: Boolean) {
        visitsListener?.remove()
        reminderListener?.remove()
        // Admin sees every visit the team has logged (including their own);
        // a member only ever sees their own. Solo users always take the own-only path.
        visitsListener = if (isAdmin && !isSolo) {
            repo.listenToTeamVisits(ownerId, isSolo) { list ->
                _state.value = _state.value.copy(visits = list)
            }
        } else {
            repo.listenToMyVisits(ownerId, isSolo, uid) { list ->
                _state.value = _state.value.copy(visits = list)
            }
        }
        reminderListener = repo.listenToUpcomingReminders(ownerId, isSolo, uid, isAdmin) { list ->
            _state.value = _state.value.copy(upcomingReminders = list)
        }

        if (isAdmin && !isSolo) loadMembers(ownerId)
    }

    /** Loads the team roster (admin + members) so the admin can see who hasn't visited. */
    private fun loadMembers(teamId: String) {
        membersJob?.cancel()
        membersJob = viewModelScope.launch {
            val team = teamRepo.getTeam(teamId).getOrNull() ?: return@launch
            teamRepo.getTeamMembers(teamId, team.memberUids).onSuccess { profiles ->
                _state.value = _state.value.copy(
                    members = profiles.map { TeamMemberInfo(it.uid, it.name.ifBlank { it.email }) }
                )
            }
        }
    }

    fun resetSaved() {
        _state.value = _state.value.copy(
            saved = false, capturedLat = null, capturedLng = null,
            capturedAddress = null, locationError = null
        )
    }

    /** Fetches a fresh GPS fix, turns it into a readable place name and shows that on the form. */
    fun captureLocation(context: Context) {
        _state.value = _state.value.copy(isLocating = true, locationError = null, capturedAddress = null)
        viewModelScope.launch {
            val helper = LocationHelper(context)
            if (!helper.hasLocationPermission()) {
                _state.value = _state.value.copy(
                    isLocating = false, capturedLat = null, capturedLng = null, capturedAddress = null,
                    locationError = Strings.locationPermissionError()
                )
                return@launch
            }
            val location = helper.getCurrentLocation()
            if (location == null) {
                _state.value = _state.value.copy(
                    isLocating = false, capturedLat = null, capturedLng = null, capturedAddress = null,
                    locationError = Strings.locationGpsError()
                )
                return@launch
            }
            val name = reverseGeocode(context, location.latitude, location.longitude)
            _state.value = _state.value.copy(
                isLocating = false,
                capturedLat = location.latitude,
                capturedLng = location.longitude,
                capturedAddress = name,
                locationError = null
            )
        }
    }

    /** lat/lng → place name (e.g. "Shahbag, Dhaka"). Returns null if no geocoder / no network. */
    private suspend fun reverseGeocode(context: Context, lat: Double, lng: Double): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                // Place name follows the app language (Bangla or English).
                val locale = if (AppLanguage.current.value == Language.BN) Locale("bn", "BD") else Locale.ENGLISH
                @Suppress("DEPRECATION")
                val a = Geocoder(context.applicationContext, locale)
                    .getFromLocation(lat, lng, 1)?.firstOrNull() ?: return@runCatching null
                val line = a.getAddressLine(0)
                if (!line.isNullOrBlank()) line
                else listOfNotNull(a.featureName, a.subLocality, a.locality)
                    .distinct().joinToString(", ").ifBlank { null }
            }.getOrNull()
        }

    /** Saves a visit with the captured GPS fix. If the user left the location text empty,
     *  the reverse-geocoded place name is used. [nextVisitDate] is the raw DatePicker value
     *  (UTC midnight); it's converted to 9:00 AM local before scheduling the reminder. */
    fun addVisit(
        context: Context,
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        userName: String,
        leadName: String,
        businessType: String,
        address: String,
        contactPerson: String,
        phoneNumber: String,
        visitPurpose: String,
        notes: String,
        nextVisitDate: Long?
    ) {
        if (leadName.isBlank()) {
            _state.value = _state.value.copy(errorMessage = Strings.leadNameRequired())
            return
        }
        val reminderAt = nextVisitDate?.let { reminderTriggerMillis(it) }
        if (reminderAt != null && reminderAt <= System.currentTimeMillis()) {
            _state.value = _state.value.copy(errorMessage = Strings.reminderFutureDateError())
            return
        }

        val lat = _state.value.capturedLat
        val lng = _state.value.capturedLng
        val finalAddress = address.trim().ifBlank { _state.value.capturedAddress.orEmpty() }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val visit = VisitEntry(
                uid = uid,
                userName = userName,
                leadName = leadName.trim(),
                businessType = businessType.trim(),
                address = finalAddress,
                contactPerson = contactPerson.trim(),
                phoneNumber = phoneNumber.trim(),
                visitPurpose = visitPurpose.trim(),
                notes = notes.trim(),
                lat = lat,
                lng = lng,
                visitTime = System.currentTimeMillis(),
                nextVisitDate = reminderAt
            )
            val result = repo.addVisit(ownerId, isSolo, visit)
            result.onSuccess { saved ->
                if (reminderAt != null) {
                    // A scheduling failure (e.g. exact-alarm permission) must not lose the visit.
                    runCatching {
                        ReminderScheduler.scheduleVisitReminder(
                            context = context,
                            visitId = saved.id,
                            leadName = saved.leadName,
                            address = saved.address,
                            triggerAtMillis = reminderAt
                        )
                    }.onSuccess {
                        repo.markReminderScheduled(ownerId, isSolo, saved.id)
                    }
                }
                _state.value = _state.value.copy(isLoading = false, saved = true)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it))
            }
        }
    }

    override fun onCleared() {
        visitsListener?.remove()
        reminderListener?.remove()
        membersJob?.cancel()
        super.onCleared()
    }
}