package com.salonflow.app

data class NotificationEntry(
    val id: Long,
    val type: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val read: Boolean = false,
)
