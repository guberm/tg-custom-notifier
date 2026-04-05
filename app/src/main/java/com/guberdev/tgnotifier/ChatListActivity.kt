package com.guberdev.tgnotifier

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
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

    inner class ChatAdapter(items: List<TgClient.ChatInfo>) :
        ArrayAdapter<TgClient.ChatInfo>(this@ChatListActivity, R.layout.item_chat, items.toMutableList()) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_chat, parent, false)
            val chat = getItem(position) ?: return view

            val avatarText = view.findViewById<TextView>(R.id.avatarText)
            val chatName = view.findViewById<TextView>(R.id.chatName)
            val chatUsername = view.findViewById<TextView>(R.id.chatUsername)
            val chatBadge = view.findViewById<TextView>(R.id.chatBadge)

            val initial = chat.title.firstOrNull()?.uppercaseChar() ?: '?'
            val colorIdx = initial.code % AVATAR_COLORS.size
            avatarText.text = initial.toString()
            avatarText.backgroundTintList = ColorStateList.valueOf(AVATAR_COLORS[colorIdx])

            chatName.text = chat.title
            if (chat.username.isNotEmpty()) {
                chatUsername.text = "@${chat.username}"
                chatUsername.visibility = View.VISIBLE
            } else {
                chatUsername.visibility = View.GONE
            }

            val isOn = favChats.contains(chat.id)
            if (isOn) {
                chatBadge.text = "ON"
                chatBadge.setTextColor(0xFF27AE60.toInt())
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
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> exportLauncher.launch("tg_enabled_chats.json")
                    2 -> importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
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
    }

    override fun onDestroy() {
        super.onDestroy()
        TgClient.onChatsUpdated = null
    }

    private fun updateListForTab(tabIndex: Int) {
        val targetType = when (tabIndex) {
            0 -> TgClient.ChatType.USER
            1 -> TgClient.ChatType.GROUP
            else -> TgClient.ChatType.CHANNEL
        }
        val searchQuery = editSearch.text.toString().lowercase()
        val showEnabledOnly = checkShowEnabled.isChecked

        currentChatsToDisplay = allChats.filter {
            it.type == targetType &&
            it.title.isNotBlank() &&
            (searchQuery.isEmpty() || it.title.lowercase().contains(searchQuery) || it.username.lowercase().contains(searchQuery)) &&
            (!showEnabledOnly || favChats.contains(it.id))
        }.sortedBy { it.title.lowercase() }

        val firstVisible = listView.firstVisiblePosition
        val offset = listView.getChildAt(0)?.top ?: 0

        if (listAdapter == null) {
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
        val types = listOf(TgClient.ChatType.USER, TgClient.ChatType.GROUP, TgClient.ChatType.CHANNEL)
        val labels = listOf("Chats", "Groups", "Channels")
        val isFiltered = searchQuery.isNotEmpty() || showEnabledOnly

        for (i in types.indices) {
            val total = allChats.count { it.type == types[i] }
            val tab = tabLayout.getTabAt(i) ?: continue
            tab.text = if (!isFiltered) {
                "${labels[i]} ($total)"
            } else {
                val current = allChats.count { chat ->
                    chat.type == types[i] &&
                    (searchQuery.isEmpty() || chat.title.lowercase().contains(searchQuery) || chat.username.lowercase().contains(searchQuery)) &&
                    (!showEnabledOnly || favChats.contains(chat.id))
                }
                "${labels[i]} ($current/$total)"
            }
        }
    }
}
