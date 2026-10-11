package com.example.workpilotmini.localization

import androidx.compose.runtime.mutableStateOf

enum class Language { BN, EN }

/**
 * App-wide language switch. A single Compose-observable source of truth: every string
 * pulled from [Strings] reads this during composition, so flipping it from the toggle
 * button (see DashboardScreen) immediately re-composes all visible text app-wide —
 * no restart, no per-screen state needed.
 */
object AppLanguage {
    val current = mutableStateOf(Language.BN)

    fun toggle() {
        current.value = if (current.value == Language.BN) Language.EN else Language.BN
    }
}



object ReportStrings {
    private fun t(bn: String, en: String): String =
        if (AppLanguage.current.value == Language.BN) bn else en

    // Date range errors
    fun errStartAfterEnd() = t("শুরুর তারিখ শেষ তারিখের পরে হতে পারে না", "Start date can't be after the end date")
    fun errFuture() = t("ভবিষ্যতের তারিখ বাছাই করা যাবে না", "You can't pick a future date")
    fun errTooOld() = t("শুধু গত ৩ মাসের ডেটা দেখা যাবে। আরও পুরনো তারিখ বাছাই করা যাবে না", "Only the last 3 months of data can be viewed. Pick a more recent date")

    // Range presets
    fun presetToday() = t("আজ", "Today")
    fun preset7() = t("৭ দিন", "7 Days")
    fun preset30() = t("৩০ দিন", "30 Days")
    fun presetThisMonth() = t("এই মাস", "This Month")
    fun presetLastMonth() = t("গত মাস", "Last Month")
    fun preset3Months() = t("৩ মাস", "3 Months")
    fun presetCustom() = t("কাস্টম", "Custom")
    fun rangeHint() = t("গত ৩ মাসের যেকোনো দিন বা সময়কাল বেছে নিতে পারবেন", "You can pick any day or range from the last 3 months")
    fun customRangeTitle() = t("তারিখ বেছে নিন", "Select dates")


    // Extra stats
    fun avgHoursLabel() = t("গড় কাজের সময়", "Avg work time")
    fun hoursPerDayInline(h: String) = t("$h ঘণ্টা/দিন", "$h h/day")
    fun lateLabel() = t("দেরিতে Check-in", "Late check-ins")
    fun lateAfterInline(time: String) = t("$time এর পরে", "after $time")
    fun distanceLabel() = t("মোট দূরত্ব", "Distance")
    fun kmInline(km: String) = t("$km কিমি", "$km km")
    fun streakLabel() = t("টানা উপস্থিতি", "Attendance streak")
    fun daysInline(n: Int) = t("$n দিন", "$n days")
    fun bestStreakInline(n: Int) = t("সর্বোচ্চ: $n", "Best: $n")
    fun workingDaysInline(n: Int) = t("কর্মদিবস: $n (শুক্রবার বাদে)", "Working days: $n (Fridays excluded)")

    // Section headers
    fun activityChartHeader() = t("কার্যক্রম (নির্বাচিত সময়)", "Activity (selected period)")
    fun calendarHeader() = t("ক্যালেন্ডার", "Activity calendar")
    fun heatVisits() = t("ভিজিট", "Visits")
    fun heatAttendance() = t("শুধু উপস্থিতি", "Attendance only")
    fun heatNone() = t("কিছু নেই", "Nothing")
    fun businessHeader() = t("ব্যবসার ধরন অনুযায়ী ভিজিট", "Visits by business type")
    fun businessOther() = t("অন্যান্য", "Other")
    fun followUpHeader() = t("Reminder ফলো-আপ", "Reminder follow-up")
    fun followPastDue() = t("তারিখ পার", "Date passed")
    fun followToday() = t("আজ", "Today")
    fun followUpcoming() = t("আসন্ন", "Upcoming")
    fun followUpNote() = t("গত ৩ মাসের ভিজিট থেকে", "From the last 3 months of visits")

    // Admin — needs attention
    fun attentionHeader() = t("মনোযোগ দরকার", "Needs attention")
    fun absentHeader(n: Int) = t("অনুপস্থিত ($n)", "Absent ($n)")
    fun lateHeader(n: Int) = t("দেরিতে এসেছে ($n)", "Came late ($n)")
    fun inactiveHeader(n: Int) = t("কয়েকদিন ভিজিট নেই ($n)", "No recent visits ($n)")
    fun absentInline(name: String, days: Int) = t("$name • $days দিন অনুপস্থিত", "$name • absent $days day${if (days > 1) "s" else ""}")
    fun lateInline(name: String, count: Int) = t("$name • $count বার দেরি", "$name • late $count time${if (count > 1) "s" else ""}")
    fun inactiveInline(name: String, days: Int) = t("$name • $days দিন ভিজিট নেই", "$name • no visit for $days days")
    fun neverVisitedInline(name: String) = t("$name • ৩ মাসে কোনো ভিজিট নেই", "$name • no visit in 3 months")
    fun allGood() = t("সবাই ঠিকঠাক আছে ✅", "Everyone is on track ✅")

    // Admin — leaderboard
    fun leaderboardHeader() = t("টিম লিডারবোর্ড", "Team leaderboard")
    fun sortVisits() = t("ভিজিট", "Visits")
    fun sortAttendance() = t("উপস্থিতি", "Attendance")
    fun sortHours() = t("কাজের সময়", "Work time")
    fun sortDistance() = t("দূরত্ব", "Distance")
    fun presentInline(p: Int, w: Int) = t("উপস্থিত $p/$w", "Present $p/$w")
    fun absentCountInline(n: Int) = t("অনুপস্থিত $n", "Absent $n")
    fun lateCountInline(n: Int) = t("দেরি $n", "Late $n")
    fun pastDueInline(n: Int) = t("ফলো-আপ বাকি $n", "Follow-ups due $n")
    fun noVisitInline(days: Int) = t("$days দিন ভিজিট নেই", "No visit for $days days")
    fun routeButton() = t("রুট দেখুন", "View route")

