package com.example.workpilotmini.ui.report

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.workpilotmini.data.AppError
import com.example.workpilotmini.data.AttendanceRepository
import com.example.workpilotmini.data.VisitRepository
import com.example.workpilotmini.localization.ReportStrings
import com.example.workpilotmini.model.AttendanceRecord
import com.example.workpilotmini.model.VisitEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// ───────────── Tunable report settings (change here, nowhere else) ─────────────
/** Users/admin can look back at most this many months. */
const val REPORT_MAX_MONTHS_BACK = 3
/** Check-in after this time counts as "late". */
const val LATE_AFTER_HOUR = 10
const val LATE_AFTER_MINUTE = 0
/** Admin gets a warning when a member has had no visit for this many days. */
const val INACTIVE_DAYS = 3
/** Days that are NOT counted as working days (so they never count as "absent"). */
val WEEKEND_DAYS: Set<Int> = setOf(Calendar.FRIDAY)
/** Assumed visits per working day per person (used for the visit progress bar). */
const val VISIT_DAILY_TARGET = 1

private const val DAY_MS = 86_400_000L

// Kept for backwards compatibility with any code that still uses the old 3 tabs.
enum class ReportPeriod { DAILY, WEEKLY, MONTHLY }

enum class RangePreset { TODAY, LAST_7, LAST_30, THIS_MONTH, LAST_MONTH, LAST_90, CUSTOM }

// ───────────── Date helpers ─────────────

fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun endOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(millis)
    add(Calendar.DAY_OF_YEAR, 1)
    add(Calendar.MILLISECOND, -1)
}.timeInMillis

fun addDays(millis: Long, days: Int): Long = Calendar.getInstance().apply {
    timeInMillis = startOfDay(millis)
    add(Calendar.DAY_OF_YEAR, days)
}.timeInMillis

fun isWeekend(millis: Long): Boolean =
    Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK) in WEEKEND_DAYS

/** Start of the earliest day the report may show (today minus [REPORT_MAX_MONTHS_BACK] months). */
fun earliestAllowedStart(): Long = startOfDay(
    Calendar.getInstance().apply { add(Calendar.MONTH, -REPORT_MAX_MONTHS_BACK) }.timeInMillis
)

