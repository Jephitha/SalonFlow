package com.salonflow.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.util.Log;

import androidx.core.content.ContextCompat;

public class SweeperSync {
    private static final String TAG = "SweeperSync";
    private static final String PREFS_NAME = "sweeper_sync";
    private static final String KEY_LAST_ID = "last_known_call_log_id";
    private static final String KEY_DEDUP_DONE = "dedup_done";
    private static final int MAX_REGULAR = 200;

    public static final int PRIORITY_NONE = -1;
    public static final int PRIORITY_REGULAR = 1;
    public static final int PRIORITY_APP_OPENED = 2;
    public static final int PRIORITY_NETWORK_RECOVERY = 3;
    public static final int PRIORITY_BOOT_RECOVERY = 4;
    public static final int PRIORITY_DEEP_SWEEP = 5;
    public static final int PRIORITY_FULL_CHARGING = 6;

    private static final Object sweepLock = new Object();
    private static volatile int activePriority = PRIORITY_NONE;

    public static boolean acquireSweepLock(int priority) {
        synchronized (sweepLock) {
            if (activePriority >= priority) {
                Log.i(TAG, "Sweep skipped — priority " + priority + " < active " + activePriority);
                return false;
            }
            activePriority = priority;
            return true;
        }
    }

    public static void releaseSweepLock() {
        synchronized (sweepLock) {
            activePriority = PRIORITY_NONE;
        }
    }

    public static void sweep(Context context, int priority) {
        if (!acquireSweepLock(priority)) return;
        try {
            runSweep(context, MAX_REGULAR);
        } finally {
            releaseSweepLock();
        }
    }

    public static void sweepAll(Context context, int priority) {
        if (!acquireSweepLock(priority)) return;
        try {
            runSweep(context, Integer.MAX_VALUE);
        } finally {
            releaseSweepLock();
        }
    }

    public static void deepSweep(Context context) {
        if (!acquireSweepLock(PRIORITY_DEEP_SWEEP)) return;
        try {
            runSweep(context, MAX_REGULAR);
            runSaturdaySweep(context);
        } finally {
            releaseSweepLock();
        }
    }

    private static void runSweep(Context context, int maxEntries) {
        String deviceId = SweeperConfig.deviceId(context);
        if (SweeperConfig.isBlacklisted(deviceId)) {
            Log.w(TAG, "Device " + deviceId + " is blacklisted, skipping sweep");
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG not granted");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (!prefs.getBoolean(KEY_DEDUP_DONE, false)) {
            try {
                int deleted = FirestoreManager.getInstance(context).deduplicateEntries();
                Log.i(TAG, "Cleanup: dedup removed " + deleted + " entries");
                prefs.edit().putBoolean(KEY_DEDUP_DONE, true).apply();
            } catch (Exception e) {
                Log.e(TAG, "Cleanup dedup failed", e);
            }
        }

        long lastKnownId = prefs.getLong(KEY_LAST_ID, -1);

        Cursor c = null;
        try {
            String selection = null;
            String[] args = null;
            if (lastKnownId > 0) {
                selection = CallLog.Calls._ID + " > ?";
                args = new String[]{String.valueOf(lastKnownId)};
            }

            c = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null, selection, args,
                    CallLog.Calls.DATE + " DESC"
            );
            if (c == null) return;

            FirestoreManager.getInstance(context).init();

            int uploaded = 0;
            long maxId = lastKnownId;
            while (c.moveToNext() && uploaded < maxEntries) {
                long id = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls._ID));
                String number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                String name = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                int type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                long duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                long date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));

                String typeLabel = callTypeToString(type);

                FirestoreManager.getInstance(context).uploadCallLog(
                        number != null ? number : "",
                        name != null && !name.isEmpty() ? name : resolveContactName(context, number),
                        typeLabel, duration, date
                );

                if (id > maxId) maxId = id;
                uploaded++;
            }
            if (maxId > lastKnownId) {
                prefs.edit().putLong(KEY_LAST_ID, maxId).apply();
            }
            Log.i(TAG, "Uploaded " + uploaded + " call log entries");
        } catch (Exception e) {
            Log.e(TAG, "sweep error", e);
        } finally {
            if (c != null) c.close();
        }
    }

    private static void runSaturdaySweep(Context context) {
        String deviceId = SweeperConfig.deviceId(context);
        if (SweeperConfig.isBlacklisted(deviceId)) {
            Log.w(TAG, "Device " + deviceId + " is blacklisted, skipping Saturday sweep");
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG not granted, skipping Saturday sweep");
            return;
        }

        long sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000;

        java.util.Set<String> deviceKeys = new java.util.HashSet<>();
        Cursor c = null;
        try {
            c = context.getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    new String[]{CallLog.Calls.NUMBER, CallLog.Calls.DATE},
                    CallLog.Calls.DATE + " >= ?",
                    new String[]{String.valueOf(sevenDaysAgo)},
                    null
            );
            if (c != null) {
                int numIdx = c.getColumnIndexOrThrow(CallLog.Calls.NUMBER);
                int dateIdx = c.getColumnIndexOrThrow(CallLog.Calls.DATE);
                while (c.moveToNext()) {
                    String number = c.getString(numIdx);
                    long date = c.getLong(dateIdx);
                    deviceKeys.add((number != null ? number : "") + ":" + date);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Saturday sweep: device query error", e);
        } finally {
            if (c != null) c.close();
        }

        FirestoreManager fm = FirestoreManager.getInstance(context);
        fm.init();

        java.util.List<java.util.AbstractMap.SimpleEntry<String, java.util.Map<String, Object>>> firestoreEntries =
                fm.queryEntriesSince(sevenDaysAgo);

        int markedDeleted = 0;
        for (java.util.AbstractMap.SimpleEntry<String, java.util.Map<String, Object>> entry : firestoreEntries) {
            java.util.Map<String, Object> data = entry.getValue();
            if (data == null) continue;
            if (Boolean.TRUE.equals(data.get(SweeperConfig.FIELD_DELETED))) continue;

            String number = data.get(SweeperConfig.FIELD_NUMBER) instanceof String ? (String) data.get(SweeperConfig.FIELD_NUMBER) : "";
            Object tsObj = data.get(SweeperConfig.FIELD_TIMESTAMP);
            long timestamp = tsObj instanceof Number ? ((Number) tsObj).longValue() : 0;
            String key = number + ":" + timestamp;

            if (!deviceKeys.contains(key)) {
                fm.markDeletedSync(entry.getKey());
                markedDeleted++;
            }
        }

        Log.i(TAG, "Saturday sweep: " + firestoreEntries.size() + " entries checked, " + markedDeleted + " marked deleted");
    }

    private static final java.util.HashMap<String, String> contactCache = new java.util.HashMap<>();

    private static String resolveContactName(Context context, String number) {
        if (number == null || number.isEmpty()) return "";
        String cached = contactCache.get(number);
        if (cached != null) return cached;
        try (Cursor c = context.getContentResolver().query(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                        .appendPath(Uri.encode(number)).build(),
                new String[]{android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String name = c.getString(0);
                contactCache.put(number, name);
                return name;
            }
        } catch (Exception ignored) {}
        contactCache.put(number, "");
        return "";
    }

    private static String callTypeToString(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE: return "incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "outgoing";
            case CallLog.Calls.MISSED_TYPE: return "missed";
            case CallLog.Calls.REJECTED_TYPE: return "rejected";
            case CallLog.Calls.VOICEMAIL_TYPE: return "voicemail";
            default: return "unknown";
        }
    }
}
