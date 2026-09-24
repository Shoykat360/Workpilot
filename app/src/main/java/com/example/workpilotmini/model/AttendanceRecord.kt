package com.example.workpilotmini.model

/** Mirrors a document in "teams/{teamId}/attendance/{id}". One check-in (+ optional
 *  check-out) per user per day — check-out fields start null and are filled in later
 *  the same day via [com.example.workpilotmini.data.AttendanceRepository.checkOut].
 *
 *  [address]/[outAddress] are a best-effort human-readable reverse-geocoded string
 *  (e.g. "Gulshan, Dhaka") resolved from [lat]/[lng] and [outLat]/[outLng] at the
 *  moment of check-in/out. They can be null even when the coordinates are present —
 *  geocoding needs a network/backend service and can fail, so the UI always falls
 *  back to showing the raw coordinates in that case. */
data class AttendanceRecord(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val dateKey: String = "",     // "yyyy-MM-dd", used to prevent duplicate check-ins in one day
    val checkInTime: Long = System.currentTimeMillis(),
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String? = null,
    val checkOutTime: Long? = null,
    val outLat: Double? = null,
    val outLng: Double? = null,
    val outAddress: String? = null
)