/** Local day → the UTC-midnight value Material3 date pickers use. */
fun localDayToUtcMillis(localMillis: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** UTC-midnight value from a Material3 date picker → start of that same day in local time. */
fun utcDayToLocalStart(utcMillis: Long): Long {
    val u = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(u.get(Calendar.YEAR), u.get(Calendar.MONTH), u.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
    }.timeInMillis
}

fun presetRange(preset: RangePreset): Pair<Long, Long> {
    val now = System.currentTimeMillis()
    val todayEnd = endOfDay(now)
    val range: Pair<Long, Long> = when (preset) {
        RangePreset.TODAY, RangePreset.CUSTOM -> startOfDay(now) to todayEnd
        RangePreset.LAST_7 -> addDays(now, -6) to todayEnd
        RangePreset.LAST_30 -> addDays(now, -29) to todayEnd
        RangePreset.THIS_MONTH -> {
            val first = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            startOfDay(first) to todayEnd
        }
        RangePreset.LAST_MONTH -> {
            val first = Calendar.getInstance().apply {
                timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, -1)
            }.timeInMillis
            val lastDay = Calendar.getInstance().apply {
                timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis
            startOfDay(first) to endOfDay(lastDay)
        }
        RangePreset.LAST_90 -> earliestAllowedStart() to todayEnd
    }
    return maxOf(range.first, earliestAllowedStart()) to range.second
}

/** Returns a user-facing error message if the range isn't allowed, else null. */
fun validateRange(start: Long, end: Long): String? = when {
    start > end -> ReportStrings.errStartAfterEnd()
    end > endOfDay(System.currentTimeMillis()) -> ReportStrings.errFuture()
    start < earliestAllowedStart() -> ReportStrings.errTooOld()
    else -> null
}

// ───────────── UI models ─────────────

/** One row of the admin's per-member table. New fields all have defaults. */
data class MemberReportRow(
    val uid: String,
    val name: String,
    val attendanceCount: Int,
    val visitCount: Int,
    val reminderCount: Int,
    val presentDays: Int = 0,
    val absentDays: Int = 0,
    val lateCount: Int = 0,
    val avgHours: Double = 0.0,
    val distanceKm: Double = 0.0,
    val lastVisitTime: Long? = null,
    val daysSinceLastVisit: Int? = null,
    val pastDueFollowUps: Int = 0,
    /** Visits (with GPS) of the member's most recent active day in range, oldest first. */
    val routeVisits: List<VisitEntry> = emptyList()
)

/** Kept only so old references still compile. */
data class WeeklyActivity(
    val weekIndex: Int, val startDay: Int, val endDay: Int,
    val attendanceCount: Int, val visitCount: Int, val reminderCount: Int
)

/** One calendar day in a member's last-7-days detail. */
data class MemberDayDetail(
    val dayStart: Long,
    val isWeekend: Boolean,
    val firstCheckIn: Long?,
    val lastCheckOut: Long?,
    val isLate: Boolean,
    val isAbsent: Boolean,
    val hours: Double,
    val visits: List<VisitEntry>
)

data class MemberWeekDetail(
    val uid: String,
    val days: List<MemberDayDetail>,
    val workingDays: Int,
    val presentDays: Int,
    val absentDays: Int,
    val lateCount: Int,
    val visitCount: Int,
    val avgHours: Double
)

data class DayStat(
    val dayStart: Long,
    val attendance: Int,
    val visits: Int,
    /** Reminders SET on this day (visits saved with a next-visit date). */
    val reminders: Int,
    /** Distinct people who checked in / out this day. */
    val checkedInPeople: Int = 0,
    val checkedOutPeople: Int = 0,
    /** Reminders whose next-visit date falls on this day. */
    val remindersDue: Int = 0,
    /** Earliest check-in / latest check-out of the day (single-person view shows these times). */
    val firstCheckIn: Long? = null,
    val lastCheckOut: Long? = null
)

/** Totals for one calendar month (only the part inside the selected range). */
data class MonthSummary(
    val label: String,
    val monthStart: Long,
    val workingDays: Int,
    val presentDays: Int,
    val checkedInPeople: Int,
    val checkedInPersonDays: Int,
    val checkedOutPersonDays: Int,
    val visits: Int,
    val remindersSet: Int,
    val remindersDue: Int
)
data class ActivityBucket(val label: String, val attendance: Int, val visits: Int, val reminders: Int)
data class BusinessTypeStat(val type: String, val count: Int)
data class FollowUpSummary(val pastDue: Int = 0, val today: Int = 0, val upcoming: Int = 0)

data class ReportUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val rangeError: String? = null,
    val period: ReportPeriod = ReportPeriod.DAILY,
    val preset: RangePreset = RangePreset.TODAY,
    val rangeStart: Long = presetRange(RangePreset.TODAY).first,
    val rangeEnd: Long = presetRange(RangePreset.TODAY).second,

    val attendanceCount: Int = 0,
    val visitCount: Int = 0,
    val reminderCount: Int = 0,
    val checkOutCount: Int = 0,
    /** Team-wide: people (of [teamSize]) with at least one check-in / check-out in the range. */
    val teamSize: Int = 1,
    val peopleCheckedIn: Int = 0,
    val peopleCheckedOut: Int = 0,
    val dueRemindersInRange: Int = 0,
    val monthRows: List<MonthSummary> = emptyList(),
    val handledReminders: Int = 0,

    val workingDays: Int = 0,
    val attendanceRatePercent: Int = 0,
    val visitTarget: Int = 1,
    val avgHours: Double = 0.0,
    val lateCount: Int = 0,
    val distanceKm: Double = 0.0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,

    val visits: List<VisitEntry> = emptyList(),
    val memberBreakdown: List<MemberReportRow> = emptyList(),
    val weeklyBreakdown: List<WeeklyActivity> = emptyList(),
    val recentAttendance: List<AttendanceRecord> = emptyList(),
    val recentVisits: List<VisitEntry> = emptyList(),

    val dailyStats: List<DayStat> = emptyList(),
    val buckets: List<ActivityBucket> = emptyList(),
    val businessTypes: List<BusinessTypeStat> = emptyList(),
    val followUp: FollowUpSummary = FollowUpSummary(),

    // Admin → tapped a member → last 7 days full details
    val memberWeekOpen: Boolean = false,
    val memberWeekName: String = "",
    val memberWeekLoading: Boolean = false,
    val memberWeekError: String? = null,
    val memberWeek: MemberWeekDetail? = null
)

// ───────────── Pure calculation helpers ─────────────

private data class PersonStats(
    val presentDays: Int,
    val presentWorkingDays: Int,
    val lateCount: Int,
    val hoursTotal: Double,
    val hoursSamples: Int,
    val distanceKm: Double
) {
    val avgHours: Double get() = if (hoursSamples > 0) hoursTotal / hoursSamples else 0.0
}

