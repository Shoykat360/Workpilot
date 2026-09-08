package com.example.workpilotmini.data

import com.example.workpilotmini.model.Team
import com.example.workpilotmini.model.UserProfile
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

class TeamRepository {
    private val db = Firebase.db
    private val teamsCol = db.collection(Firebase.TEAMS)
    private val usersCol = db.collection(Firebase.USERS)
    private val authRepo = AuthRepository()

    /** Creates a team with [maxSize] clamped to 2..5. [companyName] and [location] are
     *  both optional. Creator becomes admin. */
    suspend fun createTeam(
        name: String,
        maxSize: Int,
        adminUid: String,
        companyName: String = "",
        location: String = ""
    ): Result<Team> = runCatching {
        val clampedSize = maxSize.coerceIn(Team.MIN_SIZE, Team.MAX_SIZE)
        val code = generateUniqueInviteCode()
        val docRef = teamsCol.document()
        val team = Team(
            teamId = docRef.id,
            name = name,
            companyName = companyName.trim(),
            location = location.trim(),
            inviteCode = code,
            adminUid = adminUid,
            maxSize = clampedSize,
            memberUids = listOf(adminUid)
        )
        docRef.set(team).await()
        authRepo.updateTeam(adminUid, docRef.id, role = "admin").getOrThrow()
        team
    }

    /** Joins an existing team by 6-char invite code. Fails if team is full. */
    suspend fun joinTeamByCode(code: String, uid: String): Result<Team> = runCatching {
        val snapshot = teamsCol.whereEqualTo("inviteCode", code.uppercase()).limit(1).get().await()
        val doc = snapshot.documents.firstOrNull() ?: error("এই কোডে কোনো টিম পাওয়া যায়নি")
        val team = doc.toObject(Team::class.java) ?: error("টিম ডেটা পড়া যায়নি")

        if (team.memberUids.contains(uid)) return@runCatching team
        if (team.memberUids.size >= team.maxSize) error("টিম পূর্ণ (সর্বোচ্চ ${team.maxSize} জন)")

        val updatedMembers = team.memberUids + uid
        doc.reference.update("memberUids", updatedMembers).await()
        authRepo.updateTeam(uid, team.teamId, role = "member").getOrThrow()
        team.copy(memberUids = updatedMembers)
    }

    suspend fun getTeam(teamId: String): Result<Team?> = runCatching {
        teamsCol.document(teamId).get().await().toObject(Team::class.java)
    }

    /**
     * Admin-only: adds an existing user directly to the team by email — no invite code
     * needed. Only works on users who have signed up already and aren't on any team yet
     * (protects against an admin "stealing" a member from another team).
     */
    suspend fun addMemberByEmail(team: Team, email: String, requestingAdminUid: String): Result<UserProfile> = runCatching {
        if (team.adminUid != requestingAdminUid) error("শুধু Admin মেম্বার add করতে পারবে")

        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) error("ইমেইল দিন")
        if (team.memberUids.size >= team.maxSize) error("টিম পূর্ণ (সর্বোচ্চ ${team.maxSize} জন)")

        val snapshot = usersCol
            .whereEqualTo("teamId", "")
            .whereEqualTo("email", normalizedEmail)
            .limit(1)
            .get()
            .await()
        val doc = snapshot.documents.firstOrNull()
            ?: error("এই ইমেইলে কোনো অ্যাকাউন্ট পাওয়া যায়নি। তাকে আগে অ্যাপে Sign up করতে বলুন, তারপর আবার Add করুন।")
        val target = doc.toObject(UserProfile::class.java) ?: error("ইউজার প্রোফাইল পড়া যায়নি")

        if (target.uid == requestingAdminUid) error("এটা আপনার নিজের ইমেইল")
        if (target.uid in team.memberUids) error("এই ইউজার আগে থেকেই টিমে আছে")
        if (target.teamId.isNotBlank()) error("এই ইউজার ইতিমধ্যে অন্য একটি টিমে আছে")

        // Order matters for rule evaluation: flip the user's own doc first (rule checks
        // resource.data.teamId == '' at the time of this write), then append to the team.
        usersCol.document(target.uid).update(
            mapOf("teamId" to team.teamId, "role" to "member")
        ).await()
        teamsCol.document(team.teamId).update(
            "memberUids", team.memberUids + target.uid
        ).await()

