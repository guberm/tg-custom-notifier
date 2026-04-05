package com.guberdev.tgnotifier

import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)
    private var logFile: File? = null
    private const val MAX_BYTES = 512 * 1024L // 512 KB — trim when exceeded

    fun init(filesDir: File) {
        logFile = File(filesDir, "tgnotifier.log")
    }

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        write("D/$tag: $msg")
    }

    fun e(tag: String, msg: String) {
        Log.e(tag, msg)
        write("E/$tag: $msg")
    }

    private fun write(line: String) {
        val file = logFile ?: return
        try {
            if (file.exists() && file.length() > MAX_BYTES) {
                val content = file.readText()
                file.writeText(content.substring(content.length / 2))
            }
            val ts = dateFormat.format(Date())
            file.appendText("[$ts] $line\n")
        } catch (_: Exception) {}
    }

    fun readAll(): String = logFile?.takeIf { it.exists() }?.readText() ?: "(no logs yet)"

    fun clear() { logFile?.writeText("") }

    fun getFile(): File? = logFile
}
