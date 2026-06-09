package com.salonflow.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public class AppSettings {
    private static final String PREFS = "salonflow_prefs";
    private static final String KEY_PIN_HASH = "pin_hash";
    private static final String KEY_PIN_SALT = "pin_salt";
    private static final String KEY_SETTINGS_PIN_REQUIRED = "settings_pin_required";
    private static final String KEY_APP_LOCK_ENABLED = "app_lock_enabled";
    private static final String KEY_NOTIFY_MORNING_BOOKINGS = "notify_morning_bookings";
    private static final String KEY_NOTIFY_OVERDUE = "notify_overdue_7_days";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_LAST_PIN_VERIFIED = "last_pin_verified";
    private static final String KEY_DRIVE_KEY_SALT = "drive_key_salt";
    private static final String KEY_DRIVE_KEY_BYTES = "drive_key_bytes";
    private static final String KEY_LAST_DRIVE_BACKUP = "last_drive_backup";
    private static final long GRACE_PERIOD_MS = 120_000;

    private final SharedPreferences prefs;

    AppSettings(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    boolean hasPin() {
        return !prefs.getString(KEY_PIN_HASH, "").isEmpty();
    }

    boolean isSettingsPinRequired() {
        return prefs.getBoolean(KEY_SETTINGS_PIN_REQUIRED, true) && hasPin();
    }

    void setSettingsPinRequired(boolean required) {
        prefs.edit().putBoolean(KEY_SETTINGS_PIN_REQUIRED, required).apply();
    }

    boolean isAppLockEnabled() {
        return prefs.getBoolean(KEY_APP_LOCK_ENABLED, false) && hasPin();
    }

    void setAppLockEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply();
    }

    boolean checkPin(String input) {
        if (input == null) return false;
        String salt = prefs.getString(KEY_PIN_SALT, "");
        if (salt.isEmpty()) return hash(input.trim(), "").equals(prefs.getString(KEY_PIN_HASH, ""));
        return hash(input.trim(), salt).equals(prefs.getString(KEY_PIN_HASH, ""));
    }

    void setPin(String pin) {
        String trimmed = pin == null ? "" : pin.trim();
        if (trimmed.isEmpty()) {
            prefs.edit().putString(KEY_PIN_HASH, "").putString(KEY_PIN_SALT, "")
                    .remove(KEY_DRIVE_KEY_SALT).remove(KEY_DRIVE_KEY_BYTES).apply();
            return;
        }
        SecureRandom rng = new SecureRandom();
        byte[] saltBytes = new byte[16];
        rng.nextBytes(saltBytes);
        StringBuilder salt = new StringBuilder();
        for (byte b : saltBytes) salt.append(String.format("%02x", b & 0xff));
        String saltHex = salt.toString();
        prefs.edit()
                .putString(KEY_PIN_HASH, hash(trimmed, saltHex))
                .putString(KEY_PIN_SALT, saltHex)
                .remove(KEY_DRIVE_KEY_SALT)
                .remove(KEY_DRIVE_KEY_BYTES)
                .apply();
    }

    void clearPin() {
        prefs.edit()
                .putString(KEY_PIN_HASH, "")
                .putBoolean(KEY_SETTINGS_PIN_REQUIRED, false)
                .putBoolean(KEY_APP_LOCK_ENABLED, false)
                .putLong(KEY_LAST_PIN_VERIFIED, 0)
                .remove(KEY_DRIVE_KEY_SALT)
                .remove(KEY_DRIVE_KEY_BYTES)
                .apply();
    }

    void cacheDriveKey(String pin) {
        try {
            byte[] salt = new byte[16];
            new java.security.SecureRandom().nextBytes(salt);
            byte[] keyBytes = DataCipher.deriveKeyBytes(pin, salt);
            StringBuilder sb = new StringBuilder();
            for (byte b : salt) sb.append(String.format("%02x", b & 0xff));
            prefs.edit()
                    .putString(KEY_DRIVE_KEY_SALT, sb.toString())
                    .putString(KEY_DRIVE_KEY_BYTES, android.util.Base64.encodeToString(keyBytes, android.util.Base64.NO_WRAP))
                    .apply();
        } catch (Exception e) {
            android.util.Log.e("AppSettings", "Failed to cache drive key", e);
        }
    }

    boolean hasDriveKey() {
        return !prefs.getString(KEY_DRIVE_KEY_BYTES, "").isEmpty();
    }

    byte[] getDriveKeyBytes() {
        String s = prefs.getString(KEY_DRIVE_KEY_BYTES, null);
        if (s == null || s.isEmpty()) return null;
        try {
            return android.util.Base64.decode(s, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    byte[] getDriveKeySalt() {
        String s = prefs.getString(KEY_DRIVE_KEY_SALT, null);
        if (s == null || s.isEmpty()) return null;
        byte[] salt = new byte[16];
        for (int i = 0; i < 16; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            salt[i] = (byte) ((hi << 4) | lo);
        }
        return salt;
    }

    long getLastDriveBackupTime() {
        return prefs.getLong(KEY_LAST_DRIVE_BACKUP, 0);
    }

    void setLastDriveBackupTime(long time) {
        prefs.edit().putLong(KEY_LAST_DRIVE_BACKUP, time).apply();
    }

    boolean notifyMorningBookings() {
        return prefs.getBoolean(KEY_NOTIFY_MORNING_BOOKINGS, false);
    }

    void setNotifyMorningBookings(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFY_MORNING_BOOKINGS, enabled).apply();
    }

    boolean notifyOverdueSevenDays() {
        return prefs.getBoolean(KEY_NOTIFY_OVERDUE, false);
    }

    void setNotifyOverdueSevenDays(boolean enabled) {
        prefs.edit().putBoolean(KEY_NOTIFY_OVERDUE, enabled).apply();
    }

    boolean onboardingDone() {
        return prefs.getBoolean(KEY_ONBOARDING_DONE, false);
    }

    void setOnboardingDone(boolean done) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, done).apply();
    }

    void markPinVerified() {
        prefs.edit().putLong(KEY_LAST_PIN_VERIFIED, System.currentTimeMillis()).apply();
    }

    boolean isWithinGracePeriod() {
        return System.currentTimeMillis() - prefs.getLong(KEY_LAST_PIN_VERIFIED, 0) < GRACE_PERIOD_MS;
    }

    private static String hash(String value, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes());
            byte[] digest = md.digest(value.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return value;
        }
    }
}
