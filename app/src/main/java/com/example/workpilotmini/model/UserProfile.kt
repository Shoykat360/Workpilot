package com.example.workpilotmini.model

import com.google.firebase.firestore.PropertyName

/** Mirrors a document in the top-level "users/{uid}" Firestore collection. */
data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val mobile: String = "",       // editable from Profile screen, optional
    val address: String = "",      // editable from Profile screen, optional
    val teamId: String = "",
    val role: String = "member",   // "admin" or "member"
    // True until an admin deactivates this user. Deactivated users are signed out on
    // next login attempt (see AuthViewModel) and can't be re-activated except by an admin.
    // NOTE: Kotlin compiles `isActive`'s getter as isActive(), and Firestore's POJO
    // mapper strips the "is" prefix from bean-style getters, so without this explicit
    // @PropertyName it would read/write the field as "active" instead of "isActive" —
    // silently mismatching firestore.rules (which checks resource.data.isActive) and
    // any query/update that references the "isActive" field name directly.
    @get:PropertyName("isActive")
    val isActive: Boolean = true,
    // True if the user chose "Use individually" on the team-setup screen instead of
    // creating/joining a team. Lets them skip team setup and go straight to the dashboard.
    val soloMode: Boolean = false,
    // True once the user has been through (or explicitly skipped) the post-signup
    // "complete your profile" prompt, so we don't nag them again on every login.
    val profileCompleted: Boolean = false,
    val termsAcceptedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis()
)
