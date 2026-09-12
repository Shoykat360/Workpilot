package com.example.workpilotmini.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.AppError
import com.example.workpilotmini.data.AuthRepository
import com.example.workpilotmini.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.util.Patterns
import com.example.workpilotmini.localization.Strings
import com.google.firebase.auth.FirebaseAuthInvalidUserException

data class AuthUiState(
    // True only while we're checking whether a Firebase session already exists on
    // app start (see AuthViewModel.init). NavGraph waits for this to become false
    // before deciding whether to show Login or Dashboard, so a logged-in user never
    // sees a flash of the Login screen before landing on the Dashboard.
    val isCheckingAuth: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val profile: UserProfile? = null,
    val isLoggedIn: Boolean = false,
    val resetEmailSent: Boolean = false,
    val isSavingProfile: Boolean = false,
    val profileSaved: Boolean = false
)

class AuthViewModel : ViewModel() {
    private val repo = AuthRepository()

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        if (repo.currentUid != null) {
            refreshProfile()
        } else {
            // No persisted session at all — nothing to check, go straight to Login.
            _state.value = _state.value.copy(isCheckingAuth = false)
        }
    }

    private val emailRegex = Regex("^[A-Za-z0-9+_.-]+@(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}$")

    private fun isValidEmail(email: String): Boolean {
        val trimmed = email.trim()
        return trimmed.isNotBlank() &&
                Patterns.EMAIL_ADDRESS.matcher(trimmed).matches() &&
                emailRegex.matches(trimmed)
    }

    fun signUp(name: String, email: String, password: String, termsAccepted: Boolean) {
        if (name.isBlank() || email.isBlank() || password.length < 6) {
            _state.value = _state.value.copy(errorMessage = Strings.signUpRequiredFieldsError())
            return
        }
        if (!isValidEmail(email)) {
            _state.value = _state.value.copy(errorMessage = Strings.invalidEmailError())
            return
        }
        if (!termsAccepted) {
            _state.value = _state.value.copy(errorMessage = Strings.termsRequiredError())
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.signUp(name, email, password, System.currentTimeMillis())
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false, profile = it, isLoggedIn = true, isCheckingAuth = false)
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it), isCheckingAuth = false)
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(errorMessage = Strings.emailPasswordRequiredError())
            return
        }

        if (!isValidEmail(email)) {
            _state.value = _state.value.copy(errorMessage = Strings.invalidEmailError())
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.login(email, password)
            result.onSuccess {
                refreshProfile()
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it), isCheckingAuth = false)
            }
        }
    }

    /** Always reports success in the UI (even for unregistered emails) so we don't
     *  reveal which addresses have accounts — a common account-enumeration leak. */
    fun sendPasswordReset(email: String) {
        val trimmed = email.trim()
        if (!isValidEmail(trimmed)) {
            _state.value = _state.value.copy(errorMessage = Strings.invalidEmailError())
            return
        }
        _state.value = _state.value.copy(isLoading = true, errorMessage = null, resetEmailSent = false)
        viewModelScope.launch {
            val result = repo.sendPasswordReset(trimmed)
            result.onSuccess {
                _state.value = _state.value.copy(isLoading = false, resetEmailSent = true)
            }.onFailure { e ->
                if (e is FirebaseAuthInvalidUserException) {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = Strings.emailNotRegisteredError())
                } else {
                    _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(e))
                }
            }
        }
    }

    fun clearResetEmailSent() {
        _state.value = _state.value.copy(resetEmailSent = false)
    }

    fun refreshProfile() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            val result = repo.getCurrentProfile()
            result.onSuccess { profile ->
                // A deactivated account (admin turned it off) is signed out immediately,
                // wherever this refresh was triggered from (login, app resume, etc.).
                if (profile != null && !profile.isActive) {
                    repo.logout()
                    _state.value = AuthUiState(
                        isCheckingAuth = false,
                        errorMessage = "আপনার অ্যাকাউন্ট Admin দ্বারা নিষ্ক্রিয় করা হয়েছে। বিস্তারিত জানতে Admin এর সাথে যোগাযোগ করুন।"
                    )
                    return@onSuccess
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    profile = profile,
                    isLoggedIn = profile != null,
                    errorMessage = null,
                    isCheckingAuth = false
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it), isCheckingAuth = false)
            }
        }
    }

    fun logout() {
        repo.logout()
        _state.value = AuthUiState(isCheckingAuth = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    /** Self-service profile edit: name, mobile, address (mobile/address optional). */
    fun updateProfile(name: String, mobile: String, address: String) {
        val uid = repo.currentUid ?: return
        if (name.isBlank()) {
            _state.value = _state.value.copy(errorMessage = Strings.nameRequiredError())
            return
        }
        _state.value = _state.value.copy(isSavingProfile = true, errorMessage = null, profileSaved = false)
        viewModelScope.launch {
            val result = repo.updateProfile(uid, name, mobile, address)
            result.onSuccess {
                _state.value = _state.value.copy(
                    isSavingProfile = false,
                    profileSaved = true,
                    profile = _state.value.profile?.copy(
                        name = name.trim(), mobile = mobile.trim(), address = address.trim(), profileCompleted = true
                    )
                )
            }.onFailure {
                _state.value = _state.value.copy(isSavingProfile = false, errorMessage = AppError.message(it))
            }
        }
    }

    /** Changes the account password. Requires a recent login on Firebase's side. */
    fun updatePassword(newPassword: String, confirmPassword: String) {
        if (newPassword != confirmPassword) {
            _state.value = _state.value.copy(errorMessage = Strings.passwordMismatchError())
            return
        }
        _state.value = _state.value.copy(isSavingProfile = true, errorMessage = null, profileSaved = false)
        viewModelScope.launch {
            val result = repo.updatePassword(newPassword)
            result.onSuccess {
                _state.value = _state.value.copy(isSavingProfile = false, profileSaved = true)
            }.onFailure {
                _state.value = _state.value.copy(isSavingProfile = false, errorMessage = AppError.message(it))
            }
        }
    }

    /** Marks the post-signup profile prompt as seen, without editing any fields ("Skip"). */
    fun skipProfileCompletion() {
        val uid = repo.currentUid ?: return
        viewModelScope.launch {
            repo.markProfileCompleted(uid)
            _state.value = _state.value.copy(profile = _state.value.profile?.copy(profileCompleted = true))
        }
    }

    /** "Use individually" — skips team creation/joining; user stays teamless permanently. */
    fun setSoloMode(onDone: () -> Unit) {
        val uid = repo.currentUid ?: return
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch {
            val result = repo.setSoloMode(uid)
            result.onSuccess {
                _state.value = _state.value.copy(
                    isLoading = false,
                    profile = _state.value.profile?.copy(soloMode = true, profileCompleted = true)
                )
                onDone()
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, errorMessage = AppError.message(it))
            }
        }
    }

    fun clearProfileSaved() {
        _state.value = _state.value.copy(profileSaved = false)
    }
}