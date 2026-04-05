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
6. **Refresh Contacts**: Tap the ↻ button in the chat list to force-fetch updated contacts and chats from Telegram servers.
7. **Import / Export Enabled Chats**: Use the ⋮ menu in the chat list to export your enabled chats to a JSON file, or import a previously exported file. Useful for backup or migrating settings between devices.
8. **Log Viewer**: View and export internal app logs from the main screen. Useful for troubleshooting. Logs are stored in the app's private files directory and can be shared via any app.
9. **Autostart on Boot**: The notification service starts automatically when the device boots.

## Build Information
We use `jitpack.io` to fetch community-maintained TDLib Android wrappers (`com.github.tdlibx:td:1.8.56`). **No manual NDK compilation required!** 

To compile the app in Android Studio / VSCode:
1. Ensure your IDE is synced to Jetpack (via `settings.gradle.kts`).
2. Build via `./gradlew clean :app:assembleDebug`.
(If the Gradle Daemon locks the project output files on Windows, simply run `./gradlew --stop` before compiling again).

## Architecture Structure
* `MainActivity`: First-time setup, requesting POST_NOTIFICATIONS permission and disabling Battery Optimization. Entry point to all other screens.
* `AuthActivity`: UI handling Telegram Login (Phone -> Code -> 2FA Password).
* `ChatListActivity`: Real-time list UI with refresh, import/export. Fetches from local cache + remote queries.
* `LogActivity`: Scrollable log viewer with clear and share functionality.
* `NotifierService`: The core sticky foreground service that routes payloads into ringtones.
* `BootReceiver`: BroadcastReceiver that auto-starts NotifierService on device boot.
* `TgClient`: TDLib singleton facade managing connections, Chat lists, and `UpdateNewChat` dispatches.
* `AppLogger`: File-based logger that writes timestamped entries to `tgnotifier.log` in the app's private files directory.
* `PreferencesHelper`: SharedPreferences helper for persisting enabled chat IDs.

## Version History
- **1.2** — Service auto-starts on every app launch (no manual button); all chats fully loaded on startup; real-time chat list updates
- **1.1** — Refresh button, import/export enabled chats, log viewer, autostart on boot
- **1.0** — Initial release
