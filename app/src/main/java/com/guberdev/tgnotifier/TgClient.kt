package com.guberdev.tgnotifier

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

object TgClient {
    enum class AuthState {
        WAITING_PARAMETERS,
        WAITING_PHONE,
        WAITING_CODE,
        WAITING_PASSWORD,
        READY,
        LOGGED_OUT
    }

    enum class ChatType {
        USER, GROUP, CHANNEL
    }

    data class ChatInfo(val id: Long, var title: String, val type: ChatType, var username: String = "")

    var authStateCallback: ((AuthState) -> Unit)? = null
    var onAuthStateChanged: ((AuthState) -> Unit)? = null  // secondary listener (MainActivity status)
    var newMessageCallback: ((Long, String, String, String) -> Unit)? = null
    var onChatsUpdated: (() -> Unit)? = null  // fires on every UpdateNewChat or UpdateChatTitle

    var currentAuthState: AuthState = AuthState.WAITING_PARAMETERS
    
    private var client: Client? = null
    private var cachedChats: MutableList<ChatInfo> = mutableListOf()
    private var isFetchingChats = false

    private const val TAG = "TgClient"

    fun initialize(dir: String) {
        if (client != null) return
        AppLogger.d(TAG, "Initializing TDLib in $dir")
        
        client = Client.create({ obj -> 
            handleUpdate(obj, dir)
        }, null, null)
    }

