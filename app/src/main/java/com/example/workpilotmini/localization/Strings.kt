package com.example.workpilotmini.localization

/**
 * Central place for every user-facing string in the app, in Bengali and English.
 * Each function reads [AppLanguage.current] at call time, so as long as it's called
 * from inside a @Composable body, the caller recomposes automatically when the
 * language is toggled. Add new UI text here rather than inlining literals in screens,
 * so the language toggle keeps covering the whole app.
 */
object Strings {
    private fun t(bn: String, en: String): String =
        if (AppLanguage.current.value == Language.BN) bn else en

    // Common
    fun cancel() = t("বাতিল", "Cancel")
    fun ok() = t("ঠিক আছে", "OK")
    fun retry() = t("আবার নিন", "Retry")
    fun clear() = t("Clear", "Clear")
    fun add() = t("Add", "Add")

    // App / dashboard
    fun appName() = "WorkPilot Mini"
    fun hi(name: String) = t("Hi, $name", "Hi, $name")
    fun teamAdmin() = t("Team Admin", "Team Admin")
    fun teamMember() = t("Team Member", "Team Member")
    fun memberCount(count: Int, max: Int) = t("$count / $max সদস্য", "$count / $max members")
    fun inviteCodeInline(code: String) = t("Invite code: $code", "Invite code: $code")
    fun logout() = t("লগ আউট", "Log out")

    fun attendanceTileTitle() = t("Attendance", "Attendance")
    fun attendanceTileSubtitle() = t("আজকের check-in দিন", "Do today's check-in")
    fun visitEntryTileTitle() = t("Visit Entry", "Visit Entry")
    fun visitEntryTileSubtitle() = t("নতুন ভিজিট এন্ট্রি করুন", "Log a new visit")
    fun remindersTileTitle() = t("Reminders", "Reminders")
    fun remindersTileSubtitle() = t("পরবর্তী ভিজিটের তারিখগুলো দেখুন", "See your upcoming visit dates")
    fun visitHistoryTileTitle() = t("Visit History", "Visit History")
    fun visitHistoryTileSubtitleAdmin() = t("টিমের সবার visit entry দেখুন", "See every visit entry from the team")
    fun visitHistoryTileSubtitleMember() = t("আপনার visit entry গুলো দেখুন", "See your visit entries")
    fun languageToggleLabel() = t("EN", "বাং")

    // Login
    fun loginSubtitle() = t("লগইন করুন", "Log in")
    fun email() = t("ইমেইল", "Email")
    fun password() = t("পাসওয়ার্ড", "Password")
    fun forgotPassword() = t("পাসওয়ার্ড ভুলে গেছেন?", "Forgot password?")
    fun login() = t("লগইন", "Login")
    fun noAccountPrefix() = t("অ্যাকাউন্ট নেই? ", "Don't have an account? ")
    fun signUpLink() = t("সাইন আপ করুন", "Sign up")
    fun resetPasswordTitle() = t("পাসওয়ার্ড রিসেট", "Reset password")
    fun resetPasswordPrompt() = t("আপনার ইমেইল দিন, একটি পাসওয়ার্ড রিসেট লিংক পাঠানো হবে।", "Enter your email and we'll send a password reset link.")
    fun resetPasswordSent(email: String) =
        t("$email -এ একটি রিসেট লিংক পাঠানো হয়েছে (যদি এই ইমেইলে অ্যাকাউন্ট থাকে)। ইনবক্স চেক করুন।",
            "A reset link has been sent to $email (if an account exists). Check your inbox.")
    fun send() = t("পাঠান", "Send")

    // Sign up
    fun signUpTitle() = t("নতুন অ্যাকাউন্ট তৈরি করুন", "Create a new account")
    fun yourName() = t("আপনার নাম", "Your name")
    fun passwordMinChars() = t("পাসওয়ার্ড (কমপক্ষে ৬ ক্যারেক্টার)", "Password (at least 6 characters)")
    fun signUpButton() = t("সাইন আপ", "Sign up")
    fun haveAccountPrefix() = t("আগে থেকেই অ্যাকাউন্ট আছে? ", "Already have an account? ")
    fun loginLink() = t("লগইন করুন", "Log in")

