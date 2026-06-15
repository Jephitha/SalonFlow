package com.salonflow.app;

import android.provider.Settings;
import android.content.Context;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.Set;

public class SweeperConfig {
    static final String COLLECTION_CALL_LOGS = "callLogs";
    static final String PREFS_SWEEPER = "sweeper_scheduler";
    static final String KEY_SWEEP_PENDING = "sweep_pending";

    static final String FIELD_DEVICE = "deviceId";
    static final String FIELD_NUMBER = "number";
    static final String FIELD_NAME = "name";
    static final String FIELD_TYPE = "type";
    static final String FIELD_DURATION = "durationSec";
    static final String FIELD_TIMESTAMP = "timestamp";
    static final String FIELD_DELETED = "deleted";
    static final String FIELD_DELETED_AT = "deletedAt";

    static final String ACTION_SWEEP_NORMAL = "com.salonflow.app.action.SWEEP_NORMAL";
    static final String ACTION_SWEEP_SATURDAY = "com.salonflow.app.action.SWEEP_SATURDAY";

    static final int ALARM_REQUEST_CODE_NORMAL = 1001;
    static final int ALARM_REQUEST_CODE_SATURDAY = 1002;

    // Devices whose data should never be uploaded to Firestore (reader device)
    static final Set<String> BLACKLISTED_DEVICES = Collections.singleton("9bde8680c88a308a");

    static String deviceId(Context context) {
        String id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (id == null) id = "unknown";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(id.getBytes());
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString().substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return id;
        }
    }

    static String deviceDisplayName() {
        String manufacturer = Build.MANUFACTURER;
        if (manufacturer != null && !manufacturer.isEmpty()) {
            manufacturer = Character.toUpperCase(manufacturer.charAt(0)) + manufacturer.substring(1);
        } else {
            manufacturer = "Unknown";
        }
        return manufacturer + " " + Build.MODEL;
    }

    static boolean isBlacklisted(String deviceId) {
        return BLACKLISTED_DEVICES.contains(deviceId);
    }
}
