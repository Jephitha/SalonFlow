package com.salonflow.app;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
                if (SweeperConfig.isBlacklisted(deviceId)) {
                    Log.w(TAG, "Device " + deviceId + " is blacklisted — purging data and skipping upload");
                    FirebaseAuth auth = FirebaseAuth.getInstance();
                    if (auth.getCurrentUser() == null) {
                        Tasks.await(auth.signInAnonymously());
                    }
                    purgeDeviceData();
                    initialized = true;
                    initLatch.countDown();
                    return;
                }
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

    private boolean isBlacklisted() {
        return SweeperConfig.isBlacklisted(deviceId);
    }

    public void uploadCallLog(String number, String name, String type, long durationSec, long timestamp) {
        executor.execute(() -> {
            if (isBlacklisted()) return;
            try {
                awaitInit();
                Tasks.await(addCallLog(number, name, type, durationSec, timestamp));
                Log.i(TAG, "Uploaded call log: " + number);
            } catch (Exception e) {
                Log.e(TAG, "Failed to upload call log", e);
            }
        });
    }

    private static String docIdForCallLog(String number, long timestamp) {
        try {
            String raw = (number != null ? number : "") + ":" + timestamp;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return (number != null ? number : "") + "_" + timestamp;
        }
    }

    private Task<Void> addCallLog(String number, String name, String type, long durationSec, long timestamp) {
        Map<String, Object> data = new HashMap<>();
        data.put(SweeperConfig.FIELD_DEVICE, deviceId);
        data.put(SweeperConfig.FIELD_NUMBER, number);
        data.put(SweeperConfig.FIELD_NAME, name != null ? name : "");
        data.put(SweeperConfig.FIELD_TYPE, type);
        data.put(SweeperConfig.FIELD_DURATION, durationSec);
        data.put(SweeperConfig.FIELD_TIMESTAMP, timestamp);
        String docId = docIdForCallLog(number, timestamp);
        return collection(SweeperConfig.COLLECTION_CALL_LOGS).document(docId).set(data);
    }

    private void purgeDeviceData() {
        String[] collections = {SweeperConfig.COLLECTION_CALL_LOGS};
        for (String col : collections) {
            try {
                int deleted = 0;
                while (true) {
                    QuerySnapshot snap = Tasks.await(
                        db.collection(col).document(deviceId).collection("entries")
                            .limit(500)
                            .get()
                    );
                    if (snap.isEmpty()) break;
                    WriteBatch batch = db.batch();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                        batch.delete(doc.getReference());
                        deleted++;
                    }
                    Tasks.await(batch.commit());
                }
                if (deleted > 0) {
                    Log.i(TAG, "Purged " + deleted + " entries from " + col);
                }
                Tasks.await(db.collection(col).document(deviceId).delete());
            } catch (Exception e) {
                Log.e(TAG, "Failed to purge " + col, e);
            }
        }
    }

    private CollectionReference collection(String name) {
        return db.collection(name).document(deviceId).collection("entries");
    }

    private void registerDevice() {
        try {
            for (String col : new String[]{SweeperConfig.COLLECTION_CALL_LOGS}) {
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

    public List<AbstractMap.SimpleEntry<String, Map<String, Object>>> queryEntriesSince(long sinceTimestamp) {
        if (isBlacklisted()) return java.util.Collections.emptyList();
        try {
            awaitInit();
            com.google.firebase.firestore.QuerySnapshot snap = Tasks.await(
                collection(SweeperConfig.COLLECTION_CALL_LOGS)
                    .whereGreaterThanOrEqualTo(SweeperConfig.FIELD_TIMESTAMP, sinceTimestamp)
                    .get()
            );
            List<AbstractMap.SimpleEntry<String, Map<String, Object>>> results = new java.util.ArrayList<>();
            for (com.google.firebase.firestore.DocumentSnapshot doc : snap.getDocuments()) {
                results.add(new AbstractMap.SimpleEntry<>(doc.getId(), doc.getData()));
            }
            return results;
        } catch (Exception e) {
            Log.e(TAG, "Failed to query entries since " + sinceTimestamp, e);
            return java.util.Collections.emptyList();
        }
    }

    public void markDeleted(String entryId) {
        executor.execute(() -> {
            if (isBlacklisted()) return;
            try {
                awaitInit();
                Map<String, Object> updates = new HashMap<>();
                updates.put(SweeperConfig.FIELD_DELETED, true);
                updates.put(SweeperConfig.FIELD_DELETED_AT, System.currentTimeMillis());
                Tasks.await(
                    collection(SweeperConfig.COLLECTION_CALL_LOGS).document(entryId)
                        .update(updates)
                );
                Log.i(TAG, "Marked entry " + entryId + " as deleted");
            } catch (Exception e) {
                Log.e(TAG, "Failed to mark entry " + entryId + " as deleted", e);
            }
        });
    }

    public void markDeletedSync(String entryId) {
        if (isBlacklisted()) return;
        try {
            awaitInit();
            Map<String, Object> updates = new HashMap<>();
            updates.put(SweeperConfig.FIELD_DELETED, true);
            updates.put(SweeperConfig.FIELD_DELETED_AT, System.currentTimeMillis());
            Tasks.await(
                collection(SweeperConfig.COLLECTION_CALL_LOGS).document(entryId)
                    .update(updates)
            );
            Log.i(TAG, "Marked entry " + entryId + " as deleted (sync)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to mark entry " + entryId + " as deleted (sync)", e);
        }
    }

    public int deduplicateEntries() {
        if (isBlacklisted()) return 0;
        try {
            awaitInit();
            QuerySnapshot snap = Tasks.await(collection(SweeperConfig.COLLECTION_CALL_LOGS).get());
            Map<String, String> seen = new HashMap<>();  // key -> first doc ID
            Set<String> toDelete = new HashSet<>();
            for (DocumentSnapshot doc : snap.getDocuments()) {
                String number = doc.getString(SweeperConfig.FIELD_NUMBER);
                Long timestamp = doc.getLong(SweeperConfig.FIELD_TIMESTAMP);
                String key = (number != null ? number : "") + ":" + (timestamp != null ? timestamp : 0);
                String existing = seen.get(key);
                if (existing != null) {
                    toDelete.add(doc.getId());
                } else {
                    seen.put(key, doc.getId());
                }
            }
            if (!toDelete.isEmpty()) {
                WriteBatch batch = db.batch();
                for (String id : toDelete) {
                    batch.delete(collection(SweeperConfig.COLLECTION_CALL_LOGS).document(id));
                }
                Tasks.await(batch.commit());
                Log.i(TAG, "Dedup: deleted " + toDelete.size() + " duplicate entries, kept " + seen.size());
            } else {
                Log.i(TAG, "Dedup: no duplicates found");
            }
            return toDelete.size();
        } catch (Exception e) {
            Log.e(TAG, "Dedup failed", e);
            return -1;
        }
    }
}