private fun isLate(millis: Long): Boolean {
    val c = Calendar.getInstance().apply { timeInMillis = millis }
    val minutes = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    return minutes > LATE_AFTER_HOUR * 60 + LATE_AFTER_MINUTE
}

private fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
            Math.sin(dLng / 2) * Math.sin(dLng / 2)
    return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
}

private fun personStats(att: List<AttendanceRecord>, vis: List<VisitEntry>): PersonStats {
    val byUserDay = att.groupBy { it.uid to startOfDay(it.checkInTime) }
    var late = 0
    var workingPresent = 0
    byUserDay.forEach { (key, recs) ->
        val first = recs.minOf { it.checkInTime }
        if (isLate(first)) late++
        if (!isWeekend(key.second)) workingPresent++
    }
    var hours = 0.0
    var samples = 0
    att.forEach { r ->
        val out = r.checkOutTime
        if (out != null && out > r.checkInTime) {
            hours += (out - r.checkInTime) / 3_600_000.0
            samples++
        }
    }
    var km = 0.0
    vis.filter { it.lat != null && it.lng != null }
        .groupBy { it.uid to startOfDay(it.visitTime) }
        .values
        .forEach { dayList ->
            val sorted = dayList.sortedBy { it.visitTime }
            for (i in 1 until sorted.size) {
                val a = sorted[i - 1]
                val b = sorted[i]
                val aLat = a.lat; val aLng = a.lng; val bLat = b.lat; val bLng = b.lng
                if (aLat != null && aLng != null && bLat != null && bLng != null) {
                    km += haversineKm(aLat, aLng, bLat, bLng)
                }
            }
        }
    return PersonStats(byUserDay.size, workingPresent, late, hours, samples, km)
}

/** Returns (current streak, best streak) in days. Weekend days without attendance never break a streak. */
private fun computeStreaks(days: Set<Long>, today: Long): Pair<Int, Int> {
    var cursor = days.minOrNull() ?: return 0 to 0
    var run = 0
    var best = 0
    val yesterday = addDays(today, -1)
    var guard = 0
    while (cursor <= yesterday && guard < 500) {
        if (cursor in days) {
            run++
            if (run > best) best = run
        } else if (!isWeekend(cursor)) {
            run = 0
        }
        cursor = addDays(cursor, 1)
        guard++
    }
    if (today in days) {
        run++
        if (run > best) best = run
    }
    return run to best
}

private fun dayLabel(millis: Long): String =
    SimpleDateFormat("d/M", Locale.getDefault()).format(Date(millis))

// ───────────── ViewModel ─────────────

/**
 * Powers the Report screen. Scope rule is unchanged: the real team admin on the whole-team
 * report sees everyone; a member / solo user / single-member drill-down sees one person.
 *
 * The report covers any date range within the last [REPORT_MAX_MONTHS_BACK] months and
 * compares it with the equally long period right before it.
 */
class ReportViewModel : ViewModel() {
    private val attendanceRepo = AttendanceRepository()
    private val visitRepo = VisitRepository()
    private var loadJob: Job? = null
    private var weekJob: Job? = null

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun setPreset(preset: RangePreset) {
        val (s, e) = presetRange(preset)
        _state.value = _state.value.copy(preset = preset, rangeStart = s, rangeEnd = e, rangeError = null)
    }

    /** [startMillis]/[endMillis] are local-time millis (any time of day). Invalid → rangeError, range unchanged. */
    fun setCustomRange(startMillis: Long, endMillis: Long) {
        val s = startOfDay(startMillis)
        val e = endOfDay(endMillis)
        val error = validateRange(s, e)
        _state.value = if (error != null) {
            _state.value.copy(rangeError = error)
        } else {
            _state.value.copy(preset = RangePreset.CUSTOM, rangeStart = s, rangeEnd = e, rangeError = null)
        }
    }

