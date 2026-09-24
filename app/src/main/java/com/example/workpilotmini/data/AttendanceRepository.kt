package com.example.workpilotmini.data

import android.util.Log
import com.example.workpilotmini.model.AttendanceRecord
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AttendanceRepository {
    private val db = Firebase.db

    /** [ownerId] is a teamId when [isSolo] is false, or the user's own uid when [isSolo]
     *  is true (solo users have no team document, so their data lives under a
     *  "soloData/{uid}" root instead of "teams/{teamId}"). */
    private fun attendanceCol(ownerId: String, isSolo: Boolean) =
        db.collection(if (isSolo) Firebase.SOLO_DATA else Firebase.TEAMS)
            .document(ownerId)
            .collection(Firebase.ATTENDANCE)

    fun todayKey(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    suspend fun hasCheckedInToday(ownerId: String, isSolo: Boolean, uid: String): Result<Boolean> = runCatching {
        val key = todayKey()
        val snap = attendanceCol(ownerId, isSolo)
            .whereEqualTo("uid", uid)
            .whereEqualTo("dateKey", key)
            .limit(1)
            .get()
            .await()
        !snap.isEmpty
    }

    suspend fun checkIn(
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        userName: String,
        lat: Double?,
        lng: Double?,
        address: String? = null
    ): Result<Unit> = runCatching {
        // deterministic ID = same user + same day কখনোই দুইটা doc বানাতে পারবে না
        val docRef = attendanceCol(ownerId, isSolo).document("${uid}_${todayKey()}")
        val record = AttendanceRecord(
            id = docRef.id,
            uid = uid,
            userName = userName,
            dateKey = todayKey(),
            checkInTime = System.currentTimeMillis(),
            lat = lat,
            lng = lng,
            address = address
        )
        docRef.set(record).await()
    }

    /** Marks today's check-in as checked out (fills [AttendanceRecord.checkOutTime]/
     *  outLat/outLng/outAddress on the same doc). Requires [checkIn] to have been called
     *  earlier today — matches the `attendance` update rule, which only allows the author
     *  to flip these fields, and only once (checkOutTime must currently be null). */
    suspend fun checkOut(
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        lat: Double?,
        lng: Double?,
        outAddress: String? = null
    ): Result<Unit> = runCatching {
        val docRef = attendanceCol(ownerId, isSolo).document("${uid}_${todayKey()}")
        docRef.update(
            mapOf(
                "checkOutTime" to System.currentTimeMillis(),
                "outLat" to lat,
                "outLng" to lng,
                "outAddress" to outAddress
            )
        ).await()
    }

    /** Start of today (local time) in epoch millis — used for "today's" one-time counts
     *  (e.g. the admin stats row), where a live listener isn't needed. */
    fun startOfTodayMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /**
     * One-time fetch of attendance records since [sinceMillis], used by the Report screen
     * to build daily/weekly/monthly counts. Pass [uid] to scope the result to one person
     * (a member's own report, or a single team member's report opened by the admin);
     * leave it null to get the whole team's records back (admin-wide report only —
     * matches the `attendance` read rule, so this must only be called with uid == null
     * when the caller is really the team admin).
     */
    suspend fun getAttendanceSince(
        ownerId: String,
        isSolo: Boolean,
        sinceMillis: Long,
        uid: String? = null
    ): Result<List<AttendanceRecord>> = runCatching {
        var query = attendanceCol(ownerId, isSolo).whereGreaterThanOrEqualTo("checkInTime", sinceMillis)
        if (uid != null) query = query.whereEqualTo("uid", uid)
        query.get().await().documents.mapNotNull { it.toObject(AttendanceRecord::class.java) }
    }

    /**
     * Today's check-ins. Admin gets the whole team's list; a regular member only ever
     * gets their own record — matches the `attendance` read rule so members never even
     * request data they'd be denied, and never see teammates' check-in status. For solo
     * users the collection only ever holds their own records anyway, so the uid filter
     * is harmless either way.
     */
    fun listenToTodayAttendance(
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        isAdmin: Boolean,
        onChange: (List<AttendanceRecord>) -> Unit
    ): ListenerRegistration {
        var query = attendanceCol(ownerId, isSolo).whereEqualTo("dateKey", todayKey())
        if (isSolo || !isAdmin) query = query.whereEqualTo("uid", uid)
        return query.addSnapshotListener { snap, error ->
            if (error != null) {
                // Was being swallowed before — surface it so a rules/index problem is
                // visible instead of silently showing an empty list to the admin.
                Log.e("AttendanceRepository", "listenToTodayAttendance failed (ownerId=$ownerId, isSolo=$isSolo, isAdmin=$isAdmin)", error)
                onChange(emptyList())
                return@addSnapshotListener
            }
            val list = snap?.documents?.mapNotNull { it.toObject(AttendanceRecord::class.java) } ?: emptyList()
            onChange(list.sortedByDescending { it.checkInTime })
        }
    }
}