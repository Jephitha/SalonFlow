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
    val timestamp: Long = 0,
    val deleted: Boolean = false
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val lastActivity: Long,
    val callLogCount: Int
)

class FirestoreReader {
    private val db = FirebaseFirestore.getInstance()

    suspend fun loadDevices(): List<DeviceInfo> {
        Log.i("FirestoreReader", "Loading devices from Firestore")
        val deviceIds = mutableSetOf<String>()
        val collections = listOf("callLogs")
        for (col in collections) {
            try {
                val snap = db.collection(col).get().await()
                for (doc in snap.documents) {
                    deviceIds.add(doc.id)
                }
            } catch (e: Exception) {
                Log.e("FirestoreReader", "Failed to read collection '$col': ${e.message}", e)
            }
        }

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
                callLogCount = 0
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
                    timestamp = doc.getLong("timestamp") ?: 0,
                    deleted = doc.getBoolean("deleted") ?: false
                )
            }
        } catch (e: Exception) {
            Log.e("FirestoreReader", "Failed to load call logs: ${e.message}", e)
            return emptyList()
        }
    }
}
