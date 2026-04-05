package com.guberdev.tgnotifier

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()

        AppLogger.init(filesDir)
        AppLogger.d("MainActivity", "App started")
        TgClient.initialize(filesDir.absolutePath)

        // Auto-start service on every launch
        val serviceIntent = Intent(this, NotifierService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        findViewById<View>(R.id.btnAuth).setOnClickListener {
            startActivity(Intent(this, AuthActivity::class.java))
        }

        findViewById<View>(R.id.btnSetupChats).setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }

        findViewById<Button>(R.id.btnLogOut).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Log out from Telegram? You will need to re-authorize.")
                .setPositiveButton("Log Out") { _, _ ->
                    TgClient.logOut()
                    updateStatusIndicators()
                    Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogActivity::class.java))
        }

        findViewById<Button>(R.id.btnDisableBatteryOpt).setOnClickListener {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } else {
                Toast.makeText(this, "Battery optimization already disabled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        TgClient.onAuthStateChanged = { runOnUiThread { updateStatusIndicators() } }
        updateStatusIndicators()
    }

    override fun onPause() {
        super.onPause()
        TgClient.onAuthStateChanged = null
    }

    private fun updateStatusIndicators() {
        val tvAuth = findViewById<TextView>(R.id.tvAuthStatus)
        val tvService = findViewById<TextView>(R.id.tvServiceStatus)

        if (TgClient.currentAuthState == TgClient.AuthState.READY) {
            tvAuth.text = "● Authorized"
            tvAuth.setTextColor(ContextCompat.getColor(this, R.color.color_on))
        } else {
            tvAuth.text = "● Not authorized"
            tvAuth.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }

        if (NotifierService.isRunning) {
            tvService.text = "● Service active"
            tvService.setTextColor(ContextCompat.getColor(this, R.color.color_on))
        } else {
            tvService.text = "● Service stopped"
            tvService.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }
    }
}
