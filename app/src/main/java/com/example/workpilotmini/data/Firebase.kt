package com.example.workpilotmini.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Single shared entry point to Firebase Auth + Firestore instances used across repositories. */
object Firebase {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    const val USERS = "users"
    const val TEAMS = "teams"
    const val ATTENDANCE = "attendance"
    const val VISITS = "visits"
    // Root collection for solo-mode users' own attendance/visits, mirroring the
    // "teams/{teamId}/attendance|visits" shape as "soloData/{uid}/attendance|visits".
    // Kept separate from TEAMS so a solo user's data never depends on a team document
    // existing, and so firestore.rules can lock it down to owner-only access.
    const val SOLO_DATA = "soloData"

    private const val ACCOUNT_CREATION_APP_NAME = "workpilotmini-account-creation"

    /**
     * A FirebaseAuth instance backed by a *secondary* FirebaseApp, used only when an admin
     * creates a brand-new member account (email + password) from inside the app.
     *
     * We can't reuse [auth] for this: calling createUserWithEmailAndPassword on the default
     * FirebaseAuth instance immediately signs that new user in on this device, replacing the
     * admin's own session. Running it on a second, independent FirebaseApp avoids touching the
     * admin's session at all — [db] stays bound to the default app the whole time.
     */
    fun accountCreationAuth(): FirebaseAuth {
        val secondaryApp = try {
            FirebaseApp.getInstance(ACCOUNT_CREATION_APP_NAME)
        } catch (e: IllegalStateException) {
            val defaultApp = FirebaseApp.getInstance()
            FirebaseApp.initializeApp(defaultApp.applicationContext, defaultApp.options, ACCOUNT_CREATION_APP_NAME)
        }
        return FirebaseAuth.getInstance(secondaryApp)
    }
}
