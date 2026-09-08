package com.example.workpilotmini.data

import android.util.Log
import com.example.workpilotmini.model.VisitEntry
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class VisitRepository {
    private val db = Firebase.db

    /** [ownerId] is a teamId when [isSolo] is false, or the user's own uid when [isSolo]
     *  is true (solo users have no team document, so their data lives under a
     *  "soloData/{uid}" root instead of "teams/{teamId}"). */
    private fun visitsCol(ownerId: String, isSolo: Boolean) =
        db.collection(if (isSolo) Firebase.SOLO_DATA else Firebase.TEAMS)
            .document(ownerId)
            .collection(Firebase.VISITS)

    /** Creates a visit entry and returns it with its generated id (for scheduling a reminder). */
    suspend fun addVisit(ownerId: String, isSolo: Boolean, visit: VisitEntry): Result<VisitEntry> = runCatching {
        val docRef = visitsCol(ownerId, isSolo).document()
        val toSave = visit.copy(id = docRef.id)
        docRef.set(toSave).await()
        toSave
    }

    suspend fun markReminderScheduled(ownerId: String, isSolo: Boolean, visitId: String): Result<Unit> = runCatching {
        visitsCol(ownerId, isSolo).document(visitId).update("reminderScheduled", true).await()
    }

    /** Live feed of every visit logged by the team, newest first (lead-gen history). Admin only. */
    fun listenToTeamVisits(ownerId: String, isSolo: Boolean, onChange: (List<VisitEntry>) -> Unit): ListenerRegistration {
        return visitsCol(ownerId, isSolo)
            .orderBy("visitTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("VisitRepository", "listenToTeamVisits failed (ownerId=$ownerId, isSolo=$isSolo)", error)
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(VisitEntry::class.java) } ?: emptyList()
                onChange(list)
            }
    }

    /** Live feed of just this member's own visit history, newest first — matches the
     *  `visits` read rule (a member can only ever read their own visit docs). */
    fun listenToMyVisits(ownerId: String, isSolo: Boolean, uid: String, onChange: (List<VisitEntry>) -> Unit): ListenerRegistration {
        return visitsCol(ownerId, isSolo)
            .whereEqualTo("uid", uid)
            .orderBy("visitTime", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { it.toObject(VisitEntry::class.java) } ?: emptyList()
                onChange(list)
            }
    }

    /**
     * One-time fetch of visits logged since [sinceMillis], used by the Report screen to
     * build daily/weekly/monthly visit + reminder counts. Pass [uid] to scope the result
     * to one person (a member's own report, or a single team member's report opened by
     * the admin); leave it null to get the whole team's visits back (admin-wide report
     * only — matches the `visits` read rule, so this must only be called with uid == null
     * when the caller is really the team admin).
     */
    suspend fun getVisitsSince(
        ownerId: String,
        isSolo: Boolean,
        sinceMillis: Long,
        uid: String? = null
    ): Result<List<VisitEntry>> = runCatching {
        var query = visitsCol(ownerId, isSolo).whereGreaterThanOrEqualTo("visitTime", sinceMillis)
        if (uid != null) query = query.whereEqualTo("uid", uid)
        query.get().await().documents.mapNotNull { it.toObject(VisitEntry::class.java) }
    }

    /**
     * Live feed of upcoming "next visit" reminders, soonest first. Admin gets the whole
     * team's reminders (so they can see who's due where); a regular member only ever
     * gets their own — matches the `visits` read rule, so members never even request
     * data they'd be denied. Solo users always get the member-scoped query since the
     * collection only ever holds their own entries anyway.
     *
     * NOTE: the member-scoped query (uid == + nextVisitDate > + orderBy nextVisitDate)
     * needs a Firestore composite index. It's declared in firestore.indexes.json —
     * deploy it with `firebase deploy --only firestore:indexes`, or open this screen
     * once, check Logcat for the "The query requires an index" error, and click the
     * link it prints (takes a couple of minutes to build either way). The index is
     * defined by collection ID ("visits"), so it applies under "soloData/{uid}" too.
     */
    fun listenToUpcomingReminders(
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        isAdmin: Boolean,
        onChange: (List<VisitEntry>) -> Unit
    ): ListenerRegistration {
        var query = visitsCol(ownerId, isSolo)
            .whereGreaterThan("nextVisitDate", System.currentTimeMillis() - 86_400_000L)
        if (isSolo || !isAdmin) query = query.whereEqualTo("uid", uid)
        return query
            .orderBy("nextVisitDate", Query.Direction.ASCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e("VisitRepository", "listenToUpcomingReminders failed (ownerId=$ownerId, isSolo=$isSolo, isAdmin=$isAdmin)", error)
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { it.toObject(VisitEntry::class.java) } ?: emptyList()
                onChange(list)
            }
    }
}
