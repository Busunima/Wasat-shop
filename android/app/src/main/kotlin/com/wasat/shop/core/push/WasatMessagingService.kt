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
import com.wasat.shop.core.db.NotificationDao
import com.wasat.shop.core.db.NotificationEntity
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

/**
 * Приём FCM-сообщений (FR-B10): сохраняет уведомление в центр уведомлений (§11.5,
 * Room) и показывает системное уведомление с title/body. В центр пишем даже если
 * системные уведомления отключены. Тап открывает приложение. Новый токен
 * (onNewToken) не регистрируется немедленно — регистрация привязана к магазину
 * (best-effort при действии покупателя).
 */
@AndroidEntryPoint
class WasatMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var notificationDao: NotificationDao

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: getString(R.string.app_name)
        val body = message.notification?.body ?: return

        // Центр уведомлений (§11.5): сохраняем независимо от системного разрешения.
        runCatching {
            runBlocking {
                notificationDao.insert(
                    NotificationEntity(
                        id = message.messageId ?: UUID.randomUUID().toString(),
                        title = title,
                        body = body,
                        type = message.data["type"],
                        receivedAt = System.currentTimeMillis(),
                        read = false,
                    ),
                )
            }
        }

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
