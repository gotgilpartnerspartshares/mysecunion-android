package com.mysecunion.app.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.mysecunion.app.BuildConfig
import com.mysecunion.app.MainActivity
import com.mysecunion.app.R
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FR-301~309: receives admin-sent FCM pushes, routes them into one of three
 * channels, dedupes by msg_id, and deep-links into MainActivity on tap.
 * Payload shape: see SRS Appendix C.
 */
class PushMessagingService : FirebaseMessagingService() {

    companion object {
        // FR-302: debug/release get separate topics so test pushes never reach production users.
        val NOTICE_TOPIC: String = if (BuildConfig.DEBUG) "notice_debug" else "notice"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // No app server to notify (CON-01) — topics alone drive delivery. Still (re)subscribe
        // here per Firebase's own guidance ("onNewToken ... is where you should complete any
        // initialization-related tasks"); MainActivity.ensureNoticeTopicSubscription() covers
        // the normal launch path with a success/retry guarantee, this just covers token rotation.
        FirebaseMessaging.getInstance().subscribeToTopic(NOTICE_TOPIC)
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
            // Only takes effect on API <26 — on 26+ sound/vibration are channel properties
            // (set on the "notice" NotificationChannel below), but this keeps behavior
            // correct if minSdk is ever lowered.
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun ensureChannels(notificationManager: NotificationManager) {
        // 조합공지 (notice): HIGH importance with sound+vibration — the channel spec that
        // actually governs alert behavior on API 26+ (Builder.setDefaults above is the <26 fallback).
        val noticeChannel = NotificationChannel(
            getString(R.string.channel_notice), "조합공지", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }
        val emergencyChannel = NotificationChannel(
            getString(R.string.channel_emergency), "긴급공지", NotificationManager.IMPORTANCE_HIGH
        ).apply { enableVibration(true) }
        val generalChannel = NotificationChannel(
            getString(R.string.channel_general), "일반", NotificationManager.IMPORTANCE_LOW
        )

        notificationManager.createNotificationChannel(noticeChannel)
        notificationManager.createNotificationChannel(emergencyChannel)
        notificationManager.createNotificationChannel(generalChannel)
    }
}
