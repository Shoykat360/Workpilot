package com.example.workpilotmini.data

import com.example.workpilotmini.model.UserProfile
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = Firebase.auth
    private val usersCol = Firebase.db.collection(Firebase.USERS)

    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signUp(name: String, email: String, password: String, termsAcceptedAt: Long): Result<UserProfile> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("Sign up failed: no uid returned")
        val profile = UserProfile(
            uid = uid,
            name = name,
            email = email.trim().lowercase(),
            termsAcceptedAt = termsAcceptedAt
        )
        usersCol.document(uid).set(profile).await()
        profile
    }

    suspend fun login(email: String, password: String): Result<Unit> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        Unit
    }

    /** Sends a Firebase password-reset email. Caller shows a confirmation regardless of
     *  whether the email exists, to avoid leaking which emails are registered. */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }

    fun logout() = auth.signOut()

    suspend fun getCurrentProfile(): Result<UserProfile?> = runCatching {
        val uid = currentUid ?: return@runCatching null
        usersCol.document(uid).get().await().toObject(UserProfile::class.java)
    }

    suspend fun updateTeam(uid: String, teamId: String, role: String): Result<Unit> = runCatching {
        usersCol.document(uid).update(mapOf("teamId" to teamId, "role" to role)).await()
    }

    /** Self-service profile edit: name, mobile, address (mobile/address optional).
     *  Email/uid/role/teamId are never touched here — see firestore.rules. */
    suspend fun updateProfile(uid: String, name: String, mobile: String, address: String): Result<Unit> = runCatching {
        if (name.isBlank()) error("নাম দিন")
        usersCol.document(uid).update(
            mapOf(
                "name" to name.trim(),
                "mobile" to mobile.trim(),
                "address" to address.trim(),
                "profileCompleted" to true
            )
        ).await()
    }

    /** Marks the post-signup "complete your profile" prompt as seen without changing
     *  anything else, for when the user taps "Skip for now". */
    suspend fun markProfileCompleted(uid: String): Result<Unit> = runCatching {
        usersCol.document(uid).update(mapOf("profileCompleted" to true)).await()
    }

    /** Updates the Firebase Auth password for the currently signed-in user. Firebase
     *  requires a recent login for this; if it fails with a "requires recent login"
     *  error the caller should prompt the user to log out and back in. */
    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        if (newPassword.length < 6) error("নতুন পাসওয়ার্ড কমপক্ষে ৬ ক্যারেক্টার দিন")
        val user = auth.currentUser ?: error("লগইন করা নেই")
        user.updatePassword(newPassword).await()
    }

    /** Sets solo/single-use mode: the user skips team creation/joining entirely and
     *  uses the app individually. teamId stays blank. */
    suspend fun setSoloMode(uid: String): Result<Unit> = runCatching {
        usersCol.document(uid).update(
            mapOf("soloMode" to true, "profileCompleted" to true)
        ).await()
    }
}
