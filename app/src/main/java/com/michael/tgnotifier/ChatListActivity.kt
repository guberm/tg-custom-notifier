package com.michael.tgnotifier

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayout

class ChatListActivity : AppCompatActivity() {
    private lateinit var listView: ListView
    private lateinit var tabLayout: TabLayout
    private var allChats = listOf<Triple<Long, String, TgClient.ChatType>>()
    private lateinit var favChats: MutableSet<Long>
    private var currentChatsToDisplay = listOf<Triple<Long, String, TgClient.ChatType>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_list)

        listView = findViewById(R.id.listViewChats)
        tabLayout = findViewById(R.id.tabLayoutChats)
        favChats = PreferencesHelper.getFavoriteChats(this).toMutableSet()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                updateListForTab(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        TgClient.getChats(100) { chats ->
            runOnUiThread {
                allChats = chats
                updateListForTab(tabLayout.selectedTabPosition)
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val chat = currentChatsToDisplay[position]
            if (favChats.contains(chat.first)) {
                favChats.remove(chat.first)
                PreferencesHelper.removeFavoriteChat(this, chat.first)
            } else {
                favChats.add(chat.first)
                PreferencesHelper.addFavoriteChat(this, chat.first)
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
        
        currentChatsToDisplay = allChats.filter { it.third == targetType }
        
        val adapterMap = currentChatsToDisplay.map { 
            "${it.second} " + if (favChats.contains(it.first)) "[ON]" else "[OFF]" 
        }
        listView.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, adapterMap)
    }
}
