package com.salonflow.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class NotificationRepository private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "salonflow_notifications"
        private const val KEY_NOTIFICATIONS = "notifications"

        @Volatile
        private var instance: NotificationRepository? = null

        @JvmStatic
        fun getInstance(context: Context): NotificationRepository {
            return instance ?: synchronized(this) {
                instance ?: NotificationRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    fun findAll(): MutableList<NotificationEntry> {
        val list = mutableListOf<NotificationEntry>()
        val json = prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                NotificationEntry(
                    id = obj.getLong("id"),
                    type = obj.getString("type"),
                    title = obj.getString("title"),
                    message = obj.getString("message"),
                    timestamp = obj.getLong("timestamp"),
                    read = obj.optBoolean("read", false),
                )
            )
        }
        list.sortByDescending { it.timestamp }
        return list
    }

    fun add(type: String, title: String, message: String): Long {
        val list = findAll()
        val newId = (list.maxOfOrNull { it.id } ?: 0) + 1
        val entry = NotificationEntry(
            id = newId,
            type = type,
            title = title,
            message = message,
            timestamp = System.currentTimeMillis(),
            read = false,
        )
        list.add(0, entry)
        saveAll(list)
        return newId
    }

    fun markAsRead(id: Long) {
        val list = findAll()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(read = true)
            saveAll(list)
        }
    }

    fun clearAll() {
        prefs.edit().remove(KEY_NOTIFICATIONS).apply()
    }

    fun unreadCount(): Int {
        val json = prefs.getString(KEY_NOTIFICATIONS, "[]") ?: "[]"
        val arr = JSONArray(json)
        var count = 0
        for (i in 0 until arr.length()) {
            if (!arr.getJSONObject(i).optBoolean("read", false)) {
                count++
            }
        }
        return count
    }

    private fun saveAll(list: List<NotificationEntry>) {
        val arr = JSONArray()
        for (entry in list) {
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("type", entry.type)
            obj.put("title", entry.title)
            obj.put("message", entry.message)
            obj.put("timestamp", entry.timestamp)
            obj.put("read", entry.read)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_NOTIFICATIONS, arr.toString()).apply()
    }
}
