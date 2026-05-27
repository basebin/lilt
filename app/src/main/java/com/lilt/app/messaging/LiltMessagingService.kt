package com.lilt.app.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lilt.app.MainActivity
import com.lilt.app.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.lilt.domain.chat.ChatRepository
import com.lilt.domain.chat.IncomingMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class LiltMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var chatRepository: ChatRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createMessageChannel()
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed")
        // Next step: send this token to the Lilt backend for this signed-in user.
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "Push message received from ${message.from}")
        persistIncomingMessage(message)
        showMessageNotification(message)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun persistIncomingMessage(message: RemoteMessage) {
        val threadId = message.data["threadId"] ?: return
        val body = message.data["body"] ?: message.notification?.body ?: return
        val senderName = message.data["senderName"]
            ?: message.notification?.title
            ?: "Friend"
        val messageId = message.data["messageId"]
            ?: "${threadId}-${message.sentTime.takeIf { it > 0L } ?: System.currentTimeMillis()}"
        val receivedAt = message.sentTime.takeIf { it > 0L } ?: System.currentTimeMillis()

        serviceScope.launch {
            chatRepository.receiveMessage(
                IncomingMessage(
                    id = messageId,
                    threadId = threadId,
                    senderName = senderName,
                    body = body,
                    receivedAtMillis = receivedAt,
                ),
            )
        }
    }

    private fun createMessageChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            MESSAGE_CHANNEL_ID,
            "Messages",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "New Lilt message alerts"
        }

        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun showMessageNotification(message: RemoteMessage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val title = message.notification?.title
            ?: message.data["title"]
            ?: "New message"
        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Open Lilt to read it."
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, MESSAGE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this)
            .notify(System.currentTimeMillis().toInt(), notification)
    }

    private companion object {
        const val TAG = "LiltMessaging"
        const val MESSAGE_CHANNEL_ID = "lilt_messages"
    }
}