    /** Admin tapped a member card: loads that member's last 7 days (today included) in full detail. */
    fun openMemberWeek(ownerId: String, isSolo: Boolean, memberUid: String, memberName: String) {
        _state.value = _state.value.copy(
            memberWeekOpen = true, memberWeekName = memberName,
            memberWeekLoading = true, memberWeekError = null, memberWeek = null
        )
        weekJob?.cancel()
        weekJob = viewModelScope.launch {
            val today = startOfDay(System.currentTimeMillis())
            val since = addDays(today, -6)
            val attendanceResult = attendanceRepo.getAttendanceSince(ownerId, isSolo, since, memberUid)
            val visitsResult = visitRepo.getVisitsSince(ownerId, isSolo, since, memberUid)
            if (attendanceResult.isFailure || visitsResult.isFailure) {
                _state.value = _state.value.copy(
                    memberWeekLoading = false,
                    memberWeekError = AppError.message(attendanceResult.exceptionOrNull() ?: visitsResult.exceptionOrNull()!!)
                )
                return@launch
            }
            val att = attendanceResult.getOrDefault(emptyList()).filter { it.uid == memberUid }
            val vis = visitsResult.getOrDefault(emptyList()).filter { it.uid == memberUid }

            val days = (0..6).map { i ->
                val ds = addDays(since, i)
                val de = endOfDay(ds)
                val dayAtt = att.filter { it.checkInTime in ds..de }
                val dayVis = vis.filter { it.visitTime in ds..de }.sortedBy { it.visitTime }
                val first = dayAtt.minOfOrNull { it.checkInTime }
                val lastOut = dayAtt.mapNotNull { it.checkOutTime }.maxOrNull()
                var hours = 0.0
                dayAtt.forEach { r ->
                    val out = r.checkOutTime
                    if (out != null && out > r.checkInTime) hours += (out - r.checkInTime) / 3_600_000.0
                }
                MemberDayDetail(
                    dayStart = ds,
                    isWeekend = isWeekend(ds),
                    firstCheckIn = first,
                    lastCheckOut = lastOut,
                    isLate = first != null && isLate(first),
                    isAbsent = first == null && !isWeekend(ds),
                    hours = hours,
                    visits = dayVis
                )
            }.reversed() // newest day first

            val workingDays = days.count { !it.isWeekend }
            val withHours = days.filter { it.hours > 0 }
            _state.value = _state.value.copy(
                memberWeekLoading = false,
                memberWeek = MemberWeekDetail(
                    uid = memberUid,
                    days = days,
                    workingDays = workingDays,
                    presentDays = days.count { it.firstCheckIn != null && !it.isWeekend },
                    absentDays = days.count { it.isAbsent },
                    lateCount = days.count { it.isLate },
                    visitCount = vis.size,
                    avgHours = if (withHours.isEmpty()) 0.0 else withHours.sumOf { it.hours } / withHours.size
                )
            )
        }
    }

    fun closeMemberWeek() {
        weekJob?.cancel()
        _state.value = _state.value.copy(
            memberWeekOpen = false, memberWeekLoading = false, memberWeekError = null, memberWeek = null
        )
    }

    fun clearRangeError() {
        _state.value = _state.value.copy(rangeError = null)
    }

    /** Old API (3 tabs) mapped onto the new presets. */
    fun setPeriod(
        period: ReportPeriod,
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        isAdmin: Boolean,
        focusUid: String? = null,
        focusName: String? = null,
        teamMembers: List<Pair<String, String>> = emptyList()
    ) {
        val preset = when (period) {
            ReportPeriod.DAILY -> RangePreset.TODAY
            ReportPeriod.WEEKLY -> RangePreset.LAST_7
            ReportPeriod.MONTHLY -> RangePreset.THIS_MONTH
        }
        setPreset(preset)
        _state.value = _state.value.copy(period = period)
        load(ownerId, isSolo, uid, isAdmin, focusUid, focusName, teamMembers)
    }