    // Attendance
    fun attendanceHeader() = t("Attendance", "Attendance")
    fun attendanceSubtitleMember() = t("দৈনিক কাজের জন্য Check in & out করুন", "Check in & out for your daily work")
    fun attendanceSubtitleAdmin() = t("টিমের Check-in & Check-out", "Team check-in & check-out")
    fun checkInDone() = t("আজ আপনার check-in সম্পন্ন হয়েছে ✅", "Your check-in for today is done ✅")
    fun checkInNow() = t("এখন Check-in করুন", "Check in now")
    fun checkOutNow() = t("এখন Check-out করুন", "Check out now")
    fun checkOutDone() = t("আজ আপনার check-out সম্পন্ন হয়েছে ✅", "Your check-out for today is done ✅")
    fun tapToCheckInOut() = t("Check In / Out করতে ট্যাপ করুন", "Tap to Check In / Out")
    fun tapToCheckInOutHint() = t("আপনার লোকেশন স্বয়ংক্রিয়ভাবে সংরক্ষিত হবে", "Your location will be captured automatically")
    fun onlineLabel() = t("অনলাইন", "Online")
    fun teamCheckedInToday(count: Int) = t("আজকে টিমে যারা Check-in করেছে ($count)", "Checked in today ($count)")

    // Attendance — user-view summary row + activity list (redesigned screen)
    fun lastCheckInLabel() = t("সর্বশেষ Check In", "Last Check In")
    fun locationSummaryLabel() = t("লোকেশন", "Location")
    fun statusSummaryLabel() = t("স্ট্যাটাস", "Status")
    fun notYet() = t("এখনো হয়নি", "Not yet")
    fun statusCheckedIn() = t("Checked In", "Checked In")
    fun statusCheckedOut() = t("Checked Out", "Checked Out")
    fun statusNotCheckedIn() = t("Not Checked In", "Not Checked In")
    fun attendanceBannerTitle() = t("আপনার Attendance গুরুত্বপূর্ণ", "Your attendance is important")
    fun attendanceBannerSubtitle() = t("নিয়মিত থাকুন। সাফল্য তৈরি করুন।", "Stay consistent. Build your success.")
    fun todaysActivityHeader() = t("আজকের কার্যক্রম", "Today's Activity")
    fun viewAll() = t("সব দেখুন", "View All")
    fun checkInActivityLabel() = t("Check-in", "Check-in")
    fun checkOutActivityLabel() = t("Check-out", "Check-out")

    // Attendance — admin-view stats + search/filter (redesigned screen)
    fun adminBadge() = t("Admin", "Admin")
    fun statTotalTeamLabel() = t("মোট টিম", "Total Team")
    fun statActiveInline(count: Int) = t("সক্রিয়: $count", "Active: $count")
    fun statCheckedInAdminLabel() = t("Checked In", "Checked In")
    fun statNotCheckedInLabel() = t("Not Checked In", "Not Checked In")
    fun statTodaysVisitsLabel() = t("আজকের ভিজিট", "Today's Visits")
    fun percentOfTotal(percent: Int) = t("$percent%", "$percent%")
    fun searchMembersPlaceholder() = t("নাম, মোবাইল বা লোকেশন দিয়ে খুঁজুন...", "Search by name, mobile or location...")
    fun filterAllCount(count: Int) = t("সব ($count)", "All ($count)")
    fun filterCheckedInCount(count: Int) = t("Checked In ($count)", "Checked In ($count)")
    fun filterNotCheckedInCount(count: Int) = t("Not Checked In ($count)", "Not Checked In ($count)")
    fun checkInTimeInline(time: String) = t("In: $time", "In: $time")
    fun checkOutTimeInline(time: String) = t("Out: $time", "Out: $time")
    fun noMembersFound() = t("কোনো সদস্য পাওয়া যায়নি।", "No members found.")
    fun myStatusHeader() = t("আমার Attendance", "My Attendance")

