# SalonFlow — Development Summary

## Project Overview
Salon management app (SalonFlow) + companion Reader app, with Firestore call-log sync, dark mode, and alarm-based scheduling.

- **Main app package**: `com.salonflow.app`  
- **Reader app package**: `com.salonflow.reader`  
- **Active branch**: `sweeper`  
- **Remote**: `origin/sweeper`  
- **Latest commit**: `9bfcb54` (Add 8 PM sweep, fix missing 2:30 PM alarm, first-Saturday unlimited sweepAll)  
- **App version**: 3.0 (versionCode 3)

---

## Critical Context

### Build
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew :app:assembleRelease
KEYSTORE_PASSWORD=jpkangethera
```

- JDK 17 is required (JDK 26 fails)
- Keystore password from env var `KEYSTORE_PASSWORD`

### ADB
```bash
/Users/jotham/Library/Android/sdk/platform-tools/adb
```
- Connected device IDs rotate between restarts

### Permissions in Manifest
Actively used: `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`, `INTERNET`, `READ_CALL_LOG`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`

Purged (unused): `WRITE_EXTERNAL_STORAGE`, `MANAGE_EXTERNAL_STORAGE`, `GET_ACCOUNTS`

---

## File Map

### Main App (`app/src/main/java/com/salonflow/app/`)

| File | Purpose |
|------|---------|
| `MainActivity.java` | Entry point, tab UI, backup, dark mode (`applyNightColors`) |
| `SweeperSync.java` | Call log upload (`sweep` capped 200, `sweepAll` unlimited, `sweepSaturday` comparison) |
| `FirestoreManager.java` | Singleton, Firestore CRUD, deterministic doc IDs, dedup cleanup |
| `SweeperConfig.java` | Constants: fields, alarm actions, request codes, device ID helpers |
| `SweeperScheduler.kt` | Alarm scheduling (7:30/14:30/20:00 weekdays, 2:00 Sat), connectivity observer |
| `SweeperAlarmReceiver.kt` | BroadcastReceiver: normal/saturday sweep + boot pending sweep |
| `SweeperChargingReceiver.kt` | BroadcastReceiver: runs `sweepAll()` on first charge after update |
| `SweeperBootReceiver.kt` | BroadcastReceiver: re-schedules alarms after reboot |
| `SweeperApp.kt` | Application class: init scheduler + charging check |
| `BookingsCompose.kt` | Calendar with weekend white text in dark mode |
| `SettingsCompose.kt` | Backup/restore buttons (white text), Drive sign-in/out |
| `NotificationBell.kt` | Notification dialog, theme-aware cards, swipe-to-mark-read |
| `Theme.kt` / `Colors.kt` | Compose color scheme definitions |
| `FirestoreManager.java` | All Firestore operations |

### Reader App (`reader/src/main/java/com/salonflow/reader/`)

| File | Purpose |
|------|---------|
| `MainActivity.kt` | All screens: PIN, device list, call logs, stats, branding |
| `FirestoreReader.kt` | Firestore data loading with dedup |

---

## Features Implemented

### Sweeper (Call Log Upload)
- `SweeperSync.sweep()` — uploads new call logs (cap 200) tracking last `_id`
- `SweeperSync.sweepAll()` — unlimited upload (used once on first charge)
- `SweeperSync.sweepSaturday()` — compares device vs Firestore for last 7 days, marks deleted
- Deterministic document IDs (`SHA-256(number + ":" + timestamp)`) prevent duplicates
- Static lock (`sweepLock`) prevents concurrent sweeps
- One-time dedup cleanup on first `sweep()`/`sweepAll()` after deploy

### Alarm Scheduling
| Day | Times | Notes |
|-----|-------|-------|
| Mon–Fri | 7:30, 14:30, 20:00 | Normal `sweep()` |
| Saturday | 2:00 AM | Normal `sweep()` + `sweepSaturday()` comparison; cancels 7:30/14:30/20:00 |
| First charge after update | immediate | `sweepAll()` unlimited, sets `KEY_FIRST_SATURDAY_DONE` |

### Offline Resilience
- `sweep_pending` flag in SharedPreferences when sweep fails due to no connectivity
- `ConnectivityManager.NetworkCallback` runs pending sweep when internet returns
- `SweeperBootReceiver` re-schedules alarms on boot + runs pending sweep
- `SweeperChargingReceiver` triggers first unlimited sweep on power connect

### Dark Mode (Main App)
- `MODE_NIGHT_FOLLOW_SYSTEM`
- Dynamic colors in `applyNightColors()`: PAPER/SURFACE/MUTED per `uiMode`
- Compose pages use `MaterialTheme.colorScheme.*`
- Calendar cells retain light colors (`0xFFF8FAF8`) in dark mode
- Weekend date text switches to `Color.White` in dark mode
- Notification dialog uses theme-aware backgrounds/text

### Reader App
- **Branding**: "Weather Reader", dark blue `#192E45`, yellow `#FFD524`
- **PIN**: 6-digit PBKDF2 with device Android ID salt
- **Device list**: deduplicated, hide button, white top-bar labels
- **Call logs**: search by number, "Deleted" filter chip, DELETED label
- **Stats**: most contacted, most missed/declined, per-number exclude/restore (stores `number:name`)
- **Long-press**: copy number to clipboard with Toast
- **Reader dedup**: `.distinctBy { it.number + ":" + it.timestamp }`

### Firestore Schema
**`callLogs/{deviceId}`**: `deviceId`, `deviceName`, `lastSeen`
**`callLogs/{deviceId}/entries/{docId}`**: `deviceId`, `number`, `name`, `type`, `durationSec`, `timestamp`, `deleted` (optional), `deletedAt` (optional)

Doc ID = `SHA-256(number + ":" + timestamp)` for dedup.

---

## Key Decisions
- **Blacklist over self-filtering**: reader device `9bde8680c88a308a` hard-coded; skips upload + purges existing data
- **WhatsApp removed**: entire feature deleted from sweeper and reader
- **First charge unlimited**: replaces old "first Saturday" logic — `sweepAll()` runs on first power connect (or if already charging at app start), not on Saturday
- **Alarm request codes**: 730=1001, 1430=1003, 2000=1004, Saturday=1002 (each time a unique PendingIntent)
- **Notification suppression**: no sweep produces notifications

---

## What to Resume / Known Issues
- Reader app color `Orange = 0xFFFFD524` is yellow (from logo) — used for "rejected" calls, "DELETED" labels
- After dedup fix, existing Firestore duplicates will be cleaned on first sweep
- Device IDs in Firestore with old random doc IDs will remain but dedup removes extras
- `FirestoreManager.deduplicateEntries()` deletes all but one copy per `number:timestamp` key
