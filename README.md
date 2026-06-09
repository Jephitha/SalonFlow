# SalonFlow

SalonFlow is a local-first Android salon management app for small salon owners. It gives owners quick visibility into bookings, revenue, expenses, stylist commissions, inventory COGS, product stock purchases, reports, and backups — with optional encrypted Google Drive backup.

## Features

- **Dashboard**: Daily business health — bookings, collected amount, outstanding balances, business revenue, product COGS, commissions, stock purchases, low stock alerts
- **Bookings**: Monthly calendar view with date drill-down, add/edit/cancel/mark-paid, partial payments, overdue tracking
- **Revenue**: Walk-in sales, expenses, cash & mobile money payment methods, month totals, payment mix
- **Clients**: Customer records with name/phone, client bookings and sales with partial payment support, payment behaviour analysis
- **Reports**: Business revenue (collected − expenses − commissions − COGS), daily/monthly gross sales, stylist revenue, commissions, expenses, product COGS, stock purchases KPI, trend charts
- **Inventory**: Stock products with FIFO batch tracking, purchase history, COGS computed from actual batch costs at sale time
- **Settings**: Manage stylists, services, inventory; configure security PIN and app lock; notification preferences; backup & restore
- **Backup**: Local backup + optional PIN-encrypted Google Drive backup with automatic retry on failure
- **Notifications**: In-app notification system with bell icon badge, swipe-to-dismiss, color-coded types (backup success/failure, overdue, reminders)

## Security

- **Data at rest**: All client names, phone numbers, stylist names/phones, and service descriptions encrypted with AES/GCM via **Android Keystore**. Hardware-backed key, never leaves the device.
- **PIN hashing**: 16-byte `SecureRandom` salt per user + SHA-256 (not plaintext, not unsalted). Legacy unsalted hashes still verified for migrated users.
- **App lock**: PIN-based app lock with 2-minute grace period after successful entry.
- **Backup encryption**: PIN-derived PBKDF2WithHmacSHA256 key (100K iterations, 256-bit, 16-byte salt) — restorable on any device where the user knows the PIN.
- **Drive key cache**: Derived AES key bytes cached in SharedPreferences (not the PIN). Invalidated on PIN change.
- **No plaintext PIN stored anywhere**. No PII logged.
- **No network calls unless Drive backup is explicitly configured and enabled**.

## Inventory & COGS

Stock products are tracked via FIFO batches. When a product is purchased, a batch record stores the quantity and unit purchase price. When products are sold, `deductFromBatches()` consumes oldest batches first, and the actual batch cost is recorded in `sale_stock_usage`.

- **COGS** = cost of goods actually sold in the selected period (from batch costs at sale time)
- **Stock purchases** = total purchase cost of batches added in the period (visibility KPI)
- **Business Revenue** = `collected − expenses − commissions − COGS`
- Stock purchases are **not** expensed immediately — they are asset conversions recognized through COGS at sale time

## Backups

### Local
- Daily auto-backup at 8PM via `AlarmManager`
- Manual backup and restore in Settings
- Location: `<app external files>/SalonFlowBackups`
- Encrypted with Android Keystore AES/GCM (`.db.enc` files)

### Google Drive (Opt-In)
- PIN-encrypted backup files uploaded to `SalonFlowBackups` folder on Drive
- Sign in with Google account (no Drive scope requested at sign-in — scope fetched at token time via `AccountManager`)
- Silent auto-backup at 8PM using cached key (no PIN re-prompt)
- Retry mechanism: up to 3 retries at 5-minute intervals on failure
- In-app notification after 3 failed Drive upload attempts
- Requires Google Cloud Console OAuth 2.0 setup for Drive API — see `HANDOVER.md`

## Tech Stack

- Java + Kotlin hybrid (all screens migrated to Compose; dialogs and navigation shell remain in Java)
- Android SDK 36, minSdk 23
- Jetpack Compose + Material 3 (all screens)
- SQLite via `SQLiteOpenHelper` (`SalonDatabase.java`)
- Google Drive REST API v3 (HTTP, no Drive SDK)
- Gradle 8.13, Kotlin 2.0.21, Compose 1.7.0/1.3.0
- JUnit 4 + Robolectric for unit tests

## Run Locally

Build the debug APK:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

Run tests:

```bash
./gradlew testDebugUnitTest
```

See `HANDOVER.md` for detailed build commands, architecture overview, migration roadmap, security audit, and developer notes.