    // Visits list + details
    fun visitsListHeader(n: Int) = t("ভিজিট তালিকা ($n)", "Visits ($n)")
    fun showMore() = t("আরও দেখুন", "Show more")
    fun showLess() = t("কম দেখান", "Show less")
    fun noVisitsInRange() = t("এই সময়ে কোনো ভিজিট নেই।", "No visits in this period.")
    fun noDataInRange() = t("এই সময়ের জন্য কোনো ডেটা নেই।", "No data for this period.")
    fun detailVisitedAt() = t("ভিজিটের সময়", "Visited at")
    fun detailBy() = t("যিনি করেছেন", "Visited by")
    fun detailBusiness() = t("ব্যবসার ধরন", "Business type")
    fun detailAddress() = t("লোকেশন", "Location")
    fun detailContact() = t("যোগাযোগের ব্যক্তি", "Contact person")
    fun detailPhone() = t("ফোন", "Phone")
    fun detailPurpose() = t("ভিজিটের উদ্দেশ্য", "Purpose")
    fun detailNotes() = t("নোট", "Notes")
    fun detailNextVisit() = t("পরবর্তী ভিজিট", "Next visit")
    fun callAction() = t("কল করুন", "Call")
    fun close() = t("বন্ধ করুন", "Close")

    // Share
    fun shareReport() = t("রিপোর্ট শেয়ার করুন", "Share report")
    fun shareChooserTitle() = t("রিপোর্ট শেয়ার করুন", "Share report via")

    // Check-in / check-out / reminder summary (people + daily + monthly)
    fun checkedOutLabel() = t("Checked Out", "Checked Out")
    fun teamPercentInline(pct: Int) = t("টিমের $pct%", "$pct% of team")
    fun totalCheckInsInline(n: Int) = t("মোট $n বার check-in", "$n check-ins total")
    fun totalCheckOutsInline(n: Int) = t("মোট $n বার check-out", "$n check-outs total")
    fun peopleUnit() = t("জন", "people")
    fun remindersDueInline(n: Int) = t("মেয়াদ এই সময়ে: $n", "Due in range: $n")
    fun dailyHeader() = t("দৈনিক সারসংক্ষেপ", "Daily summary")
    fun monthlyHeader() = t("মাসিক সারসংক্ষেপ", "Monthly summary")
    fun colDate() = t("তারিখ", "Date")
    fun colVisits() = t("ভিজিট", "Visits")
    fun colReminders() = t("Reminder\n(সেট/মেয়াদ)", "Reminder\n(set/due)")
    fun showAllDays(n: Int) = t("সব $n দিন দেখুন", "Show all $n days")
    fun monthCheckInTeam(people: Int, team: Int, personDays: Int, expected: Int) =
        t("Check-in: $people/$team জন • $personDays/$expected জন-দিন", "Check-in: $people/$team people • $personDays/$expected person-days")
    fun monthPresentSelf(p: Int, w: Int) = t("উপস্থিত: $p/$w কর্মদিবস", "Present: $p/$w working days")
    fun monthCheckOut(n: Int) = t("Check-out: $n বার", "Check-out: $n time${if (n != 1) "s" else ""}")
    fun monthVisits(n: Int) = t("ভিজিট: $n", "Visits: $n")
    fun monthReminders(set: Int, due: Int) = t("Reminder: $set টি সেট • $due টির মেয়াদ", "Reminders: $set set • $due due")

    // Tabs
    fun tabOverview() = t("সারসংক্ষেপ", "Overview")
    fun tabDaily() = t("দৈনিক", "Daily")
    fun tabTeam() = t("টিম", "Team")
    fun tabVisits() = t("ভিজিট", "Visits")

    // Member — last 7 days detail sheet
    fun tapForWeek() = t("ট্যাপ করে গত ৭ দিনের বিস্তারিত দেখুন", "Tap for last 7 days details")
    fun weekDetailTitle(name: String) = t("$name — গত ৭ দিন", "$name — last 7 days")
    fun weekPresent(p: Int, w: Int) = t("উপস্থিত $p/$w", "Present $p/$w")
    fun weekAbsent(n: Int) = t("অনুপস্থিত $n", "Absent $n")
    fun weekLate(n: Int) = t("দেরি $n", "Late $n")
    fun weekVisits(n: Int) = t("ভিজিট $n", "Visits $n")
    fun weekAvgHours(h: String) = t("গড় $h ঘণ্টা", "Avg $h h")
    fun dayAbsent() = t("অনুপস্থিত", "Absent")
    fun dayWeekend() = t("ছুটি", "Weekend")
    fun dayLate() = t("দেরি", "Late")
    fun dayNoCheckIn() = t("Check-in নেই", "No check-in")
    fun dayNotCheckedOut() = t("Check-out হয়নি", "Not checked out")
    fun dayHours(h: String) = t("$h ঘণ্টা", "$h h")
    fun dayNoVisits() = t("এই দিন কোনো ভিজিট নেই", "No visits this day")
    fun dayVisitsHeader(n: Int) = t("ভিজিট ($n)", "Visits ($n)")
}