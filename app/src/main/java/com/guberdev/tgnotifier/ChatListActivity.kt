package com.guberdev.tgnotifier

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout
import org.json.JSONArray
import org.json.JSONObject

class ChatListActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var tabLayout: TabLayout
    private lateinit var editSearch: EditText
    private lateinit var checkShowEnabled: CheckBox
    private var allChats = listOf<TgClient.ChatInfo>()
    private lateinit var favChats: MutableSet<Long>
    private lateinit var hiddenChats: MutableSet<Long>
    private var currentChatsToDisplay = listOf<TgClient.ChatInfo>()
    private var listAdapter: ChatAdapter? = null

    private val AVATAR_COLORS = listOf(
        0xFF2AABEE.toInt(), 0xFFE91E63.toInt(), 0xFF9C27B0.toInt(),
        0xFF4CAF50.toInt(), 0xFFFF9800.toInt(), 0xFF00BCD4.toInt(),
        0xFFFF5722.toInt(), 0xFF607D8B.toInt()
    )

    // ── Export: write JSON to a user-chosen file ──────────────────────────────
    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val arr = JSONArray()
            favChats.forEach { id ->
                val chat = allChats.find { it.id == id }
                val obj = JSONObject()
                obj.put("id", id)
                obj.put("title", chat?.title ?: "")
                arr.put(obj)
            }
            contentResolver.openOutputStream(uri)?.use { it.write(arr.toString(2).toByteArray()) }
            Toast.makeText(this, "Exported ${favChats.size} chats", Toast.LENGTH_SHORT).show()
            AppLogger.d("ChatList", "Exported ${favChats.size} enabled chats")
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            AppLogger.e("ChatList", "Export failed: ${e.message}")
        }
    }

    // ── Import: read JSON from a user-chosen file ─────────────────────────────
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: return@registerForActivityResult
            val arr = JSONArray(json)
            val ids = mutableSetOf<Long>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                ids.add(obj.getLong("id"))
            }
            PreferencesHelper.setFavoriteChats(this, ids)
            favChats.clear()
            favChats.addAll(ids)
            updateListForTab(tabLayout.selectedTabPosition)
            Toast.makeText(this, "Imported ${ids.size} chats", Toast.LENGTH_SHORT).show()
            AppLogger.d("ChatList", "Imported ${ids.size} enabled chats")
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            AppLogger.e("ChatList", "Import failed: ${e.message}")
        }
    }

    private val photoCache = HashMap<Long, Bitmap?>()

    private fun toCircleBitmap(path: String): Bitmap? {
        val src = BitmapFactory.decodeFile(path) ?: return null
        val size = minOf(src.width, src.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        val x = (src.width - size) / 2
        val y = (src.height - size) / 2
        canvas.drawBitmap(src, (-x).toFloat(), (-y).toFloat(), paint)
        src.recycle()
        return output
    }

    inner class ChatAdapter(items: List<TgClient.ChatInfo>) :
        ArrayAdapter<TgClient.ChatInfo>(this@ChatListActivity, R.layout.item_chat, items.toMutableList()) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false)
            val chat = getItem(position) ?: return view

            val avatarText = view.findViewById<TextView>(R.id.avatarText)
            val avatarImage = view.findViewById<ImageView>(R.id.avatarImage)
            val chatName = view.findViewById<TextView>(R.id.chatName)
            val chatUsername = view.findViewById<TextView>(R.id.chatUsername)
            val chatBadge = view.findViewById<TextView>(R.id.chatBadge)

            val initial = chat.title.firstOrNull()?.uppercaseChar() ?: '?'
            val colorIdx = initial.code % AVATAR_COLORS.size
            avatarText.text = initial.toString()
            avatarText.backgroundTintList = ColorStateList.valueOf(AVATAR_COLORS[colorIdx])

            // Show photo if available, otherwise fall back to letter
            val path = chat.photoPath
            if (path != null) {
                view.tag = chat.id
                val cached = photoCache[chat.id]
                if (cached != null) {
                    avatarImage.setImageBitmap(cached)
                    avatarImage.visibility = View.VISIBLE
                } else if (!photoCache.containsKey(chat.id)) {
                    photoCache[chat.id] = null // mark as loading
                    android.os.AsyncTask.execute {
                        val bmp = toCircleBitmap(path)
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            photoCache[chat.id] = bmp
                            if (view.tag == chat.id) {
                                if (bmp != null) {
                                    avatarImage.setImageBitmap(bmp)
                                    avatarImage.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }
            } else {
                avatarImage.visibility = View.GONE
                avatarImage.setImageBitmap(null)
            }

            chatName.text = chat.title
            if (chat.username.isNotEmpty()) {
                chatUsername.text = "@${chat.username}"
                chatUsername.visibility = View.VISIBLE
            } else {
                chatUsername.visibility = View.GONE
            }

            val isOn = favChats.contains(chat.id)
            if (isOn) {
                val dir = PreferencesHelper.effectiveDirection(context, chat.id)
                val (label, color) = when (dir) {
                    PreferencesHelper.Direction.INCOMING -> "IN"  to 0xFF27AE60.toInt()
                    PreferencesHelper.Direction.OUTGOING -> "OUT" to 0xFF2980B9.toInt()
                    PreferencesHelper.Direction.BOTH     -> "↔"   to 0xFF8E44AD.toInt()
                    PreferencesHelper.Direction.NONE     -> "MUTE" to 0xFF95A5A6.toInt()
                }
                chatBadge.text = label
                chatBadge.setTextColor(color)
                chatBadge.setBackgroundResource(R.drawable.bg_badge_on)
            } else {
                chatBadge.text = "OFF"
                chatBadge.setTextColor(0xFF6B7A8D.toInt())
                chatBadge.setBackgroundResource(R.drawable.bg_badge_off)
            }

            return view
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        listView = findViewById(R.id.listViewChats)
        tabLayout = findViewById(R.id.tabLayoutChats)
        editSearch = findViewById(R.id.editSearch)
        checkShowEnabled = findViewById(R.id.checkShowEnabled)
        favChats = PreferencesHelper.getFavoriteChats(this).toMutableSet()
        hiddenChats = PreferencesHelper.getHiddenChats(this).toMutableSet()

        // ── Refresh button — quick or full refresh ────────────────────────────
        findViewById<TextView>(R.id.btnRefreshChats).setOnClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Refresh")
                .setMessage("Quick refresh (show cached) or full reload (clear cache and fetch all from scratch)?")
                .setPositiveButton("Full reload") { _, _ ->
                    AppLogger.d("ChatList", "Full refresh triggered")
                    TgClient.fullRefresh()
                    Toast.makeText(this, "Full reload started...", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Quick") { _, _ ->
                    AppLogger.d("ChatList", "Quick refresh triggered")
                    TgClient.getChats(1000) { chats ->
                        runOnUiThread {
                            allChats = chats
                            updateListForTab(tabLayout.selectedTabPosition)
                            Toast.makeText(this, "Refreshed (${chats.size} chats)", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .show()
        }

        // ── More options (⋮) popup menu ───────────────────────────────────────
        findViewById<TextView>(R.id.btnMoreOptions).setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menu.add(0, 1, 0, "Export enabled chats")
            popup.menu.add(0, 2, 1, "Import enabled chats")
            popup.menu.add(0, 3, 2, "Global direction settings")
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> exportLauncher.launch("tg_enabled_chats.json")
                    2 -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    3 -> showGlobalDirectionDialog()
                }
                true
            }
            popup.show()
        }

        editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                TgClient.searchRemote(query)
                updateListForTab(tabLayout.selectedTabPosition)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        checkShowEnabled.setOnCheckedChangeListener { _, _ ->
            updateListForTab(tabLayout.selectedTabPosition)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                listAdapter = null
                updateListForTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Real-time listener with debounce — UpdateNewChat fires for every single chat,
        // so we batch updates: only refresh UI at most once per 200ms
        val debounceHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val debounceRunnable = Runnable {
            TgClient.getChats(1000) { chats ->
                runOnUiThread {
                    allChats = chats
                    updateListForTab(tabLayout.selectedTabPosition)
                }
            }
        }
        TgClient.onChatsUpdated = {
            debounceHandler.removeCallbacks(debounceRunnable)
            debounceHandler.postDelayed(debounceRunnable, 200)
        }

        // Initial load
        TgClient.getChats(1000) { chats ->
            runOnUiThread {
                allChats = chats
                updateListForTab(tabLayout.selectedTabPosition)
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val chat = currentChatsToDisplay[position]
            if (favChats.contains(chat.id)) {
                favChats.remove(chat.id)
                PreferencesHelper.removeFavoriteChat(this, chat.id)
            } else {
                favChats.add(chat.id)
                PreferencesHelper.addFavoriteChat(this, chat.id)
            }
            updateListForTab(tabLayout.selectedTabPosition)
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            val chat = currentChatsToDisplay[position]
            val options = arrayOf("Direction settings", "Delete / Leave")
            android.app.AlertDialog.Builder(this)
                .setTitle(chat.title)
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> showChatDirectionDialog(chat)
                        1 -> showLeaveDialog(chat)
                    }
                }
                .show()
            return@setOnItemLongClickListener true
        }
    }

    private fun showGlobalDirectionDialog() {
        val dirs = PreferencesHelper.Direction.values()
        val labels = arrayOf("Incoming only", "Outgoing only", "Both directions", "None (mute all)")
        val current = PreferencesHelper.getGlobalDirection(this)
        val checked = dirs.indexOf(current)
        android.app.AlertDialog.Builder(this)
            .setTitle("Global notification direction")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                PreferencesHelper.setGlobalDirection(this, dirs[which])
                updateListForTab(tabLayout.selectedTabPosition)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showChatDirectionDialog(chat: TgClient.ChatInfo) {
        val dirs = arrayOf<PreferencesHelper.Direction?>(null) + PreferencesHelper.Direction.values()
        val global = PreferencesHelper.getGlobalDirection(this)
        val labels = arrayOf(
            "Inherit global (${global.name.lowercase()})",
            "Incoming only",
            "Outgoing only",
            "Both directions",
            "None (mute)"
        )
        val current = PreferencesHelper.getChatDirection(this, chat.id)
        val checked = if (current == null) 0 else PreferencesHelper.Direction.values().indexOf(current) + 1
        android.app.AlertDialog.Builder(this)
            .setTitle("Direction: ${chat.title}")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                PreferencesHelper.setChatDirection(this, chat.id, dirs[which])
                updateListForTab(tabLayout.selectedTabPosition)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLeaveDialog(chat: TgClient.ChatInfo) {
        fun doLeave(blockBot: Boolean) {
            if (favChats.contains(chat.id)) {
                favChats.remove(chat.id)
                PreferencesHelper.removeFavoriteChat(this, chat.id)
            }
            TgClient.leaveChat(chat.id, chat.type, blockBot) { success ->
                runOnUiThread {
                    if (success) {
                        allChats = allChats.filter { it.id != chat.id }
                        updateListForTab(tabLayout.selectedTabPosition)
                        val label = if (blockBot) "Deleted & blocked" else "Removed"
                        Toast.makeText(this, "$label: ${chat.title}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Failed: ${chat.title}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        when (chat.type) {
            TgClient.ChatType.BOT -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Remove bot \"${chat.title}\"?")
                    .setMessage("Cannot be undone.")
                    .setPositiveButton("Delete & Block") { _, _ -> doLeave(true) }
                    .setNeutralButton("Delete") { _, _ -> doLeave(false) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            TgClient.ChatType.GROUP, TgClient.ChatType.CHANNEL -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Leave \"${chat.title}\"?")
                    .setMessage("Cannot be undone.")
                    .setPositiveButton("Leave") { _, _ -> doLeave(false) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            else -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Delete chat \"${chat.title}\"?")
                    .setMessage("Cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> doLeave(false) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TgClient.onChatsUpdated = null
    }

    private fun updateListForTab(tabIndex: Int) {
        val targetType = when (tabIndex) {
            0 -> TgClient.ChatType.USER
            1 -> TgClient.ChatType.GROUP
            2 -> TgClient.ChatType.CHANNEL
            else -> TgClient.ChatType.BOT
        }
        val searchQuery = editSearch.text.toString().lowercase()
        val showEnabledOnly = checkShowEnabled.isChecked

        currentChatsToDisplay = allChats.filter {
            it.type == targetType &&
            it.title.isNotBlank() &&
            !hiddenChats.contains(it.id) &&
            (searchQuery.isEmpty() || it.title.lowercase().contains(searchQuery) || it.username.lowercase().contains(searchQuery)) &&
            (!showEnabledOnly || favChats.contains(it.id))
        }.sortedBy { it.title.lowercase() }

        val firstVisible = listView.firstVisiblePosition
        val offset = listView.getChildAt(0)?.top ?: 0

        if (listAdapter == null) {
            photoCache.clear()
            listAdapter = ChatAdapter(currentChatsToDisplay)
            listView.adapter = listAdapter
        } else {
            listAdapter!!.clear()
            listAdapter!!.addAll(currentChatsToDisplay)
            listView.setSelectionFromTop(firstVisible, offset)
        }

        updateTabCounts(searchQuery, showEnabledOnly)
    }

    private fun updateTabCounts(searchQuery: String, showEnabledOnly: Boolean) {
        val types = listOf(TgClient.ChatType.USER, TgClient.ChatType.GROUP, TgClient.ChatType.CHANNEL, TgClient.ChatType.BOT)
        val labels = listOf("Chats", "Groups", "Channels", "Bots")
        val isFiltered = searchQuery.isNotEmpty() || showEnabledOnly

        for (i in types.indices) {
            val total = allChats.count { it.type == types[i] && !hiddenChats.contains(it.id) }
            val tab = tabLayout.getTabAt(i) ?: continue
            tab.text = if (!isFiltered) {
                "${labels[i]} ($total)"
            } else {
                val current = allChats.count { chat ->
                    chat.type == types[i] &&
                    !hiddenChats.contains(chat.id) &&
                    (searchQuery.isEmpty() || chat.title.lowercase().contains(searchQuery) || chat.username.lowercase().contains(searchQuery)) &&
                    (!showEnabledOnly || favChats.contains(chat.id))
                }
                "${labels[i]} ($current/$total)"
            }
        }
    }
}
