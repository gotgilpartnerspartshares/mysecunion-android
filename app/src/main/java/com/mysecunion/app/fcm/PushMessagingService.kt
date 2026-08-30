package com.mysecunion.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.mysecunion.app.MainActivity
import com.mysecunion.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FR-301~309: receives admin-sent FCM pushes (topic "notice"), routes them into
 * one of three channels, dedupes by msg_id, and deep-links into MainActivity
 * on tap. Payload shape: see SRS Appendix C.
 */
class PushMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // No app server to notify (CON-01) — topics alone drive delivery.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val msgId = message.data["msg_id"]
        if (msgId != null && wasAlreadyShown(msgId)) return // FR-308

        val title = message.notification?.title ?: message.data["title"] ?: getString(R.string.app_name)
        val body = message.notification?.body ?: message.data["body"] ?: ""
        val category = message.data["category"] ?: "general"
        val deepLinkUrl = message.data["url"]

        showNotification(title, body, category, deepLinkUrl)
        msgId?.let { markShown(it) }
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences("push_dedupe", Context.MODE_PRIVATE)

    private fun wasAlreadyShown(msgId: String): Boolean = prefs().contains(msgId)

    private fun markShown(msgId: String) {
        prefs().edit {
            putLong(msgId, System.currentTimeMillis())
            // keep the pref file small: SharedPreferences has no easy LRU trim,
            // so this cap is best-effort and just prevents unbounded growth.
            if (prefs().all.size > 200) clear()
        }
    }

    private fun channelIdFor(category: String): String = when (category) {
        "emergency" -> getString(R.string.channel_emergency)
        "notice" -> getString(R.string.channel_notice)
        else -> getString(R.string.channel_general)
    }

    private fun showNotification(title: String, body: String, category: String, deepLinkUrl: String?) {
        val channelId = channelIdFor(category)
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureChannels(notificationManager)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            deepLinkUrl?.let { putExtra(MainActivity.EXTRA_DEEP_LINK_URL, it) } // FR-304
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val priority = if (category == "emergency") {
            NotificationCompat.PRIORITY_MAX
        } else {
            NotificationCompat.PRIORITY_DEFAULT
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)) // FR-703: full text visible even if site is down
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(priority)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannels(notificationManager: NotificationManager) {
        val channels = listOf(
            Triple(getString(R.string.channel_notice), "조합공지", NotificationManager.IMPORTANCE_DEFAULT),
            Triple(getString(R.string.channel_emergency), "긴급공지", NotificationManager.IMPORTANCE_HIGH),
            Triple(getString(R.string.channel_general), "일반", NotificationManager.IMPORTANCE_LOW),
        )
        channels.forEach { (id, name, importance) ->
            notificationManager.createNotificationChannel(NotificationChannel(id, name, importance))
        }
    }
}