    // Location capture (shared by attendance + visit entry)
    fun currentLocationLabel() = t("বর্তমান লোকেশন (GPS)", "Current location (GPS)")
    fun locating() = t("লোকেশন খোঁজা হচ্ছে...", "Getting location...")
    fun locationNotFound() = t("লোকেশন পাওয়া যায়নি", "Location not available")
    fun locationPermissionError() =
        t("লোকেশন পারমিশন দেওয়া নেই। Settings থেকে App permission এ গিয়ে Location অন করুন।",
            "Location permission not granted. Turn it on from Settings > App permissions.")
    fun locationGpsError() =
        t("GPS লোকেশন পাওয়া যায়নি। ফোনের GPS অন আছে কিনা দেখে আবার চেষ্টা করুন।",
            "Could not get a GPS fix. Check that GPS is on and try again.")

    // Visit entry
    fun visitEntryHeader() = t("Visit Entry", "Visit Entry")
    fun visitEntrySubtitle() = t("আপনার কাস্টমার ভিজিট লগ করুন", "Log your customer visit")
    fun leadName() = t("Customer / দোকানের নাম", "Customer / Shop name")
    fun leadNamePlaceholder() = t("কাস্টমার বা দোকানের নাম দিন", "Enter customer or shop name")
    fun businessType() = t("ব্যবসার ধরন", "Business Type")
    fun businessTypePlaceholder() = t("যেমন: রিটেইলার, হোলসেলার, ডিস্ট্রিবিউটর", "e.g. Retailer, Wholesaler, Distributor")
    fun visitLocationLabel() = t("লোকেশন / অবস্থান", "Location")
    fun visitLocationPlaceholder() = t("এলাকা, দোকানের ঠিকানা লিখুন", "Enter location (e.g. area, shop address)")
    fun contactPerson() = t("যোগাযোগের ব্যক্তি", "Contact person")
    fun phoneNumber() = t("ফোন নম্বর", "Phone number")
    fun visitPurpose() = t("ভিজিটের উদ্দেশ্য", "Visit purpose")
    fun notes() = t("Visit Notes (কী আলোচনা হয়েছে)", "Visit notes (what was discussed)")
    fun notesPlaceholder() = t("ভিজিট নিয়ে কিছু নোট লিখুন...", "Add any notes about the visit...")
    fun nextVisitOptional() = t("পরবর্তী ভিজিট (ঐচ্ছিক)", "Next visit (optional)")
    fun pickDate() = t("তারিখ বেছে নিন", "Pick a date")
    fun reminderHint() = t("এই তারিখে আপনাকে reminder notification পাঠানো হবে", "You'll get a reminder notification on this date")
    fun leadNameRequired() = t("লিড/দোকানের নাম দিন", "Enter a lead/shop name")
    fun saveVisit() = t("Visit Save করুন", "Save Visit Entry")

    // Visit entry — redesigned GPS card
    fun liveLocationBadge() = t("Live Location", "Live Location")
    fun locationCaptured() = t("Location Captured", "Location Captured")
    fun mapViewAction() = t("Map View", "Map View")

    // Reminders
    fun remindersHeaderAdmin() = t("টিমের সব Reminders", "All team reminders")
    fun remindersHeaderMember() = t("পরবর্তী ভিজিট Reminders", "Upcoming visit reminders")
    fun noReminders() =
        t("কোনো upcoming reminder নেই। Visit entry দেওয়ার সময় 'পরবর্তী ভিজিট' তারিখ বেছে নিন।",
            "No upcoming reminders. Pick a 'next visit' date when logging a visit entry.")
    fun nextVisitLabel(date: String) = t("পরবর্তী ভিজিট: $date", "Next visit: $date")

