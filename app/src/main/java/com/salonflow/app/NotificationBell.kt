package com.salonflow.app

import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object NotificationBellFactory {
    @JvmStatic
    fun create(context: android.content.Context): View {
        return ComposeView(context).apply {
            setContent {
                SalonFlowTheme {
                    NotificationBell()
                }
            }
        }
    }
}

@Composable
private fun NotificationBell() {
    val context = LocalContext.current
    val repo = remember { NotificationRepository.getInstance(context) }
    var unreadCount by remember { mutableStateOf(repo.unreadCount()) }
    var showDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable {
                showDialog = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = "Notifications",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Danger),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (unreadCount > 99) "99+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (showDialog) {
        NotificationDialog(
            repo = repo,
            onDismiss = {
                showDialog = false
                unreadCount = repo.unreadCount()
            },
        )
    }
}

@Composable
private fun NotificationDialog(
    repo: NotificationRepository,
    onDismiss: () -> Unit,
) {
    var notifications by remember { mutableStateOf(repo.findAll()) }
    var showArchived by remember { mutableStateOf(false) }

    val unread = notifications.filter { !it.read }
    val displayList = if (showArchived) notifications else unread
    val hasMore = unread.size > 10

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Notifications",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                )
                Spacer(Modifier.size(12.dp))

                if (notifications.isEmpty()) {
                    Text(
                        "You have read it all...",
                        color = Muted,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    val items = if (showArchived || !hasMore) displayList
                                else displayList.take(10)

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items, key = { it.id }) { notification ->
                            SwipeableNotificationItem(
                                notification = notification,
                                onMarkRead = {
                                    repo.markAsRead(notification.id)
                                    notifications = repo.findAll()
                                },
                            )
                        }
                    }

                    if (!showArchived && hasMore) {
                        TextButton(
                            onClick = { showArchived = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("See previous notifications", color = Brand)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeableNotificationItem(
    notification: NotificationEntry,
    onMarkRead: () -> Unit,
) {
    var offsetX by remember { mutableStateOf(0f) }
    val threshold = 150f

    val indicatorColor = when (notification.type) {
        "backup_success" -> Green
        "backup_failure" -> Danger
        "overdue" -> Orange
        "morning_bookings" -> Brand
        else -> Muted
    }

    val bgAlpha = if (notification.read) 0.6f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = {
                        if (abs(offsetX) > threshold) {
                            onMarkRead()
                        }
                        offsetX = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(-threshold * 2, threshold * 2)
                    },
                )
            }
            .background(
                if (notification.read) Cell else Color.White,
                RoundedCornerShape(8.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = bgAlpha)),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    notification.title,
                    fontSize = 14.sp,
                    fontWeight = if (notification.read) FontWeight.Normal else FontWeight.Bold,
                    color = Ink.copy(alpha = bgAlpha),
                )
                Text(
                    notification.message,
                    fontSize = 12.sp,
                    color = Muted.copy(alpha = bgAlpha),
                )
            }
            Text(
                formatTimestamp(notification.timestamp),
                fontSize = 10.sp,
                color = Muted.copy(alpha = bgAlpha),
            )
        }
    }
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
    return fmt.format(Date(millis))
}
