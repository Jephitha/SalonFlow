# SalonFlow Handover

## Project Location

`/Users/jotham/Documents/New project`

## Build & Install

### Debug build
```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release build (signed)
```bash
KEYSTORE_PASSWORD="tempstorepass" JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home ./gradlew assembleRelease
```

APK output (after running the build): `app/build/outputs/apk/release/app-release.apk`

Keystore: `app/salonflow-release.keystore` (alias: `salonflow`)
SHA-1 fingerprint: `E0:FE:D3:96:88:5E:E4:EF:29:C5:A5:C0:0F:56:B2:2E:8B:EA:F3:8B`
Use this SHA-1 in Google Cloud Console → Credentials → OAuth 2.0 Client ID for the release build.

The release build has `debuggable false` and is minified with ProGuard.

### Run tests
```bash
./gradlew testDebugUnitTest
```

APK after build: `app/build/outputs/apk/debug/app-debug.apk`

Android SDK: `/Users/jotham/Library/Android/sdk`

## Source Files

`app/src/main/java/com/salonflow/app/`:

| File | Purpose |
|---|---|
| `MainActivity.java` | Activity: navigation, dialogs, business logic (1876 lines) |
| `SalonDatabase.java` | SQLite data layer (backups, CRUD, schema v2, FIFO batch deduction) |
| `AppSettings.java` | SharedPreferences: salted PIN hash, drive key cache, toggles, timestamps |
| `DataCipher.java` | AES/GCM encryption: Android Keystore (data at rest), PBKDF2 (backups) |
| `DriveBackupManager.java` | Google Drive REST API v3: upload, list, download, delete, retry scheduling |
| `BackupReceiver.java` | Daily 8PM BroadcastReceiver: local backup + silent Drive upload |
| `DriveRetryReceiver.java` | Retry BroadcastReceiver (AlarmManager): 3 retries at 5-min intervals |
| `ReminderReceiver.java` | Morning/overdue notification scheduling |
| `HomeCompose.kt` | Compose dashboard: bookings, revenue, commissions, stock, COGS |
| `BookingsCompose.kt` | Compose calendar + booking list + CRUD |
| `RevenueCompose.kt` | Compose revenue/expense tracking |
| `ClientsCompose.kt` | Compose client list + dialogs |
| `ReportsCompose.kt` | Compose reports: KPIs, COGS, stock purchases, business revenue, trends |
| `SettingsCompose.kt` | Compose settings hub + sub-pages (services, stylists, inventory, security, notifications, backup) |
| `NotificationRepository.kt` | Persistent in-app notification store (SharedPreferences JSON) |
| `NotificationBell.kt` | Compose bell icon with badge + swipeable notification dialog |
| `NotificationEntry.kt` | Data class for notification entries |
| `Theme.kt` | Shared Material 3 light/dark theme |
| `Colors.kt` | Shared Compose color palette |
| `SalonUtils.kt` | Utility functions (gross, collected, commission, expenses, outstanding) |

### Test Files

`app/src/test/java/com/salonflow/app/`:

| File | Tests |
|---|---|
| `DataCipherTest.kt` | 6 tests: encrypt/decrypt roundtrip, wrong PIN, key derivation |
| `AppSettingsTest.kt` | 9 tests: key caching, PIN change clears key, timestamp, restart |
| `DriveBackupManagerTest.kt` | 3 tests: retry scheduling, sign-in state |
| `NotificationRepositoryTest.kt` | CRUD tests for in-app notifications |
| `SalonUtilsTest.kt` | Utility function tests |
| `SalonDatabaseTest.kt` | 3 tests: stockPurchasesBetween sum, zero, date range |

**Total: 30 tests**, run with `./gradlew testDebugUnitTest`.

## Data Storage

Database: `salonflow.db` (SQLite via `SQLiteOpenHelper`, version 2)

Tables: `stylists`, `services`, `clients`, `inventory`, `bookings`, `sales`, `expenses`, `stock_batches`, `sale_stock_usage`

Monetary values are stored in **cents** (DB v6 migration). UI divides by 100 for display.

## Inventory & COGS Logic

### Stock Tracking (FIFO Batches)

When products are purchased:
- A `stock_batches` row is created with `quantity`, `purchasePrice` (per-unit in cents), `dateAdded`, `remaining`
- `inventory.on_hand` is incremented
- Multiple batches for the same product track different purchase prices

When products are sold:
- `deductFromBatches()` consumes oldest batches first (`ORDER BY id`)
- `sale_stock_usage` records each batch's actual `purchasePrice` × `quantity`
- `stock_batches.remaining` and `inventory.on_hand` are decremented

### COGS Calculation

`costOfGoodsSold(start, end)` — sums `sale_stock_usage.purchasePrice * quantity` for sales in the date range.

### Stock Purchases (Visibility)

`stockPurchasesBetween(start, end)` — sums `stock_batches.purchasePrice * quantity` for batches added in the date range. Shown as a KPI on Home and Reports screens.

Stock purchases are **NOT** recorded as expenses. They are an asset conversion (cash → inventory) recognized as COGS when products are sold.

### Business Revenue Formula

`businessRevenue = collected - expenses - commission - cogs`

Shown on Home and Reports screens. The "Profit on sales" card shows `grossSales - cogs` (product margin only).

## Encryption

### Data at Rest
- Client names, phone numbers, stylist names/phones, service descriptions encrypted with AES/GCM via **Android Keystore**
- Hardware-backed key under alias `salonflow-data-key`
- Encrypted string format: `enc:{base64(1-byte-iv-length + iv + ciphertext)}`

### Backup Encryption
- **PIN-based**: PBKDF2WithHmacSHA256 (100K iterations, 256-bit, 16-byte salt)
- **Cached-key**: Same PBKDF2-derived bytes cached for silent auto-backup
- File format: `[16-byte salt][1-byte IV length][IV][AES/GCM ciphertext]`

### PIN Hashing
- New PINs: 16-byte `SecureRandom` salt, SHA-256(salt + PIN)
- Legacy: unsalted SHA-256 verified via `hash(pin, "")` fallback

## Backups

### Local
- Auto: daily 8PM via `AlarmManager` (`BackupReceiver`)
- Manual: Settings → Backup & Restore
- Files: `<app external files>/SalonFlowBackups/salonflow-yyyyMMdd-HHmmss.db`
- Encrypted with Android Keystore AES/GCM

### Google Drive (Opt-In)
- PIN-encrypted files in `SalonFlowBackups` Drive folder
- Silent auto-backup via cached key (no PIN re-prompt)
- Retry: up to 3 attempts at 5-min intervals (`DriveRetryReceiver`)
- In-app notification after 3 failures

## Navigation

### Settings Sub-Pages
- Compose-driven `currentSection` state + Activity's `settingsNavStack` (Deque)
- Forward navigation: `pushSettingsSection(currentSection)` then update `currentSection`
- Back button / back arrow: `popSettingsSection()` restores previous section
- The Activity's `settingsNavStack` survives `rerender()` (ComposeView rebuild), so back navigation is preserved across toggle/dialog actions

### Latest Bug Fixes
| Fix | File | Detail |
|---|---|---|
| Settings back nav preserved across rerender | `MainActivity.java:85` `SettingsCompose.kt` | Stack stored in Activity `settingsNavStack` Deque instead of Compose `remember` |
| Commissions always queried today | `HomeCompose.kt:122` | Changed `bookingsBetween(today, today)` to `bookingsBetween(start, end)` |
| Home screen recent transactions clipped | `HomeCompose.kt:82-83` | Added `verticalScroll` + `fillMaxSize` to Column/Surface |
| Settings sub-page reset on rerender | `MainActivity.java:1706` | `settingsSection` now synced from Compose via `updateFabForSettingsSection` |
| Config file misnamed | Build system | `signingConfigs.release` renamed to `build.gradle` — Gradle requires this exact name |
| Drive timestamp not updating | `MainActivity.java:1217` | Added `rerender()` after `setLastDriveBackupTime()` |

## Known Issues

| Issue | Location | Severity | Detail |
|---|---|---|---|
| Deprecated `Locale` constructor | All Compose files, `MainActivity` | Low | Replace `Locale("en", "KE")` with `Locale.Builder` or `forLanguageTag` (API 21+) |
| `@Suppress("EXPOSED_PARAMETER_TYPE")` | `SalonUtils.kt:1` | Low | `ThreadLocal<SimpleDateFormat>` exposed field — cosmetic warning |
| FAB visibility flash | `MainActivity.java:1703-1723` | Low | `renderFloatingActionButton()` hides FAB, then `LaunchedEffect` shows it |
| `readStream()` unbounded | `DriveBackupManager.java:461` | Low | Reads Drive API response entirely into heap — acceptable for metadata payloads |
| Legacy plaintext PIN migration | `AppSettings.java:173` | Removed | `migratePlaintextPin()` cleaned out — all users now on salted hashes |
| No backup file size limit | `decryptWithPin` | Low | Stream-based (8 KB buffer), so large files handled incrementally — safe |
| Manual backup filename has no `.enc` | `SalonDatabase.java:582` | Low | Info only — `salonflow.db.enc` extension, not the DB filename |

## Production Build Checklist

- [x] Keystore generated (`app/salonflow-release.keystore`)
- [x] `minifyEnabled true` (already configured)
- [x] `debuggable false` in release build type
- [x] ProGuard rules (`app/proguard-rules.pro`) — keep rules for Google Sign-In and AccountManager
- [ ] Update app version in `app/build.gradle` (`versionCode`, `versionName`)
- [ ] Add SHA-1 `ED:90:BA:DE:BE:C3:56:2D:E6:9E:A2:E1:F5:38:2D:A2:59:3D:CA:9B` to Google Cloud Console OAuth client for release signing
- [x] `pin_value` migration code removed (`migratePlaintextPin()` in AppSettings.java)
- [x] Build config file renamed `signingConfigs.release` → `build.gradle`
- [ ] Set secure `KEYSTORE_PASSWORD` env var for CI/CD or manual builds
- [ ] Remove `@Suppress("EXPOSED_PARAMETER_TYPE")` if migrating to `java.time`


## Google Drive OAuth Setup

1. https://console.cloud.google.com/ → enable Drive API
2. Credentials → OAuth 2.0 Client ID → Android
3. Package: `com.salonflow.app`
4. SHA-1: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
5. Repeat for release keystore fingerprint

Without this setup, Drive sign-in fails with DEVELOPER_ERROR / status 12500.

## Security Audit

| Finding | Severity | Detail |
|---|---|---|
| PIN storage | Good | Salted SHA-256 hash, not plaintext |
| Android Keystore | Good | Hardware-backed AES/GCM for data at rest |
| Backup encryption | Good | PBKDF2-derived key, cross-device restorable |
| Drive key cache | Good | Derived AES bytes (not PIN), invalidated on PIN change |
| SQL injection | Good | All 23+ `rawQuery` calls use `?` parameterized bindings |
| SharedPreferences | Good | `MODE_PRIVATE` (not world-readable) |
| Network exposure | Good | `INTERNET` + `GET_ACCOUNTS` only for opt-in Drive |
| Logging | Good | `Log.*` calls on errors only, no PII logged |
| Biometric auth | Low | Only PIN-based app lock |

## Migration Roadmap

1. **Phase 1**: Home, Bookings, Revenue → Compose ✅
2. **Phase 2**: Clients, Reports → Compose ✅
3. **Phase 3**: Settings → Compose ✅
4. **Phase 4**: Decompose MainActivity — extract dialogs to Compose, navigation to separate controller
5. **Phase 5** (optional): Room ORM for reactive `Flow` queries
6. **Phase 6** (optional): Dark mode, data export (CSV/PDF), biometric unlock

## Codebase Stats

- MainActivity.java: 1876 lines
- SalonDatabase.java: 783 lines
- All Compose files: ~2100 lines combined
- Test files: 6 files, 30 tests
- Gradle: 8.13, Kotlin 2.0.21, Compose 1.7.0/1.3.0, minSdk 23, targetSdk 36
