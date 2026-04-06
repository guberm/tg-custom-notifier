package com.guberdev.tgnotifier

import android.content.Context
import android.content.SharedPreferences

object PreferencesHelper {
    private const val PREFS_NAME = "TgNotifierPrefs"
    private const val KEY_FAVORITE_CHATS = "FavoriteChats"
    private const val KEY_HIDDEN_CHATS = "HiddenChats"
    private const val KEY_GLOBAL_DIR = "GlobalDirection"

    enum class Direction { INCOMING, OUTGOING, BOTH, NONE }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getGlobalDirection(context: Context): Direction {
        val s = getPrefs(context).getString(KEY_GLOBAL_DIR, "INCOMING") ?: "INCOMING"
        return runCatching { Direction.valueOf(s) }.getOrDefault(Direction.INCOMING)
    }

    fun setGlobalDirection(context: Context, dir: Direction) {
        getPrefs(context).edit().putString(KEY_GLOBAL_DIR, dir.name).apply()
    }

    fun getChatDirection(context: Context, chatId: Long): Direction? {
        val s = getPrefs(context).getString("chatDir_$chatId", null) ?: return null
        return runCatching { Direction.valueOf(s) }.getOrNull()
    }

    fun setChatDirection(context: Context, chatId: Long, dir: Direction?) {
        if (dir == null) {
            getPrefs(context).edit().remove("chatDir_$chatId").apply()
        } else {
            getPrefs(context).edit().putString("chatDir_$chatId", dir.name).apply()
        }
    }

    fun effectiveDirection(context: Context, chatId: Long): Direction {
        return getChatDirection(context, chatId) ?: getGlobalDirection(context)
    }

    fun getFavoriteChats(context: Context): Set<Long> {
        val strings = getPrefs(context).getStringSet(KEY_FAVORITE_CHATS, emptySet()) ?: emptySet()
        return strings.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun addFavoriteChat(context: Context, chatId: Long) {
        val current = getFavoriteChats(context).toMutableSet()
        current.add(chatId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_CHATS, current.map { it.toString() }.toSet()).apply()
    }

    fun removeFavoriteChat(context: Context, chatId: Long) {
        val current = getFavoriteChats(context).toMutableSet()
        current.remove(chatId)
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_CHATS, current.map { it.toString() }.toSet()).apply()
    }

    fun setFavoriteChats(context: Context, chatIds: Set<Long>) {
        getPrefs(context).edit().putStringSet(KEY_FAVORITE_CHATS, chatIds.map { it.toString() }.toSet()).apply()
    }

    fun getHiddenChats(context: Context): Set<Long> {
        val strings = getPrefs(context).getStringSet(KEY_HIDDEN_CHATS, emptySet()) ?: emptySet()
        return strings.mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun addHiddenChat(context: Context, chatId: Long) {
        val current = getHiddenChats(context).toMutableSet()
        current.add(chatId)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_CHATS, current.map { it.toString() }.toSet()).apply()
    }

    fun removeHiddenChat(context: Context, chatId: Long) {
        val current = getHiddenChats(context).toMutableSet()
        current.remove(chatId)
        getPrefs(context).edit().putStringSet(KEY_HIDDEN_CHATS, current.map { it.toString() }.toSet()).apply()
    }
}