package com.guberdev.tgnotifier

import android.content.res.ColorStateList
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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout

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

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val updateTask = object : Runnable {
            var checks = 0
            override fun run() {
                TgClient.getChats(1000) { chats ->
                    runOnUiThread {
                        allChats = chats
                        updateListForTab(tabLayout.selectedTabPosition)
                    }
                }
                checks++
                if (checks < 10) {
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(updateTask)

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
