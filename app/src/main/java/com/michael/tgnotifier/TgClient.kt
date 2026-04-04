package com.michael.tgnotifier

import android.util.Log
// import org.drinkless.td.libcore.telegram.Client
// import org.drinkless.td.libcore.telegram.TdApi

object TgClient {
    enum class AuthState {
        WAITING_PARAMETERS,
        WAITING_PHONE,
        WAITING_CODE,
        READY,
        LOGGED_OUT
    }

    enum class ChatType {
        USER, GROUP, CHANNEL
    }

    var authStateCallback: ((AuthState) -> Unit)? = null
    var newMessageCallback: ((Long, String) -> Unit)? = null
    var currentAuthState: AuthState = AuthState.WAITING_PHONE

    fun initialize(dir: String) {
        Log.d("TgClient", "Initializing TDLib in $dir")
    }

    fun sendPhoneNumber(phone: String) {
        Log.d("TgClient", "Sending phone number: $phone")
        currentAuthState = AuthState.WAITING_CODE
        authStateCallback?.invoke(currentAuthState)
    }

    fun sendCode(code: String) {
        Log.d("TgClient", "Sending code: $code")
        currentAuthState = AuthState.READY
        authStateCallback?.invoke(currentAuthState)
    }

    fun getChats(limit: Int, callback: (List<Triple<Long, String, ChatType>>) -> Unit) {
        Log.d("TgClient", "Fetching chats...")
        // В реальном TDLib тут запрашивается список чатов (GetChats), и затем проверяется
        // тип каждого чата через TdApi.ChatType (ChatTypeSupergroup, ChatTypePrivate и т.д.)
        val simulatedChats = listOf(
            Triple(100L, "John Doe", ChatType.USER),
            Triple(101L, "Alice Model", ChatType.USER),
            Triple(200L, "Family Group", ChatType.GROUP),
            Triple(201L, "Work Chat", ChatType.GROUP),
            Triple(300L, "Durov's Channel", ChatType.CHANNEL),
            Triple(301L, "Tech News", ChatType.CHANNEL)
        )
        callback(simulatedChats)
    }
}
