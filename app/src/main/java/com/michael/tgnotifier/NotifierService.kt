package com.michael.tgnotifier

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

    private val CHANNEL_ID = "TgNotifierServiceChannel"
    private val MSG_CHANNEL_ID = "TgMessageNotificationChannel"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TgNotifier::BackgroundWakelock")
        wakeLock?.acquire(10*60*1000L /*10 minutes*/) 

        TgClient.newMessageCallback = { chatId, text ->
            val favs = PreferencesHelper.getFavoriteChats(this)
            if (favs.contains(chatId)) {
                sendLocalNotification("New message in $chatId", text)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Telegram Notifier Running")
            .setContentText("Listening to messages even when MUTED")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
        return START_STICKY
    }

    private fun sendLocalNotification(title: String, text: String) {
        val notif = NotificationCompat.Builder(this, MSG_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify((System.currentTimeMillis() % 10000).toInt(), notif)
    }

    override fun onDestroy() {
        super.onDestroy()
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