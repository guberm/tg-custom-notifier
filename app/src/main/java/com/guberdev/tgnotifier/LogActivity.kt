package com.guberdev.tgnotifier

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class LogActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private val autoRefreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val autoRefreshRunnable = object : Runnable {
        override fun run() {
            loadLogs()
            autoRefreshHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        tvLog = findViewById(R.id.tvLog)
        scrollView = findViewById(R.id.scrollLog)

        loadLogs()
        autoRefreshHandler.postDelayed(autoRefreshRunnable, 2000)

        findViewById<Button>(R.id.btnRefreshLog).setOnClickListener { loadLogs() }

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AppLogger.clear()
            loadLogs()
        }

        findViewById<Button>(R.id.btnShareLog).setOnClickListener {
            val file = AppLogger.getFile() ?: return@setOnClickListener
            if (!file.exists()) return@setOnClickListener
            val uri = FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share logs"))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable)
    }

    private fun loadLogs() {
        val text = AppLogger.readAll()
        tvLog.text = text
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
