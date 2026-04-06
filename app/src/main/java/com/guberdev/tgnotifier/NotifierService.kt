package com.guberdev.tgnotifier

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class NotifierService : Service() {

    companion object {
        var isRunning = false
    }

    private val CHANNEL_ID = "TgNotifierServiceChannel"
    private val MSG_CHANNEL_ID = "TgMessageNotificationChannel"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TgNotifier::BackgroundWakelock")
        wakeLock?.acquire(10*60*1000L /*10 minutes*/) 

        TgClient.newMessageCallback = { chatId, title, username, text ->
            val favs = PreferencesHelper.getFavoriteChats(this)
            if (favs.contains(chatId)) {
                AppLogger.d("NotifierService", "NOTIF sent: $title (id=$chatId)")
                sendLocalNotification(title, text, username, chatId)
            } else {
                AppLogger.d("NotifierService", "SKIP: $title (id=$chatId) not in favorites")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Notifier Running")
            .setContentText("Listening to new messages from selected chats/groups/channels")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    private fun sendLocalNotification(title: String, text: String, username: String, chatId: Long) {
        val intent = if (username.isNotEmpty()) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://resolve?domain=$username"))
        } else {
            val i = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("tg://user?id=$chatId"))
            i.setPackage("org.telegram.messenger")
            i
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, chatId.toInt(), intent, 
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setContentTitle("New message from $title")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        if (manager != null) {
            manager.notify((System.currentTimeMillis() % 10000).toInt(), notif)
        } else {
            AppLogger.e("NotifierService", "NotificationManager unavailable, could not notify for $title")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Background Service", NotificationManager.IMPORTANCE_LOW))
            manager?.createNotificationChannel(NotificationChannel(MSG_CHANNEL_ID, "Message Notifications", NotificationManager.IMPORTANCE_HIGH))
        }
    }
}