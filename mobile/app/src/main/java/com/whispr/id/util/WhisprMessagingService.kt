package com.whispr.id.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.whispr.id.MainActivity
import com.whispr.id.R
import com.whispr.id.network.ApiClient
import com.whispr.id.network.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging service.
 * Handles push delivery + posts local notifications on the right channel.
 *
 * Data payload contract (from backend):
 *   type      = chat | call | reply | like | link | daily | generic
 *   title     = notification title
 *   body      = notification body
 *   chat_id   = (chat) chat to open
 *   caller_id = (call) peer to answer
 *   post_id   = (reply/like) post to open
 */
class WhisprMessagingService : FirebaseMessagingService() {

    companion object {
        const val CH_CHAT = "whispr_chat"
        const val CH_CALL = "whispr_call"
        const val CH_ENGAGE = "whispr_engagement"

        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val chat = NotificationChannel(CH_CHAT, "Messages", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "New chat messages, voice notes, photos"
                enableVibration(true)
            }
            val call = NotificationChannel(CH_CALL, "Calls", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Incoming voice calls"
                enableVibration(true)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val engage = NotificationChannel(CH_ENGAGE, "Activity", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Replies, likes, links and daily prompts"
            }
            nm.createNotificationChannel(chat)
            nm.createNotificationChannel(call)
            nm.createNotificationChannel(engage)
        }
    }

    override fun onNewToken(token: String) {
        // Register the refreshed token with the backend (best-effort; only if logged in).
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val jwt = TokenStore.getTokenBlocking(applicationContext)
                if (!jwt.isNullOrBlank()) {
                    ApiClient.api.registerFcmToken(mapOf("fcm_token" to token))
                }
            } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ensureChannels(applicationContext)
        val data = message.data
        val type = data["type"] ?: "generic"
        val title = data["title"] ?: message.notification?.title ?: "Whispr"
        val body = data["body"] ?: message.notification?.body ?: ""

        when (type) {
            "call" -> showCallNotification(
                callerId = data["caller_id"] ?: "",
                callId = data["call_id"] ?: "",
                title = title,
                body = body.ifBlank { "Incoming voice call" }
            )
            else -> showMessageNotification(type, title, body, data)
        }
    }

    private fun baseOpenIntent(extras: Map<String, String>): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extras.forEach { (k, v) -> putExtra(k, v) }
        }
        return PendingIntent.getActivity(
            this, extras.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showMessageNotification(type: String, title: String, body: String, data: Map<String, String>) {
        val channel = if (type == "reply" || type == "like" || type == "link" || type == "daily") CH_ENGAGE else CH_CHAT
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notif = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(baseOpenIntent(data))
            .build()
        // Group chat notifications by chat id so they stack instead of spamming
        val notifId = (data["chat_id"] ?: data["post_id"] ?: System.currentTimeMillis().toString()).hashCode()
        nm.notify(notifId, notif)
    }

    private fun showCallNotification(callerId: String, callId: String, title: String, body: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", "call")
            putExtra("caller_id", callerId)
            putExtra("call_id", callId)
        }
        val fullScreenPending = PendingIntent.getActivity(
            this, callId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(this, CH_CALL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentIntent(fullScreenPending)
            .build()
        nm.notify(callId.hashCode(), notif)
    }
}
