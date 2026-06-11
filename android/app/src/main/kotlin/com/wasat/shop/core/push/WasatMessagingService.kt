package com.wasat.shop.core.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.wasat.shop.MainActivity
import com.wasat.shop.R

/**
 * Приём FCM-сообщений (FR-B10): показывает системное уведомление с title/body из
 * notification-блока сообщения. Тап открывает приложение. Новый токен (onNewToken)
 * не регистрируется немедленно: регистрация привязана к магазину и выполняется
 * best-effort при действии покупателя (PushTokenRepository, добавление в вишлист).
 */
class WasatMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: return
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        ensureChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            NotificationManagerCompat.from(this)
                .notify(body.hashCode(), notification)
        }
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.push_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "wasat_default"
    }
}
