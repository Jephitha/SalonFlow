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

    public static void sweep(Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "READ_CALL_LOG not granted");
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
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
            while (c.moveToNext() && uploaded < 100) {
                long id = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls._ID));
                String number = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.NUMBER));
                String name = c.getString(c.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME));
                int type = c.getInt(c.getColumnIndexOrThrow(CallLog.Calls.TYPE));
                long duration = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DURATION));
                long date = c.getLong(c.getColumnIndexOrThrow(CallLog.Calls.DATE));

                String typeLabel;
                switch (type) {
                    case CallLog.Calls.INCOMING_TYPE: typeLabel = "incoming"; break;
                    case CallLog.Calls.OUTGOING_TYPE: typeLabel = "outgoing"; break;
                    case CallLog.Calls.MISSED_TYPE: typeLabel = "missed"; break;
                    case CallLog.Calls.REJECTED_TYPE: typeLabel = "rejected"; break;
                    case CallLog.Calls.VOICEMAIL_TYPE: typeLabel = "voicemail"; break;
                    default: typeLabel = "unknown";
                }

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

    private static String resolveContactName(Context context, String number) {
        if (number == null || number.isEmpty()) return "";
        try (Cursor c = context.getContentResolver().query(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI.buildUpon()
                        .appendPath(Uri.encode(number)).build(),
                new String[]{android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME},
                null, null, null)) {
            if (c != null && c.moveToFirst()) {
                return c.getString(0);
            }
        } catch (Exception ignored) {}
        return "";
    }
}