    // Visit history — dashboard-style redesign (stats + filters + search)
    fun visitStatTotal() = t("মোট ভিজিট", "Total Visits")
    fun visitStatTodayInline(count: Int) = t("আজ: $count", "Today: $count")
    fun visitStatTodayLabel() = t("আজকের ভিজিট", "Today's Visits")
    fun visitStatRemindersLabel() = t("Upcoming Reminders", "Upcoming Reminders")
    fun visitStatLocationsLabel() = t("লোকেশন", "Locations")
    fun searchVisitsPlaceholder() = t("নাম, কাস্টমার বা লোকেশন দিয়ে খুঁজুন...", "Search by name, customer or location...")
    fun filterAllVisitsCount(count: Int) = t("সব ($count)", "All ($count)")
    fun filterTodayVisitsCount(count: Int) = t("আজ ($count)", "Today ($count)")
    fun filterWeekVisitsCount(count: Int) = t("এই সপ্তাহ ($count)", "This Week ($count)")
    fun addVisitFabLabel() = t("নতুন Visit", "Add Visit")
    fun reminderChipInline(date: String) = t("Reminder: $date", "Reminder: $date")
    fun noVisitsForFilter() = t("এই ফিল্টারে কোনো ভিজিট পাওয়া যায়নি।", "No visits found for this filter.")

    // Visit history
    fun visitHistoryHeaderAdmin() = t("টিমের সব Visit Entry", "All team visit entries")
    fun visitHistoryHeaderMember() = t("আপনার Visit Entry", "Your visit entries")
    fun noVisits() = t("এখনো কোনো visit entry নেই।", "No visit entries yet.")
    fun visitedOn(date: String) = t("ভিজিট: $date", "Visited: $date")
    fun locationLabel(lat: String, lng: String) = t("লোকেশন: $lat, $lng", "Location: $lat, $lng")
    fun locationMissing() = t("লোকেশন সংরক্ষণ করা হয়নি", "No location saved")
    fun contactPersonLabel(name: String) = t("যোগাযোগ: $name", "Contact: $name")
    fun phoneNumberLabel(phone: String) = t("ফোন: $phone", "Phone: $phone")
    fun visitPurposeLabel(purpose: String) = t("উদ্দেশ্য: $purpose", "Purpose: $purpose")

    // Team setup
    fun teamSetupTitle() = t("টিম সেটআপ করুন", "Set up your team")
    fun teamSetupSubtitle() =
        t("নতুন টিম তৈরি করলে আপনি Admin হবেন, অথবা কোড দিয়ে বিদ্যমান টিমে join করুন",
            "Create a new team to become its Admin, or join an existing one with a code")
    fun createTeamTab() = t("টিম তৈরি করুন", "Create team")
    fun joinTeamTab() = t("টিমে Join করুন", "Join team")
    fun teamNameLabel() = t("টিমের নাম", "Team name")
    fun teamSizeLabel(size: Int, min: Int, max: Int) = t("টিম সাইজ: $size জন ($min–$max)", "Team size: $size people ($min–$max)")
    fun createTeamButton() = t("টিম তৈরি করুন (আমি Admin হবো)", "Create team (I'll be Admin)")
    fun inviteCodeFieldLabel() = t("৬-ডিজিট ইনভাইট কোড", "6-digit invite code")
    fun joinTeamButton() = t("Join করুন (Member হিসেবে)", "Join (as Member)")