    fun load(
        ownerId: String,
        isSolo: Boolean,
        uid: String,
        isAdmin: Boolean,
        focusUid: String? = null,
        focusName: String? = null,
        teamMembers: List<Pair<String, String>> = emptyList()
    ) {
        // Presets like "Today" / "Last 7 days" are re-evaluated on every load so they
        // never go stale after midnight; a custom range stays exactly as picked.
        var s = _state.value
        if (s.preset != RangePreset.CUSTOM) {
            val (rs, re) = presetRange(s.preset)
            s = s.copy(rangeStart = rs, rangeEnd = re)
        }
        val error = validateRange(s.rangeStart, s.rangeEnd)
        if (error != null) {
            _state.value = s.copy(rangeError = error, isLoading = false)
            return
        }
        val rangeStart = s.rangeStart
        val rangeEnd = s.rangeEnd
        _state.value = s.copy(isLoading = true, errorMessage = null, rangeError = null)

        val teamWide = focusUid == null && isAdmin && !isSolo
        val scopeUid = focusUid ?: if (teamWide) null else uid

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val todayStart = startOfDay(now)
            val todayEnd = endOfDay(now)
            // One query covers the selected range and the whole 3-month window
            // (needed for streaks / follow-ups / inactivity).
            val fetchSince = minOf(rangeStart, earliestAllowedStart())

            val attendanceResult = attendanceRepo.getAttendanceSince(ownerId, isSolo, fetchSince, scopeUid)
            val visitsResult = visitRepo.getVisitsSince(ownerId, isSolo, fetchSince, scopeUid)
            if (attendanceResult.isFailure || visitsResult.isFailure) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    errorMessage = AppError.message(attendanceResult.exceptionOrNull() ?: visitsResult.exceptionOrNull()!!)
                )
                return@launch
            }
            val attendanceAll = attendanceResult.getOrDefault(emptyList())
            val visitsAll = visitsResult.getOrDefault(emptyList())

            val att = attendanceAll.filter { it.checkInTime in rangeStart..rangeEnd }
            val vis = visitsAll.filter { it.visitTime in rangeStart..rangeEnd }

            // ---- per-day stats (calendar heatmap + chart) ----
            val dailyStats = mutableListOf<DayStat>()
            var cursor = rangeStart
            var guard = 0
            while (cursor <= rangeEnd && guard < 400) {
                val ds = cursor
                val de = endOfDay(ds)
                val dayAtt = att.filter { it.checkInTime in ds..de }
                dailyStats += DayStat(
                    dayStart = ds,
                    attendance = dayAtt.size,
                    visits = vis.count { it.visitTime in ds..de },
                    reminders = vis.count { it.visitTime in ds..de && it.nextVisitDate != null },
                    checkedInPeople = dayAtt.map { it.uid }.distinct().size,
                    checkedOutPeople = dayAtt.filter { it.checkOutTime != null }.map { it.uid }.distinct().size,
                    remindersDue = visitsAll.count { it.nextVisitDate != null && it.nextVisitDate in ds..de },
                    firstCheckIn = dayAtt.minOfOrNull { it.checkInTime },
                    lastCheckOut = dayAtt.mapNotNull { it.checkOutTime }.maxOrNull()
                )
                cursor = addDays(ds, 1)
                guard++
            }
            val buckets = if (dailyStats.size <= 7) {
                dailyStats.map { ActivityBucket(dayLabel(it.dayStart), it.attendance, it.visits, it.reminders) }
            } else {
                dailyStats.chunked(7).map { chunk ->
                    ActivityBucket(
                        dayLabel(chunk.first().dayStart),
                        chunk.sumOf { it.attendance }, chunk.sumOf { it.visits }, chunk.sumOf { it.reminders }
                    )
                }
            }
            val workingDays = dailyStats.count { !isWeekend(it.dayStart) }

            // ---- monthly totals (newest month first) ----
            val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val monthRows = dailyStats
                .groupBy { d ->
                    val c = Calendar.getInstance().apply { timeInMillis = d.dayStart }
                    c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH)
                }
                .values
                .map { days ->
                    val first = days.first().dayStart
                    val last = endOfDay(days.last().dayStart)
                    MonthSummary(
                        label = monthFmt.format(Date(first)),
                        monthStart = first,
                        workingDays = days.count { !isWeekend(it.dayStart) },
                        presentDays = days.count { it.checkedInPeople > 0 },
                        checkedInPeople = att.filter { it.checkInTime in first..last }.map { it.uid }.distinct().size,
                        checkedInPersonDays = days.sumOf { it.checkedInPeople },
                        checkedOutPersonDays = days.sumOf { it.checkedOutPeople },
                        visits = days.sumOf { it.visits },
                        remindersSet = days.sumOf { it.reminders },
                        remindersDue = days.sumOf { it.remindersDue }
                    )
                }
                .reversed()

            // ---- roster (team-wide only) ----
            val roster = LinkedHashMap<String, String>()
            if (teamWide) {
                if (teamMembers.isNotEmpty()) {
                    // Seed with the CURRENT roster: members with no activity still get a row,
                    // and removed members never reappear because of old records.
                    teamMembers.forEach { (memberUid, memberName) -> roster[memberUid] = memberName }
                } else {
                    attendanceAll.forEach { roster[it.uid] = it.userName }
                    visitsAll.forEach { if (it.uid !in roster) roster[it.uid] = it.userName }
                }
            }
            val personsCount = if (teamWide) roster.size.coerceAtLeast(1) else 1

            // ---- overall stats ----
            val overall = personStats(att, vis)
            val attendanceRate = when {
                workingDays > 0 ->
                    ((overall.presentWorkingDays * 100) / (workingDays * personsCount)).coerceIn(0, 100)
                overall.presentDays > 0 -> 100
                else -> 0
            }
            val visitTarget = (workingDays.coerceAtLeast(1) * personsCount * VISIT_DAILY_TARGET).coerceAtLeast(1)

            // ---- per-member rows ----
            val rows = if (!teamWide) emptyList<MemberReportRow>() else roster.map { (memberUid, memberName) ->
                val attU = att.filter { it.uid == memberUid }
                val visU = vis.filter { it.uid == memberUid }
                val st = personStats(attU, visU)
                val lastVisit = visitsAll.filter { it.uid == memberUid }.maxOfOrNull { it.visitTime }
                val geo = visU.filter { it.lat != null && it.lng != null }
                val lastGeoDay = geo.maxOfOrNull { startOfDay(it.visitTime) }
                val route = if (lastGeoDay == null) emptyList()
                else geo.filter { startOfDay(it.visitTime) == lastGeoDay }.sortedBy { it.visitTime }
                MemberReportRow(
                    uid = memberUid,
                    name = memberName,
                    attendanceCount = attU.size,
                    visitCount = visU.size,
                    reminderCount = visU.count { it.nextVisitDate != null },
                    presentDays = st.presentDays,
                    absentDays = (workingDays - st.presentWorkingDays).coerceAtLeast(0),
                    lateCount = st.lateCount,
                    avgHours = st.avgHours,
                    distanceKm = st.distanceKm,
                    lastVisitTime = lastVisit,
                    daysSinceLastVisit = lastVisit?.let { ((todayStart - startOfDay(it)) / DAY_MS).toInt().coerceAtLeast(0) },
                    pastDueFollowUps = visitsAll.count {
                        it.uid == memberUid && it.nextVisitDate != null && it.nextVisitDate < todayStart
                    },
                    routeVisits = route
                )
            }.sortedByDescending { it.visitCount + it.attendanceCount }

            // ---- follow-ups (whole 3-month window, independent of the selected range) ----
            val followUp = FollowUpSummary(
                pastDue = visitsAll.count { it.nextVisitDate != null && it.nextVisitDate < todayStart },
                today = visitsAll.count { it.nextVisitDate != null && it.nextVisitDate in todayStart..todayEnd },
                upcoming = visitsAll.count { it.nextVisitDate != null && it.nextVisitDate > todayEnd }
            )

            // ---- streaks (single person only) ----
            val (currentStreak, bestStreak) = if (teamWide) 0 to 0
            else computeStreaks(attendanceAll.map { startOfDay(it.checkInTime) }.toSet(), todayStart)

            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = null,
                attendanceCount = att.size,
                checkOutCount = att.count { it.checkOutTime != null },
                teamSize = personsCount,
                peopleCheckedIn = att.map { it.uid }.distinct().size,
                peopleCheckedOut = att.filter { it.checkOutTime != null }.map { it.uid }.distinct().size,
                dueRemindersInRange = dailyStats.sumOf { it.remindersDue },
                monthRows = monthRows,
                visitCount = vis.size,
                reminderCount = vis.count { it.nextVisitDate != null },
                handledReminders = vis.count { it.nextVisitDate != null && it.nextVisitDate < now },
                workingDays = workingDays,
                attendanceRatePercent = attendanceRate,
                visitTarget = visitTarget,
                avgHours = overall.avgHours,
                lateCount = overall.lateCount,
                distanceKm = overall.distanceKm,
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                visits = vis.sortedByDescending { it.visitTime },
                memberBreakdown = rows,
                recentAttendance = att.sortedByDescending { it.checkInTime },
                recentVisits = vis.sortedByDescending { it.visitTime },
                dailyStats = dailyStats,
                buckets = buckets,
                businessTypes = vis.groupBy { it.businessType.trim() }
                    .map { (type, list) -> BusinessTypeStat(type, list.size) }
                    .sortedByDescending { it.count }
                    .take(6),
                followUp = followUp
            )
        }
    }

    /** Old fixed-month chart loader — the chart now follows the selected range, so this does nothing. */
    @Deprecated("Chart now follows the selected date range; load() fills everything.")
    fun loadMonthlyBreakdown(
        ownerId: String, isSolo: Boolean, uid: String, isAdmin: Boolean, focusUid: String? = null
    ) { /* no-op */ }
}