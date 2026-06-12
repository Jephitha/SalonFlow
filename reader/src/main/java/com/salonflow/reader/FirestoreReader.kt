package com.salonflow.reader

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

data class CallLogEntry(
    val deviceId: String = "",
    val number: String = "",
    val name: String = "",
    val type: String = "",
    val durationSec: Long = 0,
    val timestamp: Long = 0
)

data class WhatsAppEntry(
    val deviceId: String = "",
    val sender: String = "",
    val content: String = "",
    val type: String = "",
    val isGroup: Boolean = false,
    val imageData: String = "",
    val timestamp: Long = 0
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val lastActivity: Long,
    val callLogCount: Int,
    val messageCount: Int,
    val callCount: Int
)

class FirestoreReader {
    private val db = FirebaseFirestore.getInstance()

    suspend fun loadDevices(): List<DeviceInfo> {
        Log.i("FirestoreReader", "Loading devices from Firestore")
        val deviceIds = mutableSetOf<String>()
        val collections = listOf("callLogs", "whatsappMessages", "whatsappCalls", "whatsappImages")
        for (col in collections) {
            try {
                val snap = db.collection(col).get().await()
                Log.i("FirestoreReader", "Collection '$col': ${snap.documents.size} documents")
                for (doc in snap.documents) {
                    deviceIds.add(doc.id)
                }
            } catch (e: Exception) {
                Log.e("FirestoreReader", "Failed to read collection '$col': ${e.message}", e)
            }
        }
        Log.i("FirestoreReader", "Found ${deviceIds.size} unique devices")

        val result = mutableListOf<DeviceInfo>()
        for (id in deviceIds) {
            var lastTs = 0L
            for (col in collections) {
                try {
                    val snap = db.collection(col).document(id)
                        .collection("entries")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(1)
                        .get().await()
                    val ts = snap.documents.firstOrNull()?.getLong("timestamp") ?: 0
                    if (ts > lastTs) lastTs = ts
                } catch (e: Exception) {
                    Log.e("FirestoreReader", "Error reading entries for $id/$col: ${e.message}")
                }
            }
            val displayName = try {
                db.collection("callLogs").document(id).get().await().getString("deviceName") ?: id.take(8)
            } catch (e: Exception) {
                id.take(8)
            }
            result.add(DeviceInfo(
                deviceId = id,
                deviceName = displayName,
                lastActivity = lastTs,
                callLogCount = 0,
                messageCount = 0,
                callCount = 0
            ))
        }
        return result.sortedByDescending { it.lastActivity }
    }

    suspend fun loadCallLogs(deviceId: String, limit: Long = 200): List<CallLogEntry> {
        Log.i("FirestoreReader", "Loading call logs for device $deviceId")
        try {
            val snap = db.collection("callLogs").document(deviceId)
                .collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
            return snap.documents.map { doc ->
                CallLogEntry(
                    deviceId = doc.getString("deviceId") ?: "",
                    number = doc.getString("number") ?: "",
                    name = doc.getString("name") ?: "",
                    type = doc.getString("type") ?: "",
                    durationSec = doc.getLong("durationSec") ?: 0,
                    timestamp = doc.getLong("timestamp") ?: 0
                )
            }
        } catch (e: Exception) {
            Log.e("FirestoreReader", "Failed to load call logs: ${e.message}", e)
            return emptyList()
        }
    }

    suspend fun loadWhatsAppEntries(deviceId: String, limit: Long = 200): List<WhatsAppEntry> {
        Log.i("FirestoreReader", "Loading WhatsApp entries for device $deviceId")
        val result = mutableListOf<WhatsAppEntry>()
        try {
            val msgSnap = db.collection("whatsappMessages").document(deviceId)
                .collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
            for (doc in msgSnap.documents) {
                result.add(WhatsAppEntry(
                    deviceId = doc.getString("deviceId") ?: deviceId,
                    sender = doc.getString("sender") ?: "",
                    content = doc.getString("preview") ?: "",
                    type = "message",
                    isGroup = doc.getBoolean("isGroup") ?: false,
                    timestamp = doc.getLong("timestamp") ?: 0
                ))
            }
        } catch (e: Exception) {
            Log.e("FirestoreReader", "Failed to load messages: ${e.message}", e)
        }
        try {
            val callSnap = db.collection("whatsappCalls").document(deviceId)
                .collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
            for (doc in callSnap.documents) {
                result.add(WhatsAppEntry(
                    deviceId = doc.getString("deviceId") ?: deviceId,
                    sender = doc.getString("caller") ?: "",
                    content = doc.getString("type") ?: "call",
                    type = "call",
                    timestamp = doc.getLong("timestamp") ?: 0
                ))
            }
        } catch (e: Exception) {
            Log.e("FirestoreReader", "Failed to load WA calls: ${e.message}", e)
        }
        try {
            val imgSnap = db.collection("whatsappImages").document(deviceId)
                .collection("entries")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit)
                .get().await()
            for (doc in imgSnap.documents) {
                result.add(WhatsAppEntry(
                    deviceId = doc.getString("deviceId") ?: deviceId,
                    type = "image",
                    content = "📷 Image",
                    imageData = doc.getString("imageData") ?: "",
                    timestamp = doc.getLong("timestamp") ?: 0
                ))
            }
        } catch (e: Exception) {
            Log.e("FirestoreReader", "Failed to load WA images: ${e.message}", e)
        }
        result.sortByDescending { it.timestamp }
        return result
    }
}