    private fun handleUpdate(update: TdApi.Object, dir: String) {
        when (update.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val authState = (update as TdApi.UpdateAuthorizationState).authorizationState
                handleAuthState(authState, dir)
            }
            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val msgUpdate = update as TdApi.UpdateNewMessage
                if (msgUpdate.message.content is TdApi.MessageText) {
                    val text = (msgUpdate.message.content as TdApi.MessageText).text.text
                    val chatId = msgUpdate.message.chatId
                    val chatInfo = cachedChats.find { it.id == chatId }
                    val title = chatInfo?.title ?: "Chat $chatId"
                    val username = chatInfo?.username ?: ""
                    newMessageCallback?.invoke(chatId, title, username, text)
                }
            }
            TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                val titleUpdate = update as TdApi.UpdateChatTitle
                cachedChats = cachedChats.map {
                    if (it.id == titleUpdate.chatId) it.copy(title = titleUpdate.title) else it
                }.toMutableList()
                onChatsUpdated?.invoke()
            }
            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val chatUpdate = update as TdApi.UpdateNewChat
                val chat = chatUpdate.chat
                val type = when (chat.type.constructor) {
                    TdApi.ChatTypePrivate.CONSTRUCTOR -> ChatType.USER
                    TdApi.ChatTypeBasicGroup.CONSTRUCTOR, TdApi.ChatTypeSupergroup.CONSTRUCTOR -> {
                        val isChannel = (chat.type as? TdApi.ChatTypeSupergroup)?.isChannel == true
                        if (isChannel) ChatType.CHANNEL else ChatType.GROUP
                    }
                    else -> ChatType.GROUP
                }
                // Avoid duplicates
                if (cachedChats.none { it.id == chat.id }) {
                    val info = ChatInfo(chat.id, chat.title, type)
                    cachedChats.add(info)
                    onChatsUpdated?.invoke()

                    if (chat.type is TdApi.ChatTypePrivate) {
                        val userId = (chat.type as TdApi.ChatTypePrivate).userId
                        client?.send(TdApi.GetUser(userId)) { userResult ->
                            if (userResult is TdApi.User) {
                                val uname = userResult.usernames?.activeUsernames?.firstOrNull()
                                if (uname != null) info.username = uname
                            }
                        }
                    } else if (chat.type is TdApi.ChatTypeSupergroup) {
                        val sgId = (chat.type as TdApi.ChatTypeSupergroup).supergroupId
                        client?.send(TdApi.GetSupergroup(sgId)) { sgResult ->
                            if (sgResult is TdApi.Supergroup) {
                                val uname = sgResult.usernames?.activeUsernames?.firstOrNull()
                                if (uname != null) info.username = uname
                            }
                        }
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState, dir: String) {
        when (state.constructor) {
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val setParameters = TdApi.SetTdlibParameters(
                    false, // useTestDc
                    dir,   // databaseDirectory
                    dir,   // filesDirectory
                    null,  // databaseEncryptionKey
                    true,  // useFileDatabase
                    true,  // useChatInfoDatabase
                    true,  // useMessageDatabase
                    true,  // useSecretChats
                    94575, // apiId
                    "a3406de8d171bb422bb6ddf3bbd800e2", // apiHash
                    "en",  // systemLanguageCode
                    "Android", // deviceModel
                    "13.0", // systemVersion
                    "1.0" // applicationVersion
                )
                client?.send(setParameters) { }
                currentAuthState = AuthState.WAITING_PARAMETERS
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                currentAuthState = AuthState.WAITING_PHONE
                authStateCallback?.invoke(currentAuthState)
                onAuthStateChanged?.invoke(currentAuthState)
            }
            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                currentAuthState = AuthState.WAITING_CODE
                authStateCallback?.invoke(currentAuthState)
                onAuthStateChanged?.invoke(currentAuthState)
            }
            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                currentAuthState = AuthState.WAITING_PASSWORD
                authStateCallback?.invoke(currentAuthState)
                onAuthStateChanged?.invoke(currentAuthState)
            }
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                currentAuthState = AuthState.READY
                authStateCallback?.invoke(currentAuthState)
                onAuthStateChanged?.invoke(currentAuthState)
                AppLogger.d(TAG, "TDLib is authorized and ready!")
                fetchRemoteChats()
            }
            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                currentAuthState = AuthState.LOGGED_OUT
                authStateCallback?.invoke(currentAuthState)
                onAuthStateChanged?.invoke(currentAuthState)
            }
        }
    }

    fun sendPhoneNumber(phone: String) {
        AppLogger.d(TAG, "Sending phone number: $phone")
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { result ->
            if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                AppLogger.e(TAG, "Error sending phone: ${(result as TdApi.Error).message}")
            }
        }
    }

    fun sendCode(code: String) {
        AppLogger.d(TAG, "Sending code")
        client?.send(TdApi.CheckAuthenticationCode(code)) { result ->
            if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                AppLogger.e(TAG, "Error verifying code: ${(result as TdApi.Error).message}")
            }
        }
    }

    fun sendPassword(password: String) {
        AppLogger.d(TAG, "Sending password")
        client?.send(TdApi.CheckAuthenticationPassword(password)) { result ->
            if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                AppLogger.e(TAG, "Error verifying password: ${(result as TdApi.Error).message}")
            }
        }
    }

    fun fetchRemoteChats() {
        if (isFetchingChats) return
        isFetchingChats = true
        AppLogger.d(TAG, "Fetching remote chats from TDLib (Main & Archive)")

        // Load in two batches of 100 — TDLib will send UpdateNewChat for each,
        // triggering onChatsUpdated in real time. Requesting 1000 at once causes
        // hundreds of sequential network round-trips and takes minutes.
        client?.send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) { }
        client?.send(TdApi.LoadChats(TdApi.ChatListArchive(), 100)) { }

        Thread {
            Thread.sleep(500)
            isFetchingChats = false
        }.start()
    }

    fun logOut() {
        client?.send(TdApi.LogOut()) { }
        client = null
        cachedChats.clear()
        currentAuthState = AuthState.WAITING_PARAMETERS
    }

    fun searchRemote(query: String) {
        if (query.length < 2) return
        client?.send(TdApi.SearchChatsOnServer(query, 50)) { result ->
            if (result is TdApi.Chats) {
                result.chatIds.forEach { client?.send(TdApi.GetChat(it)) {} }
            }
        }
        client?.send(TdApi.SearchContacts(query, 50)) { result ->
            if (result is TdApi.Users) {
                result.userIds.forEach { client?.send(TdApi.CreatePrivateChat(it, false)) {} }
            }
        }
    }

    fun getChats(limit: Int, callback: (List<ChatInfo>) -> Unit) {
        AppLogger.d(TAG, "Returning chats limit $limit")
        if (cachedChats.isEmpty()) {
            fetchRemoteChats() // Force fetch if empty
        }
        callback(cachedChats.toList())
    }
}
