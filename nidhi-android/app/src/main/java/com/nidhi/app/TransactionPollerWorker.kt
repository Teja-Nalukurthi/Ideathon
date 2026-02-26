package com.nidhi.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic WorkManager task that polls /bank/account/transactions and fires
 * a local notification when a new credit arrives.
 *
 * This is the fallback mechanism when FCM push is not configured.
 * Minimum interval: 15 minutes (Android WorkManager constraint).
 */
class TransactionPollerWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME   = "nidhi_tx_poller"
        private const val PREFS      = "nidhi_poller"
        private const val KEY_LAST   = "last_seen_ref"
        private const val CHANNEL_ID = "nidhi_notifications"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val session = SessionManager.get(context) ?: return@withContext Result.success()
            val acct = session.accountNumber
            if (acct.isBlank()) return@withContext Result.success()

            val txs = NidhiClient.api(context).getTransactions(acct)
            if (txs.isEmpty()) return@withContext Result.success()

            val prefs   = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastRef = prefs.getString(KEY_LAST, null)
            val latestRef = txs.first().referenceId ?: return@withContext Result.success()

            // First run — just remember the current top, no notification
            if (lastRef == null) {
                prefs.edit().putString(KEY_LAST, latestRef).apply()
                return@withContext Result.success()
            }

            if (lastRef == latestRef) return@withContext Result.success()  // nothing new

            // Collect all transactions newer than lastRef that are credits to this account
            val newCredits = txs
                .takeWhile { it.referenceId != lastRef }
                .filter { it.status == "SUCCESS" }
                .filter { it.toAccount == acct || it.txType == "ADMIN_CREDIT" }

            // Update stored ref regardless
            prefs.edit().putString(KEY_LAST, latestRef).apply()

            if (newCredits.isEmpty()) return@withContext Result.success()

            val totalPaise = newCredits.sumOf { it.amountPaise }
            val body = if (newCredits.size == 1)
                "₹${totalPaise / 100} credited to your account"
            else
                "${newCredits.size} credits totalling ₹${totalPaise / 100} received"

            showNotification(body)
        } catch (_: Exception) {
            // Polling is best-effort — swallow errors silently
        }
        Result.success()
    }

    private fun showNotification(body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nidhi Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Nidhi Bank transaction alerts" }
            )
        }

        val intent = Intent(context, HomeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("💰 Money Credited — Nidhi Bank")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
