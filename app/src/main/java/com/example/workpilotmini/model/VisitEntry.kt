package com.example.workpilotmini.model

/** Mirrors a document in "teams/{teamId}/visits/{id}". */
data class VisitEntry(
    val id: String = "",
    val uid: String = "",
    val userName: String = "",
    val leadName: String = "",        // Customer / Shop name
    val businessType: String = "",    // e.g. Retailer, Wholesaler, Distributor (free text)
    val contactPerson: String = "",
    val phoneNumber: String = "",
    val visitPurpose: String = "",
    val notes: String = "",
    val lat: Double? = null,
    val lng: Double? = null,
    val address: String = "",
    val visitTime: Long = System.currentTimeMillis(),
    val nextVisitDate: Long? = null,   // epoch millis, null = no reminder set
    val reminderScheduled: Boolean = false
)