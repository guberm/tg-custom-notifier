package com.guberdev.tgnotifier

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider

class LogActivity : AppCompatActivity() {

    private lateinit var tvLog: TextView
    private lateinit var scrollView: ScrollView
    private lateinit var etFilter: EditText
    private lateinit var btnClearFilter: Button
    private var fullLogText: String = ""

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
        etFilter = findViewById(R.id.etFilter)
        btnClearFilter = findViewById(R.id.btnClearFilter)

        etFilter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                btnClearFilter.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyFilter()
            }
        })

        btnClearFilter.setOnClickListener {
            etFilter.setText("")
        }

        // Quick filter chips
        val chips = mapOf(
            R.id.chipAll   to "",
            R.id.chipMsg   to "MSG",
            R.id.chipNotif to "NOTIF",
            R.id.chipSkip  to "SKIP",
            R.id.chipErr   to "E/"
        )
        chips.forEach { (id, keyword) ->
            findViewById<Button>(id).setOnClickListener { etFilter.setText(keyword) }
        }

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
        fullLogText = AppLogger.readAll()
        applyFilter()
    }

    private fun applyFilter() {
        val query = etFilter.text.toString().trim()
        val displayed = if (query.isEmpty()) {
            fullLogText
        } else {
            fullLogText.lines()
                .filter { it.contains(query, ignoreCase = true) }
                .joinToString("\n")
        }
        tvLog.text = displayed
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
