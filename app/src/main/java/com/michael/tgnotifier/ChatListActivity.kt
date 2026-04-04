package com.michael.tgnotifier

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.EditText
import android.widget.CheckBox
import android.text.Editable
import android.text.TextWatcher
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
                if (checks < 10) { // Update every 1s for 10 seconds to catch all async loading chats
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
        
        val adapterMap = currentChatsToDisplay.map { 
            val subtitle = if (it.username.isNotEmpty()) " (@${it.username})" else ""
            "${it.title}$subtitle " + if (favChats.contains(it.id)) "[ON]" else "[OFF]" 
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, adapterMap)
    }
}
