package com.example.workpilotmini.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val leadName = inputData.getString(KEY_LEAD_NAME) ?: "Lead"
        val address = inputData.getString(KEY_ADDRESS) ?: ""
        val visitId = inputData.getString(KEY_VISIT_ID) ?: id.toString()

        val message = if (address.isNotBlank()) {
            "$leadName – $address এ আজ আবার visit করার কথা ছিল"
        } else {
            "$leadName এর কাছে আজ আবার visit করার কথা ছিল"
        }

        NotificationHelper.show(
            context = applicationContext,
            notificationId = visitId.hashCode(),
            title = "পরবর্তী ভিজিট রিমাইন্ডার",
            message = message
        )
        return Result.success()
    }

    companion object {
        const val KEY_VISIT_ID = "visit_id"
        const val KEY_LEAD_NAME = "lead_name"
        const val KEY_ADDRESS = "address"
    }
}
