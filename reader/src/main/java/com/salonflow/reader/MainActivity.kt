package com.salonflow.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SweeperViewerApp() }
    }
}

private val Brand = Color(0xFF274C43)
private val Accent = Color(0xFFD45B43)
private val Paper = Color(0xFFF7F6F1)
private val Ink = Color(0xFF18211F)
private val Muted = Color(0xFF687470)
private val Danger = Color(0xFFA13F32)
private val Green = Color(0xFF4CAF50)
private val Orange = Color(0xFFFF9800)

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

@Composable
fun SweeperViewerApp() {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Brand,
            secondary = Accent,
            background = Paper,
            surface = Color.White,
            error = Danger
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Paper) {
            var screen by remember { mutableStateOf("devices") }
            var selectedDevice by remember { mutableStateOf("") }
            var selectedTab by remember { mutableStateOf(0) }

            when (screen) {
                "devices" -> DeviceListScreen(onDevice = { id ->
                    selectedDevice = id; selectedTab = 0; screen = "detail"
                })
                "detail" -> DetailScreen(
                    deviceId = selectedDevice,
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    onBack = { screen = "devices" }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(onDevice: (String) -> Unit) {
    val reader = remember { FirestoreReader() }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            withContext(Dispatchers.IO) {
                Tasks.await(FirebaseAuth.getInstance().signInAnonymously())
            }
            val result = reader.loadDevices()
            withContext(Dispatchers.Main) {
                devices = result
                loading = false
                refreshing = false
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                error = e.message ?: "Failed to load devices"
                loading = false
                refreshing = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sweeper Viewer", fontWeight = FontWeight.Bold) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            refreshing = true
                            scope.launch { load() }
                        }) {
                            Text("Refresh", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White)
            )
        }
    ) { pad ->
        Column(modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand)
                }
                return@Column
            }
            if (error.isNotEmpty()) {
                Text(error, color = Danger, modifier = Modifier.padding(bottom = 8.dp))
            }
            Text(
                "Select a device",
                fontSize = 14.sp, color = Muted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices) { device ->
                    DeviceCard(device, onClick = { onDevice(device.deviceId) })
                }
                if (devices.isEmpty() && !loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text("No devices found", color = Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: DeviceInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brand),
                contentAlignment = Alignment.Center
            ) { Text("D", color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.deviceName,
                    fontWeight = FontWeight.SemiBold, color = Ink
                )
                Text(
                    if (device.lastActivity > 0) "Active: ${dateFmt.format(Date(device.lastActivity))}"
                    else "No data yet",
                    fontSize = 12.sp, color = Muted
                )
            }
            Text("›", fontSize = 20.sp, color = Muted)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    deviceId: String,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    val reader = remember { FirestoreReader() }
    var deviceName by remember { mutableStateOf("") }
    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var whatsAppEntries by remember { mutableStateOf<List<WhatsAppEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            withContext(Dispatchers.IO) {
                val devices = reader.loadDevices()
                val dev = devices.find { it.deviceId == deviceId }
                deviceName = dev?.deviceName ?: ""
                callLogs = reader.loadCallLogs(deviceId)
                whatsAppEntries = reader.loadWhatsAppEntries(deviceId)
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load data"
        }
        loading = false
        refreshing = false
    }

    LaunchedEffect(deviceId) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(deviceName.ifEmpty { "Device" }, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            refreshing = true
                            scope.launch { load() }
                        }) {
                            Text("Refresh", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White)
            )
        },
        bottomBar = {
            TabRow(
                modifier = Modifier.navigationBarsPadding(),
                selectedTabIndex = selectedTab,
                containerColor = Color.White,
                contentColor = Brand
            ) {
                listOf("Call Logs (${callLogs.size})", "WhatsApp (${whatsAppEntries.size})").forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { onTabChange(i) }, text = {
                        Text(label, fontSize = 12.sp, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal)
                    })
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Brand)
                }
            } else if (error.isNotEmpty()) {
                Text(error, color = Danger, modifier = Modifier.padding(16.dp))
            } else {
                Box(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    when (selectedTab) {
                        0 -> CallLogList(callLogs)
                        1 -> WhatsAppList(whatsAppEntries)
                    }
                }
            }
        }
    }
}

@Composable
fun CallLogList(entries: List<CallLogEntry>) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No call logs", color = Muted)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries) { e ->
            val typeColor = when (e.type) {
                "incoming" -> Green
                "outgoing" -> Brand
                "missed" -> Danger
                "rejected" -> Orange
                else -> Muted
            }
            val typeIcon = when (e.type) {
                "incoming" -> "↓"
                "outgoing" -> "↑"
                "missed" -> "✕"
                "rejected" -> "⊘"
                else -> "?"
            }
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(typeIcon, color = typeColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (e.name.isNotEmpty()) e.name else e.number,
                            fontWeight = FontWeight.SemiBold, color = Ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (e.name.isNotEmpty() && e.number.isNotEmpty()) {
                            Text(e.number, fontSize = 11.sp, color = Muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDuration(e.durationSec), fontSize = 13.sp, color = typeColor, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (e.timestamp > 0) dateFmt.format(Date(e.timestamp)) else "",
                            fontSize = 10.sp, color = Muted
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WhatsAppList(entries: List<WhatsAppEntry>) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No WhatsApp entries", color = Muted)
        }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(entries) { e ->
            when (e.type) {
                "image" -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp)
                                    .background(Color(0xFFF0F0F0), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) { Text("📷", fontSize = 18.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Photo", fontWeight = FontWeight.Medium, color = Ink)
                                Text("Tap to view", fontSize = 11.sp, color = Muted)
                            }
                            if (e.timestamp > 0) {
                                Text(dateFmt.format(Date(e.timestamp)),
                                    fontSize = 10.sp, color = Muted)
                            }
                        }
                    }
                }
                "call" -> {
                    val callType = e.content
                    val typeColor = when (callType) {
                        "incoming" -> Green
                        "outgoing" -> Brand
                        "missed" -> Danger
                        else -> Muted
                    }
                    val typeIcon = when (callType) {
                        "incoming" -> "↓"
                        "outgoing" -> "↑"
                        "missed" -> "✕"
                        else -> "?"
                    }
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = typeIcon, color = typeColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                e.sender.ifEmpty { "Unknown" },
                                Modifier.weight(1f),
                                fontWeight = FontWeight.SemiBold, color = Ink
                            )
                            if (e.timestamp > 0) {
                                Text(dateFmt.format(Date(e.timestamp)),
                                    fontSize = 10.sp, color = Muted)
                            }
                        }
                    }
                }
                "message" -> {
                    val senderLabel = if (e.sender == "You") "You" else e.sender.ifEmpty { "Unknown" }
                    val isYou = e.sender == "You"
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isYou) Color(0xFFE3F2FD) else Color.White
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (e.isGroup) {
                                    Text("G", color = Orange, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    senderLabel,
                                    fontWeight = FontWeight.SemiBold, color = if (isYou) Brand else Ink,
                                    fontSize = 14.sp
                                )
                                Spacer(Modifier.weight(1f))
                                if (e.timestamp > 0) {
                                    Text(dateFmt.format(Date(e.timestamp)),
                                        fontSize = 10.sp, color = Muted)
                                }
                            }
                            if (e.content.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(e.content, fontSize = 13.sp, color = Ink, maxLines = 5,
                                    overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val m = sec / 60
    val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}