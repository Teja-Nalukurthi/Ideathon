package com.nidhi.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit

/**
 * Maintains a Server-Sent Events connection to /notifications/stream?account=...
 *
 * While the connection is alive, the backend pushes credit notifications
 * instantly the moment a transfer completes — no polling, no Firebase.
 *
 * Usage:
 *   client.connect(context, accountNumber, onMessage = { body -> showSnackbar(body) })
 *   client.disconnect() // in onPause
 */
object NidhiSseClient {

    private const val TAG        = "NidhiSSE"
    private const val CHANNEL_ID = "nidhi_notifications"

    private var eventSource: EventSource? = null

    // OkHttp client with infinite read timeout — SSE needs to stay open indefinitely
    private val http = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * Opens the SSE stream for [account].
     * [onMessage] is called on the background OkHttp thread when a push arrives.
     */
    fun connect(context: Context, account: String, onMessage: (String) -> Unit) {
        disconnect()   // close any existing connection first

        val url = ServerConfig.getUrl(context) + "notifications/stream?account=$account"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .header("Cache-Control", "no-cache")
            .build()

        val listener = object : EventSourceListener() {
            override fun onOpen(eventSource: EventSource, response: Response) {
                Log.i(TAG, "SSE connected — account=$account")
            }

            override fun onEvent(
                eventSource: EventSource,
                id: String?,
                type: String?,
                data: String
            ) {
                if (type == "push" && data.isNotBlank()) {
                    Log.i(TAG, "SSE push received: $data")
                    onMessage(data)
                    showSystemNotification(context, data)
                }
                // heartbeat events with empty type are silently ignored
            }

            override fun onFailure(
                eventSource: EventSource,
                t: Throwable?,
                response: Response?
            ) {
                Log.w(TAG, "SSE failure (will not auto-retry): ${t?.message}")
                // WorkManager polling is the fallback — no aggressive retry here
            }

            override fun onClosed(eventSource: EventSource) {
                Log.i(TAG, "SSE connection closed")
            }
        }

        eventSource = EventSources.createFactory(http).newEventSource(request, listener)
        Log.i(TAG, "SSE connecting to $url")
    }

    fun disconnect() {
        eventSource?.cancel()
        eventSource = null
    }

    private fun showSystemNotification(context: Context, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nidhi Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "Real-time transaction alerts" }
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
            .setContentTitle("Nidhi Bank")
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), notif)
    }
}