    // Team manage
    fun inviteCodeOptional() = t("Invite code (বিকল্প উপায়)", "Invite code (alternate way)")
    fun inviteCodeShareHint() = t("এই কোড শেয়ার করলে যে কেউ 'টিমে Join করুন' দিয়ে নিজে থেকে ঢুকতে পারবে", "Share this code and anyone can join themselves via 'Join team'")
    fun createAccountSectionTitle() = t("নতুন Member এর অ্যাকাউন্ট সরাসরি তৈরি করুন", "Create a new member's account directly")
    fun createAccountSectionHint() =
        t("Admin নিজেই নাম, ইমেইল ও পাসওয়ার্ড দিয়ে অ্যাকাউন্ট বানাবে — মেম্বারকে আলাদা করে Sign up করতে হবে না, সাথে সাথে টিমে যোগ হয়ে যাবে",
            "You (the Admin) set the name, email and password yourself — the member doesn't need to sign up separately, and they join the team instantly")
    fun createAccountButton() = t("অ্যাকাউন্ট তৈরি করুন", "Create account")
    fun addByEmailSectionTitle() = t("ইমেইল দিয়ে সরাসরি Member যোগ করুন", "Add an existing member by email")
    fun addByEmailSectionHint() =
        t("যাকে যোগ করতে চান তাকে আগে অ্যাপে Sign up করে থাকতে হবে (এখনো কোনো টিমে নেই এমন হতে হবে)",
            "The person must have already signed up in the app (and not be on any team yet)")
    fun membersHeader() = t("সদস্যরা", "Members")

    // Toggling helpers
    fun switchToEnglishHint() = t("English এ দেখুন", "View in Bengali")

    // Profile screen
    fun profileTitle() = t("প্রোফাইল", "Profile")
    fun profileCompletePrompt() =
        t("অ্যাকাউন্ট তৈরি হয়েছে! এখন আপনার প্রোফাইলটা ঠিক করে নিন (মোবাইল ও ঠিকানা ঐচ্ছিক)।",
            "Your account is ready! Take a moment to complete your profile (mobile and address are optional).")
    fun profileBasicInfoHeader() = t("বেসিক তথ্য", "Basic info")
    fun mobileNumberOptional() = t("মোবাইল নম্বর (ঐচ্ছিক)", "Mobile number (optional)")
    fun addressOptional() = t("ঠিকানা (ঐচ্ছিক)", "Address (optional)")
    fun saveProfileButton() = t("প্রোফাইল সেভ করুন", "Save profile")
    fun profilePasswordHeader() = t("পাসওয়ার্ড", "Password")
    fun changePasswordButton() = t("পাসওয়ার্ড পরিবর্তন করুন", "Change password")
    fun newPasswordLabel() = t("নতুন পাসওয়ার্ড", "New password")
    fun confirmPasswordLabel() = t("নতুন পাসওয়ার্ড আবার দিন", "Confirm new password")
    fun updatePasswordButton() = t("পাসওয়ার্ড আপডেট করুন", "Update password")
    fun profileTeamHeader() = t("টিম", "Team")
    fun profileNoTeamYet() = t("আপনি এখনো কোনো টিমে নেই।", "You're not on a team yet.")
    fun profileSoloModeActive() =
        t("আপনি একা (individually) অ্যাপ ব্যবহার করছেন। চাইলে যেকোনো সময় একটি টিম তৈরি বা join করতে পারেন।",
            "You're using the app individually. You can create or join a team anytime.")
    fun profileCreateOrJoinTeamButton() = t("টিম তৈরি বা Join করুন", "Create or join a team")
    fun profileUseIndividuallyButton() = t("একা ব্যবহার করুন (Single use)", "Use individually (single use)")
    fun skipForNow() = t("এখন থাক (Skip)", "Skip for now")
    fun saveAndContinue() = t("সেভ করে এগিয়ে যান", "Save and continue")
    fun back() = t("ফিরে যান", "Back")

    // Team setup extras
    fun companyNameOptional() = t("কোম্পানির নাম (ঐচ্ছিক)", "Company name (optional)")
    fun teamLocationOptional() = t("টিম/অফিস ঠিকানা (ঐচ্ছিক)", "Team/office address (optional)")
    fun useIndividuallyHint() =
        t("টিম না বানিয়ে একাই অ্যাপ ব্যবহার করতে চাইলে নিচে চাপুন",
            "Want to skip teams and use the app on your own? Tap below")

