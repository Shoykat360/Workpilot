package com.example.workpilotmini.ui.visit

import com.example.workpilotmini.data.AppError
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.location.LocationHelper
import com.example.workpilotmini.model.VisitEntry
import com.example.workpilotmini.notification.ReminderScheduler
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VisitUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false,
    val visits: List<VisitEntry> = emptyList(),
    val upcomingReminders: List<VisitEntry> = emptyList(),
    // GPS capture for the visit-entry form, shown live so the user can see the exact
    // coordinates that will be saved instead of it happening silently in the background.
    val isLocating: Boolean = false,
    val capturedLat: Double? = null,
    val capturedLng: Double? = null,
    val locationError: String? = null
)

class VisitViewModel : ViewModel() {
    private val repo = VisitRepository()
    private var visitsListener: ListenerRegistration? = null
    private var reminderListener: ListenerRegistration? = null

    private val _state = MutableStateFlow(VisitUiState())
    val state: StateFlow<VisitUiState> = _state.asStateFlow()

    fun startListening(ownerId: String, isSolo: Boolean, uid: String, isAdmin: Boolean) {
        visitsListener?.remove()
        reminderListener?.remove()
        // Admin sees every visit the team has logged; a member only ever sees their own —
        // both the "Visit History" screen and the underlying rule follow this same split.
        // Solo users always take the member-scoped path (they only ever have their own data).
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
    }

    fun resetSaved() {
        _state.value = _state.value.copy(saved = false, capturedLat = null, capturedLng = null, locationError = null)
    }

    /** Fetches a fresh GPS fix and shows it on the form, so the user can see the exact
     *  lat/long that will be saved with the visit (and knows right away if it failed,
     *  rather than the visit silently saving with no location). */
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

    /** Saves a visit entry with the already-captured GPS location (see [captureLocation])
     *  and, if [nextVisitDate] is set, schedules a local reminder. [businessType] and
     *  [address] are both free-text and optional. */
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
            _state.value = _state.value.copy(errorMessage = "লিড/দোকানের নাম দিন")
            return
        }
        val lat = _state.value.capturedLat
        val lng = _state.value.capturedLng
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val visit = VisitEntry(
                uid = uid,
                userName = userName,
                leadName = leadName.trim(),
                businessType = businessType.trim(),
                address = address.trim(),
                contactPerson = contactPerson.trim(),
                phoneNumber = phoneNumber.trim(),
                visitPurpose = visitPurpose.trim(),
                notes = notes.trim(),
                lat = lat,
                lng = lng,
                visitTime = System.currentTimeMillis(),
                nextVisitDate = nextVisitDate
            )
            val result = repo.addVisit(ownerId, isSolo, visit)
            result.onSuccess { saved ->
                if (nextVisitDate != null) {
                    ReminderScheduler.scheduleVisitReminder(
                        context = context,
                        visitId = saved.id,
                        leadName = saved.leadName,
                        address = saved.address,
                        triggerAtMillis = nextVisitDate
                    )
                    repo.markReminderScheduled(ownerId, isSolo, saved.id)
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
        super.onCleared()
    }
}