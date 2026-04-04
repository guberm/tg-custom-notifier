import os

base_dir = r"C:\Users\michael.guber\Desktop\my repos\Telegram Notifier"

files = {
    "app/src/main/res/layout/activity_main.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:padding="24dp">

    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
        android:text="TG Multi-Notifier" android:textSize="24sp" android:textStyle="bold" android:layout_marginBottom="32dp"/>

    <Button android:id="@+id/btnAuth" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="1. Authorize" />
    <Button android:id="@+id/btnSetupChats" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="2. Select Chats" />
    <Button android:id="@+id/btnStartService" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="3. Start Background Service" />
    <Button android:id="@+id/btnDisableBatteryOpt" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Disable Battery Sleep (REQUIRED)" android:layout_marginTop="32dp"/>
</LinearLayout>""",

    "app/src/main/res/layout/activity_auth.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp">
    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Authorization" android:textSize="20sp" android:layout_marginBottom="16dp"/>
    <EditText android:id="@+id/editPhone" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Phone number (with code)" android:inputType="phone" />
    <Button android:id="@+id/btnSendPhone" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Send Phone Number" />
    <EditText android:id="@+id/editCode" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Telegram Code" android:inputType="number" android:layout_marginTop="20dp" />
    <Button android:id="@+id/btnSendCode" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="Submit Code" />
</LinearLayout>""",

    "app/src/main/res/layout/activity_chat_list.xml": """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <TextView 
        android:layout_width="wrap_content" 
        android:layout_height="wrap_content" 
        android:text="Select Chats for Notifications" 
        android:textSize="18sp" 
        android:padding="16dp" />

    <com.google.android.material.tabs.TabLayout
        android:id="@+id/tabLayoutChats"
        android:layout_width="match_parent"
        android:layout_height="wrap_content">

        <com.google.android.material.tabs.TabItem
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Chats" />

        <com.google.android.material.tabs.TabItem
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Groups" />

        <com.google.android.material.tabs.TabItem
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Channels" />

    </com.google.android.material.tabs.TabLayout>

    <ListView 
        android:id="@+id/listViewChats" 
        android:layout_width="match_parent" 
        android:layout_height="match_parent" />

</LinearLayout>""",

    "app/src/main/java/com/michael/tgnotifier/MainActivity.kt": """package com.michael.tgnotifier

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()

        TgClient.initialize(filesDir.absolutePath)

        findViewById<Button>(R.id.btnAuth).setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        findViewById<Button>(R.id.btnSetupChats).setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            val serviceIntent = Intent(this, NotifierService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnDisableBatteryOpt).setOnClickListener {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent()
                intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Optimization already disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}""",

    "app/src/main/java/com/michael/tgnotifier/NotifierService.kt": """package com.michael.tgnotifier

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
}""",
    
    "app/src/main/java/com/michael/tgnotifier/AuthActivity.kt": """package com.michael.tgnotifier

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AuthActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)

        val editPhone = findViewById<EditText>(R.id.editPhone)
        val btnSendPhone = findViewById<Button>(R.id.btnSendPhone)
        val editCode = findViewById<EditText>(R.id.editCode)
        val btnSendCode = findViewById<Button>(R.id.btnSendCode)

        TgClient.authStateCallback = { state ->
            runOnUiThread {
                Toast.makeText(this, "Status: $state", Toast.LENGTH_SHORT).show()
                if (state == TgClient.AuthState.READY) {
                    finish()
                }
            }
        }

        btnSendPhone.setOnClickListener {
            val phone = editPhone.text.toString()
            if(phone.isNotEmpty()) TgClient.sendPhoneNumber(phone)
        }

        btnSendCode.setOnClickListener {
            val code = editCode.text.toString()
            if(code.isNotEmpty()) TgClient.sendCode(code)
        }
    }
}"""
}

for rel_path, content in files.items():
    path = os.path.join(base_dir, rel_path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)

print("Translated everything to English!")