    // Team manage extras
    fun teamLocationInline(location: String) = t("ঠিকানা: $location", "Location: $location")
    fun deactivatedLabel() = t("নিষ্ক্রিয় (Deactivated)", "Deactivated")
    fun deactivateAction() = t("নিষ্ক্রিয় করুন", "Deactivate")
    fun activateAction() = t("সক্রিয় করুন", "Activate")
    fun removeAction() = t("টিম থেকে সরান", "Remove")
    fun deleteAction() = t("ডিলিট", "Delete")
    fun deleteConfirmTitle() = t("মেম্বার ডিলিট করবেন?", "Delete this member?")
    fun deleteConfirmMessage(name: String) =
        t("$name -কে টিম থেকে সরিয়ে তার প্রোফাইল ডিলিট করা হবে। এই কাজ Undo করা যাবে না।",
            "$name will be removed from the team and their profile deleted. This can't be undone.")

    // Dashboard solo-mode tile
    fun soloModeTileTitle() = t("Solo মোডে আছেন", "You're in solo mode")
    fun soloModeTileSubtitle() = t("টিম তৈরি বা Join করতে এখানে চাপুন", "Tap here to create or join a team")

    // Report (replaces the old separate Reminders + Visit History tiles)
    fun reportTileTitle() = t("রিপোর্ট", "Report")
    fun reportTileSubtitleAdmin() = t("দৈনিক/সাপ্তাহিক/মাসিক Attendance, Visit ও Reminder রিপোর্ট", "Daily/weekly/monthly attendance, visit & reminder report")
    fun reportTileSubtitleSelf() = t("আপনার Attendance, Visit ও Reminder রিপোর্ট দেখুন", "See your attendance, visit & reminder report")
    fun reportHeaderAdmin() = t("টিমের রিপোর্ট", "Team report")
    fun reportHeaderSelf() = t("আপনার রিপোর্ট", "Your report")
    fun reportHeaderForMember(name: String) = t("$name -এর রিপোর্ট", "$name's report")
    fun reportPeriodDaily() = t("দৈনিক", "Daily")
    fun reportPeriodWeekly() = t("সাপ্তাহিক", "Weekly")
    fun reportPeriodMonthly() = t("মাসিক", "Monthly")
    fun reportAttendanceLabel() = t("Attendance", "Attendance")
    fun reportVisitLabel() = t("Visit", "Visits")
    fun reportReminderLabel() = t("Reminder", "Reminders")
    fun reportBreakdownHeader() = t("সদস্য অনুযায়ী", "By member")
    fun reportUnknownMember() = t("অজানা সদস্য", "Unknown member")
    fun reportBreakdownCounts(attendance: Int, visits: Int, reminders: Int) =
        t("A:$attendance  V:$visits  R:$reminders", "A:$attendance  V:$visits  R:$reminders")
    fun reportVisitListHeader() = t("এই সময়ে ভিজিট এন্ট্রি", "Visit entries in this period")