        target.copy(teamId = team.teamId, role = "member")
    }

    // NOTE: "Create member account directly" (admin sets email + password themselves) has
    // been intentionally disabled per product decision — the only supported way to add a
    // member now is [addMemberByEmail], which requires the person to have signed up on
    // their own first. Left here commented out in case this flow needs to come back.
    //
    // suspend fun createMemberAccount(
    //     team: Team,
    //     name: String,
    //     email: String,
    //     password: String,
    //     requestingAdminUid: String
    // ): Result<UserProfile> = runCatching {
    //     if (team.adminUid != requestingAdminUid) error("শুধু Admin অ্যাকাউন্ট তৈরি করতে পারবে")
    //     if (name.isBlank()) error("নাম দিন")
    //     if (team.memberUids.size >= team.maxSize) error("টিম পূর্ণ (সর্বোচ্চ ${team.maxSize} জন)")
    //
    //     val normalizedEmail = email.trim().lowercase()
    //     if (normalizedEmail.isBlank()) error("ইমেইল দিন")
    //     if (password.length < 6) error("পাসওয়ার্ড কমপক্ষে ৬ ক্যারেক্টার দিন")
    //
    //     val creationAuth = Firebase.accountCreationAuth()
    //     val authResult = creationAuth.createUserWithEmailAndPassword(normalizedEmail, password).await()
    //     val newUid = authResult.user?.uid ?: error("অ্যাকাউন্ট তৈরি ব্যর্থ")
    //     creationAuth.signOut()
    //
    //     val profile = UserProfile(
    //         uid = newUid,
    //         name = name.trim(),
    //         email = normalizedEmail,
    //         teamId = team.teamId,
    //         role = "member"
    //     )
    //     usersCol.document(newUid).set(profile).await()
    //     teamsCol.document(team.teamId).update("memberUids", team.memberUids + newUid).await()
    //
    //     profile
    // }

    /** Admin-only: activate or deactivate a team member's account. A deactivated user
     *  is signed out on their next login attempt (see AuthViewModel). Admin can't
     *  deactivate themself. */
    suspend fun setMemberActive(team: Team, targetUid: String, isActive: Boolean, requestingAdminUid: String): Result<Unit> = runCatching {
        if (team.adminUid != requestingAdminUid) error("শুধু Admin এই কাজ করতে পারবে")
        if (targetUid == requestingAdminUid) error("নিজেকে deactivate করা যাবে না")
        if (targetUid !in team.memberUids) error("এই ইউজার টিমে নেই")
        usersCol.document(targetUid).update("isActive", isActive).await()
    }

    /** Admin-only: removes a member from the team (they keep their account/login but
     *  are no longer on any team, and can join/create another one). Admin can't remove
     *  themself this way — they'd need to delete/transfer the team instead. */
    suspend fun removeMember(team: Team, targetUid: String, requestingAdminUid: String): Result<Unit> = runCatching {
        if (team.adminUid != requestingAdminUid) error("শুধু Admin member remove করতে পারবে")
        if (targetUid == requestingAdminUid) error("নিজেকে remove করা যাবে না")
        if (targetUid !in team.memberUids) error("এই ইউজার টিমে নেই")

        usersCol.document(targetUid).update(
            mapOf("teamId" to "", "role" to "member")
        ).await()
        teamsCol.document(team.teamId).update(
            "memberUids", team.memberUids - targetUid
        ).await()
    }

    /**
     * Admin-only: removes a member from the team AND deletes their profile document.
     * NOTE: this only deletes the Firestore "users/{uid}" profile — it cannot delete the
     * person's underlying Firebase Auth account from the client app (that requires the
     * Admin SDK / a Cloud Function running with elevated privileges). Their email would
     * still be able to log in again with an empty/new profile unless that's handled
     * server-side separately.
     */
    suspend fun deleteMember(team: Team, targetUid: String, requestingAdminUid: String): Result<Unit> = runCatching {
        if (team.adminUid != requestingAdminUid) error("শুধু Admin member delete করতে পারবে")
        if (targetUid == requestingAdminUid) error("নিজেকে delete করা যাবে না")
        if (targetUid !in team.memberUids) error("এই ইউজার টিমে নেই")

        teamsCol.document(team.teamId).update(
            "memberUids", team.memberUids - targetUid
        ).await()
        usersCol.document(targetUid).delete().await()
    }

    /** Fetches display info (name/email) for every member in [uids], for the team management screen. */
    suspend fun getTeamMembers(teamId: String, uids: List<String>): Result<List<UserProfile>> = runCatching {
        if (uids.isEmpty()) return@runCatching emptyList()
        // teamId filter yog kora holo query-r rule-er "resource.data.teamId == myProfile().teamId"
        // condition-er sathe mile jay, tai Firestore eta statically validate korte pare.
        val snapshot = usersCol
            .whereEqualTo("teamId", teamId)
            .whereIn("uid", uids)
            .get()
            .await()
        snapshot.documents.mapNotNull { it.toObject(UserProfile::class.java) }
    }

    fun listenToTeam(teamId: String, onChange: (Team?) -> Unit): ListenerRegistration {
        return teamsCol.document(teamId).addSnapshotListener { snap, _ ->
            onChange(snap?.toObject(Team::class.java))
        }
    }

    private suspend fun generateUniqueInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no confusing 0/O/1/I
        repeat(5) {
            val code = (1..6).map { chars.random() }.joinToString("")
            val existing = teamsCol.whereEqualTo("inviteCode", code).limit(1).get().await()
            if (existing.isEmpty) return code
        }
        error("Invite code generate করা যায়নি, আবার চেষ্টা করুন")
    }
}
