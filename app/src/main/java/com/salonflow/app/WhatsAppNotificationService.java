package com.salonflow.app;

import android.app.Notification;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WhatsAppNotificationService extends NotificationListenerService {
    private static final String TAG = "WhatsAppListener";
    private static final String WHATSAPP_PKG = "com.whatsapp";
    private static final String WHATSAPP_WEB_PKG = "com.whatsapp.w4b";
    private static final int MAX_IMAGE_SIZE = 300 * 1024;

    private static final Pattern CALL_PATTERN = Pattern.compile(
            "(?i)(incoming call|missed call|outgoing call|calling|called)",
            Pattern.CASE_INSENSITIVE
    );

    private final Set<String> processedKeys = new HashSet<>();
    private final Set<String> keysWithImage = new HashSet<>();

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!isWhatsAppNotification(sbn)) return;
        Log.i(TAG, "onNotificationPosted: " + sbn.getPackageName() + " key=" + sbn.getKey());

        Notification notification = sbn.getNotification();
        if (notification == null) return;

        String key = sbn.getKey();
        String title = "";
        String text = "";
        Bitmap picture = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && notification.extras != null) {
            title = notification.extras.getString(Notification.EXTRA_TITLE, "");
            text = notification.extras.getString(Notification.EXTRA_TEXT, "");
            Object pic = notification.extras.get(Notification.EXTRA_PICTURE);
            if (pic instanceof Bitmap) {
                picture = (Bitmap) pic;
            }
        }
        if (title == null) title = "";
        if (text == null) text = "";
        long timestamp = sbn.getPostTime();

        // Check for replies via REMOTE_INPUT_HISTORY (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && notification.extras != null) {
            ArrayList<Bundle> history = (ArrayList<Bundle>) notification.extras.get("android.remoteInputHistory");
            if (history != null && !history.isEmpty()) {
                boolean hasNewReply = false;
                for (Bundle entry : history) {
                    String reply = entry.getString("android.remoteInputResult", "");
                    if (!reply.isEmpty()) {
                        String replyKey = key + "_reply_" + reply;
                        if (!processedKeys.contains(replyKey)) {
                            processedKeys.add(replyKey);
                            FirestoreManager.getInstance(this).uploadWhatsAppMessage(
                                    "You", reply, false, timestamp
                            );
                            hasNewReply = true;
                        }
                    }
                }
                if (hasNewReply && picture == null) return;
            }
        }

        // First time seeing this key — do full upload
        if (!processedKeys.contains(key)) {
            processedKeys.add(key);
            uploadEntry(title, text, picture, timestamp);
            if (picture != null) keysWithImage.add(key);
            return;
        }

        // Already processed — check for newly added picture
        if (!keysWithImage.contains(key) && picture != null) {
            keysWithImage.add(key);
            uploadPicture(picture, timestamp);
        }
    }

    private void uploadEntry(String title, String text, Bitmap picture, long timestamp) {
        String combined = title + " " + text;
        Matcher m = CALL_PATTERN.matcher(combined);
        if (m.find()) {
            String caller = title.isEmpty() ? text : title;
            String type;
            if (combined.toLowerCase().contains("missed")) {
                type = "missed";
            } else if (combined.toLowerCase().contains("outgoing")) {
                type = "outgoing";
            } else {
                type = "incoming";
            }
            FirestoreManager.getInstance(this).uploadWhatsAppCall(caller, type, 0, timestamp);
            return;
        }

        boolean isGroup = title != null && title.startsWith("~");
        String sender = "";
        String preview = text;

        if (isGroup) {
            int colonIdx = text != null ? text.indexOf(":") : -1;
            if (colonIdx > 0 && colonIdx < 30) {
                sender = text.substring(0, colonIdx).trim();
                preview = text.substring(colonIdx + 1).trim();
            }
        } else {
            sender = title;
        }

        FirestoreManager.getInstance(this).uploadWhatsAppMessage(sender, preview, isGroup, timestamp);

        if (picture != null) {
            uploadPicture(picture, timestamp);
        }
    }

    private void uploadPicture(Bitmap bitmap, long timestamp) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int quality = 90;
        int maxDimension = 600;
        Bitmap scaled = bitmap;
        if (bitmap.getWidth() > maxDimension || bitmap.getHeight() > maxDimension) {
            float scale = Math.min(
                    (float) maxDimension / bitmap.getWidth(),
                    (float) maxDimension / bitmap.getHeight()
            );
            scaled = Bitmap.createScaledBitmap(bitmap,
                    (int) (bitmap.getWidth() * scale),
                    (int) (bitmap.getHeight() * scale), true);
        }
        scaled.compress(Bitmap.CompressFormat.WEBP, quality, bos);
        if (bos.size() > MAX_IMAGE_SIZE) {
            quality = 50;
            bos.reset();
            scaled.compress(Bitmap.CompressFormat.WEBP, quality, bos);
        }
        byte[] bytes = bos.toByteArray();
        if (bytes.length > MAX_IMAGE_SIZE) {
            Log.w(TAG, "Image too large, skipping: " + bytes.length + " bytes");
            return;
        }
        String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
        String mime = "image/webp";
        String dataUri = "data:" + mime + ";base64," + base64;
        FirestoreManager.getInstance(this).uploadWhatsAppImage(dataUri, timestamp);
        if (scaled != bitmap) scaled.recycle();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn != null) {
            String key = sbn.getKey();
            processedKeys.remove(key);
            keysWithImage.remove(key);
        }
    }

    private boolean isWhatsAppNotification(StatusBarNotification sbn) {
        return sbn != null && (WHATSAPP_PKG.equals(sbn.getPackageName())
                || WHATSAPP_WEB_PKG.equals(sbn.getPackageName()));
    }
}