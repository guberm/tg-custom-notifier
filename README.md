# Telegram Custom Notifier

A custom Android application for receiving local notifications from specific Telegram chats, groups, and channels, even if they are set to **Muted** in the official app.

## How it works
The app works independently of the official Telegram client and does NOT change the mute settings on the telegram servers (official clients will still show the chat as muted). Instead, it uses **TDLib** to passively listen to incoming message updates locally in the background.

When a new message arrives from a chat/channel that you've added to the "Favorites" list in this app, it triggers a loud local notification using the standard Android notification manager.

## Features
1. **Foreground Service**: The daemon runs continuously with a pinned notification and holds a CPU Wakelock to bypass the aggressive Android Doze Mode battery optimizations.
2. **Tab Layout Filtering**: Chats and groups are neatly separated by tabs: 'Chats', 'Groups', and 'Channels'.
3. **Local Mute Bypass**: Does not unmute the chat on the server. Muted stays muted everywhere else.

## Build Requirements (Important!)
Because **TDLib** (the official C++ library by Telegram) requires native `.so` libraries compiled for the specific mobile architectures, you must provide your own compiled `tdlib.aar` (or native files) in the `app/libs` directory before building this project. 
After adding the library, remember to uncomment the TDLib imports in `TgClient.kt`. The core API structure is already set up and ready to consume updates.

## Architecture Structure
* `MainActivity`: The entry point for requesting Notification permissions (Android 13+) and disabling Battery optimizations.
* `AuthActivity`: The UI for logging in via phone number and TDLib confirmation code.
* `ChatListActivity`: The Tab+List interface to selectively add chats to the white-list.
* `NotifierService`: The background service translating silent updates into Loud Local Notifications.
* `TgClient`: A facade wrapper implementing TDLib architecture updates.
