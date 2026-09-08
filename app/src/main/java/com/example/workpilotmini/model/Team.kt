package com.example.workpilotmini.model

/** Mirrors a document in the top-level "teams/{teamId}" Firestore collection. */
data class Team(
    val teamId: String = "",
    val name: String = "",
    val companyName: String = "",  // optional, shown on team-manage screen
    val location: String = "",     // team/office address, optional
    val inviteCode: String = "",
    val adminUid: String = "",
    val maxSize: Int = MAX_SIZE,   // enforced range: MIN_SIZE..MAX_SIZE
    val memberUids: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MIN_SIZE = 2
        const val MAX_SIZE = 5
    }
}
