package com.example.workpilotmini.notification

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object ReminderScheduler {

    /**
     * Schedules a local notification to fire at [triggerAtMillis] (the date the user picked
     * as the "next visit" date). If that time is already in the past, it fires almost immediately.
     */
    fun scheduleVisitReminder(
        context: Context,
        visitId: String,
        leadName: String,
        address: String,
        triggerAtMillis: Long
    ) {
        val delay = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)

        val data = Data.Builder()
            .putString(ReminderWorker.KEY_VISIT_ID, visitId)
            .putString(ReminderWorker.KEY_LEAD_NAME, leadName)
            .putString(ReminderWorker.KEY_ADDRESS, address)
            .build()

        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(TAG_PREFIX + visitId)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            TAG_PREFIX + visitId,
            androidx.work.ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReminder(context: Context, visitId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(TAG_PREFIX + visitId)
    }

    private const val TAG_PREFIX = "visit_reminder_"
}
