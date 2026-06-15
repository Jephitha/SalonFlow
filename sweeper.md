# Sweeper Branch — Feature Documentation

## Overview
The `sweeper` branch adds automatic call-log upload from the main salon management app to Firestore, a companion **Reader** app for viewing call logs and statistics on a tablet, and dark-mode support across both apps.

---

## Main App (`com.salonflow.app`)

### 1. Call Log Sweeper (`SweeperSync.java`)
- Reads the device call log via `ContentResolver` using `CallLog.Calls`
- Tracks the last uploaded call log ID in `SharedPreferences` to upload only new entries (max 100 per sweep)
- Resolves contact names from the device contacts provider
- Categorises call types: `incoming`, `outgoing`, `missed`, `rejected`, `voicemail`
- Blacklist support — the reader device ID (`9bde8680c88a308a`) is hard-coded so its data is never uploaded
- Uploaded on: app first open (`MainActivity.onCreate`), every `onResume`, and via scheduled alarms

### 2. Scheduled Sweeps (`SweeperScheduler.kt` + `SweeperAlarmReceiver.kt`)
- Uses `AlarmManager` with `setExactAndAllowWhileIdle` (API 23+) for reliable wake-up
- **Weekdays**: sweeper fires at **7:30 AM** and **2:30 PM**
- **Saturdays**: the 7:30 AM alarm is replaced by a **2:00 AM** sweep that:
  - Runs the normal upload first
  - Then compares device call logs (last 7 days) against Firestore entries (last 7 days) by matching `"number:timestamp"` keys
  - Marks entries not found on the device as `deleted = true` in Firestore
- The alarm receiver acquires a partial wake lock (5 min timeout) and uses `goAsync()`
- **No notifications** are produced by any sweep

### 3. Offline Resilience
- **Pending sweeps**: if the device is offline when a scheduled sweep fires, a `sweep_pending` flag is set in `SharedPreferences`
- **Connectivity observer**: `SweeperApp` (Application class) registers a `ConnectivityManager.NetworkCallback` that runs a pending sweep when internet is restored
- **Boot recovery**: `SweeperBootReceiver` re-schedules all alarms after `BOOT_COMPLETED` and checks for pending sweeps

### 4. Firestore Integration (`FirestoreManager.java`)
- Singleton with anonymous Firebase Auth
- Call logs are stored at: `callLogs/{deviceId}/entries/{autoId}`
- Device registration with name, ID, and `lastSeen` timestamp
- Data purge for blacklisted devices
- `queryEntriesSince(timestamp)` — queries entries within a time window
- `markDeleted(entryId)` / `markDeletedSync(entryId)` — marks a Firestore entry as `deleted = true` with a `deletedAt` timestamp

### 5. SweeperConfig (`SweeperConfig.java`)
- Device ID derived from SHA-256 of Android ID (first 16 hex chars)
- Device display name from manufacturer + model
- Firestore field constants: `deviceId`, `number`, `name`, `type`, `durationSec`, `timestamp`, `deleted`, `deletedAt`
- Alarm action strings and request codes

### 6. Dark Mode (Main App)
- `AppCompatDelegate.setDefaultNightMode(MODE_NIGHT_FOLLOW_SYSTEM)` — follows system dark mode
- `MainActivity.applyNightColors()` computes `PAPER`, `SURFACE`, `MUTED` based on `uiMode`
- All five Compose content pages (Home, Bookings, Clients, Reports, Settings) use `MaterialTheme.colorScheme.*` instead of hardcoded colours
- Backup/restore buttons explicitly use `contentColor = Color.White`
- Calendar day cells retain original light colours (`0xFFF8FAF8`) even in dark mode; weekend date text switches to white in dark mode
- Notification dialog uses theme-aware `surface` / `surfaceVariant` backgrounds and `onSurface` / `onSurfaceVariant` text colours
- Google Drive sign-in/out buttons always use white text

### 7. Notifications (`NotificationBell.kt`)
- Bell icon with unread badge in the app header
- Dialog with `LazyColumn` displaying notification cards
- Cards use `surfaceVariant` background (theme-aware), text uses `onSurface` / `onSurfaceVariant`
- Colour-coded indicator dots per notification type
- Swipe-to-dismiss gesture to mark as read
- Scrollable list (no artificial item limit)

---

## Reader App (`com.salonflow.reader`)

### 1. Branding
- App name: **Weather Reader**
- Dark blue brand colour: `#192E45` (from logo background)
- Yellow accent: `#FFD524` (from logo sun)
- Icon: weather icon (dark blue sun/cloud)

### 2. PIN Security
- 6-digit PIN with PBKDF2 hashing (device Android ID as salt)
- PIN creation on first launch, PIN entry on subsequent launches
- PIN hash stored in `SharedPreferences`

### 3. Device List
- Loads devices from Firestore (`callLogs` collection)
- Deduplicated by `deviceId` with `distinctBy`
- Shows device name, last activity timestamp
- **Hide** button per device (stored locally in `SharedPreferences`)
- Top-bar labels always white (`Color.White`)

### 4. Call Logs Tab
- Shows all call log entries for a selected device
- **Search** by phone number (dynamic, updates as you type)
- **Deleted filter** toggle — when active, shows only entries marked as `deleted` in Firestore
- **DELETED** label displayed on rows where `deleted == true`
- Long-press any entry to copy the phone number to clipboard (Toast: "Phone number copied to clipboard")
- Colour-coded type icons and duration formatting

### 5. Stats Tab
- **Most Contacted** — sorted by total (incoming + outgoing) call count (top 20)
- **Most Missed / Declined** — sorted by missed + rejected count (top 20)
- Per-number **Exclude / Restore** toggle; excluded numbers are hidden from stats
- Excluded numbers shown in a separate section with name displayed beside number (`Name (number)`)
- Tap an excluded entry to restore it
- Long-press any stats row to copy the phone number to clipboard

### 6. Dark Mode (Reader)
- System dark mode via `isSystemInDarkTheme()`
- Dynamic color scheme: `darkColorScheme` / `lightColorScheme`
- Custom `SurfaceDark`, `InkDark`, `MutedDark` for dark mode

---

## Firestore Schema

### `callLogs/{deviceId}`
```json
{
  "deviceId": "string (16-char hex SHA-256)",
  "deviceName": "string (manufacturer + model)",
  "lastSeen": "timestamp millis"
}
```

### `callLogs/{deviceId}/entries/{autoId}`
```json
{
  "deviceId": "string",
  "number": "string",
  "name": "string",
  "type": "incoming|outgoing|missed|rejected|voicemail",
  "durationSec": "number",
  "timestamp": "number (epoch millis)",
  "deleted": "boolean (optional)",
  "deletedAt": "number (epoch millis, optional)"
}
```
