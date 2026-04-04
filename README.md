# Telegram Custom Notifier

A custom Android application for receiving local loud notifications from specific Telegram chats, groups, and channels, even if they are permanently set to **Muted** in your official Telegram client.

## How it Works
The app functions as an independent, headless Telegram client. It utilizes the official **TDLib (Telegram Database Library)** to authenticate and passively listen to incoming message updates in the background. It does **NOT** modify or unmute your chats on the Telegram servers (your official client will still show them as muted without unread badges if configured that way). 

When a new message arrives from any chat, channel, or group that you have toggled `[ON]` in this app, a loud local notification is fired via the standard Android NotificationManager.

## Features
1. **True MTProto Integration**: Powered by TDLib directly. Native JNI connection to Telegram's secure protocol.
2. **2FA Cloud Password Support**: Full implementation of Telegram's two-step verification authentication flow in the UI.
3. **Deep Deep Search**: By default, TDLib only caches active recent messages. This app integrates a powerful search string connecting to `SearchChatsOnServer` and `SearchContacts` allowing you to actively query Telegram servers for any past contact, even if they aren't locally cached.
4. **Smart Filtering UI**: 
   - Tab layout separates `Chats`, `Groups`, and `Channels`.
   - Real-time alphabetical sorting.
   - Dynamic search by Chat Title or `@username`.
   - `Enabled Only` toggle to keep track of your active notification targets.
5. **Foreground Daemon**: Bypasses Android's Doze mode via a persistent foreground service and wake-locks, running seamlessly in the background 24/7.

## Build Information
We use `jitpack.io` to fetch community-maintained TDLib Android wrappers (`com.github.tdlibx:td:1.8.56`). **No manual NDK compilation required!** 

To compile the app in Android Studio / VSCode:
1. Ensure your IDE is synced to Jetpack (via `settings.gradle.kts`).
2. Build via `./gradlew clean :app:assembleDebug`.
(If the Gradle Daemon locks the project output files on Windows, simply run `./gradlew --stop` before compiling again).

## Architecture Structure
* `MainActivity`: First-time setup, requesting POST_NOTIFICATIONS permission and disabling Battery Optimization.
* `AuthActivity`: UI handling Telegram Login (Phone -> Code -> 2FA Password).
* `ChatListActivity`: Real-time list UI fetching from local cache + remote queries.
* `NotifierService`: The core sticky foreground service that routes payloads into ringtones.
* `TgClient`: TDLib singleton facade managing connections, Chat lists, and `UpdateNewChat` dispatches.