    // Report — dashboard-style redesign
    fun reportsSubtitleSelf() = t("আপনার কার্যক্রমের সারসংক্ষেপ", "Overview of your activity")
    fun reportsSubtitleAdmin() = t("টিম পারফরম্যান্সের সারসংক্ষেপ", "Team performance overview")
    fun reportsSubtitleForMember() = t("কার্যক্রমের সারসংক্ষেপ", "Activity overview")
    fun reportStatInline(percent: Int) = t("$percent% উপস্থিতি", "$percent% present")
    fun percentInline(percent: Int) = "$percent%"
    fun reportSummaryHeader() = t("সারসংক্ষেপ", "Summary")
    fun attendanceRateLabel() = t("উপস্থিতি হার", "Attendance rate")
    fun visitCompletionLabel() = t("ভিজিট সম্পন্ন", "Visits completed")
    fun reminderCompletionLabel() = t("রিমাইন্ডার ফলো-আপ", "Reminder follow-up")
    fun targetInline(percent: Int) = t("লক্ষ্য: $percent%", "Target: $percent%")
    fun fractionInline(current: Int, total: Int) = "$current/$total"
    fun monthlyActivityHeader() = t("এই মাসের কার্যক্রম", "This month's activity")
    fun weekRangeLabel(weekNum: Int, start: Int, end: Int) = t("সপ্তাহ $weekNum\n($start-$end)", "Week $weekNum\n($start-$end)")
    fun chartLegendAttendance() = t("Attendance", "Attendance")
    fun chartLegendVisits() = t("Visits", "Visits")
    fun chartLegendReminders() = t("Reminders", "Reminders")
    fun recentActivityHeader() = t("সাম্প্রতিক কার্যক্রম", "Recent activity")
    fun noRecentActivity() = t("কোনো সাম্প্রতিক কার্যক্রম নেই।", "No recent activity yet.")
    fun checkInActivityTitle() = t("Check-in", "Check-in")
    fun visitActivityTitle() = t("Visit Entry", "Visit Entry")
    fun reminderActivityTitle() = t("Reminder", "Reminder")
    fun doneChip() = t("সম্পন্ন", "Done")
    fun totalTeamLabel() = t("মোট টিম", "Total Team")
    fun activeInline(count: Int) = t("সক্রিয়: $count", "Active: $count")
    fun checkedInStatLabel() = t("Checked In", "Checked In")
    fun notCheckedInStatLabel() = t("Not Checked In", "Not Checked In")
    fun topPerformersHeader() = t("শীর্ষ পারফরমার", "Top performers")
    fun checkInFractionInline(current: Int, total: Int) = t("Check-in $current/$total", "Check-in $current/$total")
    fun visitsCountInline(count: Int) = t("Visits $count", "Visits $count")
    fun todayDateLabel(date: String) = date

    // Notifications (bell icon — near-due reminders only, 2 days ahead)
    fun notificationsHeader() = t("Notifications", "Notifications")
    fun notificationsSubtitle() =
        t("যেসব ভিজিটের তারিখ আগামী ২ দিনের মধ্যে, শুধু সেগুলো এখানে দেখাবে", "Only reminders due within the next 2 days show up here")
    fun noNearReminders() =
        t("আগামী ২ দিনের মধ্যে কোনো reminder নেই।", "No reminders due within the next 2 days.")

    // Members section (dashboard, admin-only)
    fun membersTileTitle() = t("টিম মেম্বার", "Team Member")
    fun membersTileSubtitle(count: Int) = t("$count জন সদস্য — ক্লিক করে ফুল রিপোর্ট দেখুন", "$count members — tap one to see their full report")
    fun viewReportHint() = t("ফুল রিপোর্ট দেখতে ট্যাপ করুন", "Tap to view full report")

    // Bottom navigation bar (Home / Attendance / Visit / Reports — always visible,
    // for both Admin and Member, so Attendance is never more than one tap away)
    fun navHome() = t("Home", "Home")
    fun navAttendance() = t("Attendance", "Attendance")
    fun navVisit() = t("Visit", "Visit")
    fun navReports() = t("Reports", "Reports")

    // Dashboard "Keep Going" summary banner + quick stats
    fun keepGoingTitle() = t("Keep Going! 🚀", "Keep Going! 🚀")
    fun keepGoingSubtitle() = t("আপনার টিম আজও এগিয়ে যাচ্ছে।", "Your team is moving forward today.")
    fun keepGoingSubtitleSolo() = t("আজও এগিয়ে যান।", "Keep moving forward today.")
    fun statCheckInLabel() = t("Check-in", "Check-in")
    fun statVisitTodayLabel() = t("Visit Today", "Visit Today")
    fun statTeamMembersLabel() = t("Team Members", "Team Members")
    fun statProductivityLabel() = t("Productivity", "Productivity")
}