package com.salonflow.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SweeperReaderApp() }
    }
}

private val Brand = Color(0xFF192E45)
private val BrandDark = Color(0xFF0F1C2B)
private val Accent = Color(0xFFFFD524)
private val Paper = Color(0xFFF7F6F1)
private val SurfaceDark = Color(0xFF1E1E1E)
private val Ink = Color(0xFF18211F)
private val InkDark = Color(0xFFE0E0E0)
private val Muted = Color(0xFF687470)
private val MutedDark = Color(0xFF9E9E9E)
private val Danger = Color(0xFFA13F32)
private val Green = Color(0xFF4CAF50)
private val Orange = Color(0xFFFFD524)
private val Teal = Color(0xFF009688)

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
private const val PREFS_NAME = "reader_prefs"
private const val KEY_PIN_HASH = "pin_hash"
private const val KEY_HIDDEN_DEVICES = "hidden_devices"
private const val KEY_EXCLUDED_STATS = "excluded_stats_numbers"

private fun deviceSalt(context: Context): String {
    val id = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    return id ?: "reader_default_salt"
}

private fun hashPin(pin: String, salt: String): String {
    val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), 10000, 256)
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val hash = factory.generateSecret(spec).encoded
    return hash.joinToString("") { "%02x".format(it) }
}

data class ContactStats(
    val number: String,
    val name: String,
    val incoming: Int = 0,
    val outgoing: Int = 0,
    val missed: Int = 0,
    val rejected: Int = 0
) {
    val total: Int get() = incoming + outgoing
    val failed: Int get() = missed + rejected
}

@Composable
fun SweeperReaderApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val salt = remember { deviceSalt(context) }
    val savedHash = remember { prefs.getString(KEY_PIN_HASH, null) }

    var screen by remember { mutableStateOf(if (savedHash == null) "pin_setup" else "pin_entry") }

    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF121212) else Paper
    val surface = if (isDark) SurfaceDark else Color.White
    val ink = if (isDark) InkDark else Ink
    val muted = if (isDark) MutedDark else Muted

    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme(
            primary = Brand, secondary = Accent,
            background = Color(0xFF121212), surface = SurfaceDark,
            error = Danger, onPrimary = Color.White, onSecondary = Color.White,
            onBackground = InkDark, onSurface = InkDark
        ) else lightColorScheme(
            primary = Brand, secondary = Accent,
            background = Paper, surface = Color.White, error = Danger
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            when (screen) {
                "pin_setup" -> PinSetupScreen(salt = salt, onPinSet = { hash ->
                    prefs.edit().putString(KEY_PIN_HASH, hash).apply()
                    screen = "main"
                })
                "pin_entry" -> PinEntryScreen(savedHash = savedHash ?: "", salt = salt, onVerified = { screen = "main" })
                "main" -> MainScreen(prefs = prefs, bg = bg, surface = surface, ink = ink, muted = muted)
            }
        }
    }
}

@Composable
fun PinSetupScreen(salt: String, onPinSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Set PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Create a 6-digit PIN to protect the reader", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; error = "" } },
            label = { Text("Enter PIN") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = confirm, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { confirm = it; error = "" } },
            label = { Text("Confirm PIN") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(error, color = Danger, fontSize = 13.sp) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (pin.length != 6) error = "PIN must be 6 digits"
            else if (pin != confirm) error = "PINs do not match"
            else onPinSet(hashPin(pin, salt))
        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("Set PIN", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PinEntryScreen(savedHash: String, salt: String, onVerified: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Weather Reader", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Enter PIN to unlock", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = pin, onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; error = "" } },
            label = { Text("PIN") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (pin.length == 6) { if (hashPin(pin, salt) == savedHash) onVerified(); else { error = "Wrong PIN"; pin = "" } }
                else error = "Enter 6 digits"
            }), singleLine = true, modifier = Modifier.fillMaxWidth())
        if (error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(error, color = Danger, fontSize = 13.sp) }
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (pin.length == 6) { if (hashPin(pin, salt) == savedHash) onVerified(); else { error = "Wrong PIN"; pin = "" } }
            else error = "Enter 6 digits"
        }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)) { Text("Unlock", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun MainScreen(prefs: android.content.SharedPreferences, bg: Color, surface: Color, ink: Color, muted: Color) {
    var screen by remember { mutableStateOf("devices") }
    var selectedDevice by remember { mutableStateOf("") }
    when (screen) {
        "devices" -> DeviceListScreen(prefs = prefs, surface = surface, ink = ink, muted = muted, onDevice = { id ->
            selectedDevice = id; screen = "detail"
        })
        "detail" -> DetailScreen(deviceId = selectedDevice, prefs = prefs, onBack = { screen = "devices" }, surface = surface, ink = ink, muted = muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(prefs: android.content.SharedPreferences, surface: Color, ink: Color, muted: Color, onDevice: (String) -> Unit) {
    val reader = remember { FirestoreReader() }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val hiddenSet = remember { mutableStateOf(prefs.getStringSet(KEY_HIDDEN_DEVICES, emptySet()) ?: emptySet()) }

    suspend fun load() {
        try {
            withContext(Dispatchers.IO) { Tasks.await(FirebaseAuth.getInstance().signInAnonymously()) }
            val result = reader.loadDevices()
            withContext(Dispatchers.Main) { devices = result; loading = false; refreshing = false }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) { error = e.message ?: "Failed to load devices"; loading = false; refreshing = false }
        }
    }

    LaunchedEffect(Unit) { load() }

    val filteredDevices = devices.filter { it.deviceId !in hiddenSet.value }.distinctBy { it.deviceId }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Sweeper Viewer", fontWeight = FontWeight.Bold) }, actions = {
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 8.dp), color = Color.White, strokeWidth = 2.dp)
            else TextButton(onClick = { refreshing = true; scope.launch { load() } }) { Text("Refresh", color = Color.White, fontWeight = FontWeight.Bold) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = Color.White))
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(16.dp)) {
            if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) }; return@Column }
            if (error.isNotEmpty()) Text(error, color = Danger, modifier = Modifier.padding(bottom = 8.dp))
            Text("Select a device", fontSize = 14.sp, color = muted, modifier = Modifier.padding(bottom = 12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredDevices) { device ->
                    DeviceCard(device = device, surface = surface, ink = ink, muted = muted, onClick = { onDevice(device.deviceId) }, onHide = {
                        val updated = hiddenSet.value.toMutableSet().also { it.add(device.deviceId) }
                        hiddenSet.value = updated; prefs.edit().putStringSet(KEY_HIDDEN_DEVICES, updated).apply()
                    })
                }
                if (filteredDevices.isEmpty() && !loading) item { Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { Text("No devices found", color = muted) } }
            }
        }
    }
}

@Composable
fun DeviceCard(device: DeviceInfo, surface: Color, ink: Color, muted: Color, onClick: () -> Unit, onHide: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brand), contentAlignment = Alignment.Center) { Text("D", color = surface, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(device.deviceName, fontWeight = FontWeight.SemiBold, color = ink)
                Text(if (device.lastActivity > 0) "Active: ${dateFmt.format(Date(device.lastActivity))}" else "No data yet", fontSize = 12.sp, color = muted)
            }
            TextButton(onClick = onHide) { Text("Hide", color = Danger, fontWeight = FontWeight.Medium, fontSize = 13.sp) }
            Text("›", fontSize = 20.sp, color = muted, modifier = Modifier.clickable(onClick = onClick).padding(end = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(deviceId: String, prefs: android.content.SharedPreferences, onBack: () -> Unit, surface: Color, ink: Color, muted: Color) {
    val reader = remember { FirestoreReader() }
    var deviceName by remember { mutableStateOf("") }
    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
    var sweepStatus by remember { mutableStateOf<SweepStatus?>(null) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            withContext(Dispatchers.IO) {
                val dev = reader.loadDevices().find { it.deviceId == deviceId }
                deviceName = dev?.deviceName ?: ""
                callLogs = reader.loadCallLogs(deviceId)
                sweepStatus = reader.loadSweepStatus(deviceId)
            }
        } catch (e: Exception) { error = e.message ?: "Failed to load data" }
        loading = false; refreshing = false
    }

    LaunchedEffect(deviceId) { load() }

    val topTextColor = if (isSystemInDarkTheme()) Color.White else surface

    Scaffold(topBar = {
        TopAppBar(title = { Text(deviceName.ifEmpty { "Device" }, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("← Back", color = topTextColor, fontWeight = FontWeight.Bold) } },
            actions = {
                if (refreshing) CircularProgressIndicator(Modifier.size(20.dp).padding(end = 8.dp), color = topTextColor, strokeWidth = 2.dp)
                else TextButton(onClick = { refreshing = true; scope.launch { load() } }) { Text("Refresh", color = topTextColor, fontWeight = FontWeight.Bold) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = topTextColor))
    }, bottomBar = {
        Column {
            sweepStatus?.let { status ->
                val sweepTypeLabel = when (status.lastSweepType) {
                    "regular" -> "Regular"
                    "full" -> "Full"
                    "saturday" -> "Saturday"
                    else -> status.lastSweepType.replaceFirstChar { it.uppercase() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().background(if (isSystemInDarkTheme()) Color(0xFF1A1A1A) else Color(0xFFF0EFEA)).padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Last sweep:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = muted)
                    Spacer(Modifier.width(4.dp))
                    if (status.lastSweepType.isNotEmpty()) {
                        Text(sweepTypeLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (status.lastSweepTimestamp > 0) {
                        Text(dateFmt.format(Date(status.lastSweepTimestamp)), fontSize = 11.sp, color = muted)
                    } else {
                        Text("never", fontSize = 11.sp, color = muted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    }
                }
            }
            TabRow(selectedTabIndex = selectedTab, containerColor = surface, contentColor = Brand, modifier = Modifier.navigationBarsPadding()) {
                listOf("Call Logs (${callLogs.size})", "Stats").forEachIndexed { i, label ->
                    Tab(selected = selectedTab == i, onClick = { selectedTab = i }, text = { Text(label, fontSize = 12.sp, fontWeight = if (selectedTab == i) FontWeight.Bold else FontWeight.Normal) })
                }
            }
        }
    }) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (loading) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Brand) } }
            else if (error.isNotEmpty()) Text(error, color = Danger, modifier = Modifier.padding(16.dp))
            else when (selectedTab) {
                0 -> CallLogList(callLogs, surface, ink, muted)
                1 -> StatsScreen(callLogs, prefs, surface, ink, muted)
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Phone number", text))
    Toast.makeText(context, "Phone number copied to clipboard", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CallLogList(entries: List<CallLogEntry>, surface: Color, ink: Color, muted: Color) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var deletedOnly by remember { mutableStateOf(false) }

    if (entries.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No call logs", color = muted) }; return }

    val filtered = entries.filter { e ->
        val matchesSearch = searchQuery.isBlank() || e.number.contains(searchQuery, ignoreCase = true)
        val matchesDeleted = !deletedOnly || e.deleted
        matchesSearch && matchesDeleted
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search number...", color = muted) },
                singleLine = true,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
            )
            FilterChip(
                selected = deletedOnly,
                onClick = { deletedOnly = !deletedOnly },
                label = { Text("Deleted", fontSize = 12.sp) },
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (filtered.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text(if (searchQuery.isNotBlank()) "No matches" else "No call logs", color = muted) } }
            }
            items(filtered) { e ->
                val typeColor = when (e.type) { "incoming" -> Green; "outgoing" -> Brand; "missed" -> Danger; "rejected" -> Orange; else -> muted }
                val typeIcon = when (e.type) { "incoming" -> "↓"; "outgoing" -> "↑"; "missed" -> "✕"; "rejected" -> "⊘"; else -> "?" }
                Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = { copyToClipboard(context, e.number.ifEmpty { e.name }) },
                    )
                ) {
                    Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(typeIcon, color = typeColor, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (e.name.isNotEmpty()) e.name else e.number, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (e.deleted) {
                                    Spacer(Modifier.width(6.dp))
                                    Text("DELETED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Orange)
                                }
                            }
                            if (e.name.isNotEmpty() && e.number.isNotEmpty()) Text(e.number, fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatDuration(e.durationSec), fontSize = 13.sp, color = typeColor, fontWeight = FontWeight.SemiBold)
                            Text(if (e.timestamp > 0) dateFmt.format(Date(e.timestamp)) else "", fontSize = 10.sp, color = muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatsScreen(entries: List<CallLogEntry>, prefs: android.content.SharedPreferences, surface: Color, ink: Color, muted: Color) {
    var excludedSet by remember { mutableStateOf(prefs.getStringSet(KEY_EXCLUDED_STATS, emptySet()) ?: emptySet()) }
    val toggleExclude: (String, String) -> Unit = { number, name ->
        val key = "$number:$name"
        val updated = excludedSet.toMutableSet()
        if (excludedSet.any { it == number || it.startsWith("$number:") }) {
            updated.removeAll { it == number || it.startsWith("$number:") }
        } else {
            updated.add(key)
        }
        excludedSet = updated
        prefs.edit().putStringSet(KEY_EXCLUDED_STATS, updated).apply()
    }
    val isExcluded: (String) -> Boolean = { number -> excludedSet.any { it == number || it.startsWith("$number:") } }

    val statsMap = mutableMapOf<String, ContactStats>()
    for (e in entries) {
        if (isExcluded(e.number)) continue
        val key = if (e.number.isNotEmpty()) e.number else "unknown"
        val existing = statsMap.getOrPut(key) { ContactStats(number = key, name = e.name) }
        when (e.type) {
            "incoming" -> statsMap[key] = existing.copy(incoming = existing.incoming + 1)
            "outgoing" -> statsMap[key] = existing.copy(outgoing = existing.outgoing + 1)
            "missed" -> statsMap[key] = existing.copy(missed = existing.missed + 1)
            "rejected" -> statsMap[key] = existing.copy(rejected = existing.rejected + 1)
        }
        if (e.name.isNotEmpty() && existing.name.isEmpty()) statsMap[key] = statsMap[key]!!.copy(name = e.name)
    }
    val statsList = statsMap.values.toList()
    val mostContacted = statsList.filter { it.total > 0 }.sortedByDescending { it.total }
    val mostFailed = statsList.filter { it.failed > 0 }.sortedByDescending { it.failed }

    if (entries.isEmpty()) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No data for stats", color = muted) }; return }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Most Contacted", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink)
            Spacer(Modifier.height(4.dp))
        }
        items(mostContacted.take(20)) { stat ->
            StatsRow(stat = stat, isExcluded = false, onToggleExclude = { toggleExclude(stat.number, stat.name) }, surface = surface, ink = ink, muted = muted, statType = "contacted")
        }
        if (mostContacted.isEmpty()) item { Text("No contacts", color = muted, modifier = Modifier.padding(vertical = 4.dp)) }

        item { Spacer(Modifier.height(12.dp)); Text("Most Missed / Declined", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ink); Spacer(Modifier.height(4.dp)) }
        items(mostFailed.take(20)) { stat ->
            StatsRow(stat = stat, isExcluded = false, onToggleExclude = { toggleExclude(stat.number, stat.name) }, surface = surface, ink = ink, muted = muted, statType = "failed")
        }
        if (mostFailed.isEmpty()) item { Text("No missed or declined calls", color = muted, modifier = Modifier.padding(vertical = 4.dp)) }

        if (excludedSet.isNotEmpty()) {
            item { Spacer(Modifier.height(12.dp)); Text("Excluded Numbers (tap to restore)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Danger); Spacer(Modifier.height(4.dp)) }
            items(excludedSet.toList().sorted()) { entry ->
                val parts = entry.split(":", limit = 2)
                val number = parts[0]
                val name = if (parts.size > 1 && parts[1].isNotEmpty()) parts[1] else null
                Row(Modifier.fillMaxWidth().clickable { toggleExclude(number, name ?: "") }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (name != null) "$name ($number)" else number, fontSize = 13.sp, color = muted, modifier = Modifier.weight(1f))
                    Text("Restore", color = Brand, fontWeight = FontWeight.Medium, fontSize = 12.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatsRow(stat: ContactStats, isExcluded: Boolean, onToggleExclude: () -> Unit, surface: Color, ink: Color, muted: Color, statType: String) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.combinedClickable(
            onClick = { },
            onLongClick = { copyToClipboard(context, stat.number.ifEmpty { stat.name }) },
        )
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stat.name.ifEmpty { stat.number }, fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (stat.name.isNotEmpty()) Text(stat.number, fontSize = 11.sp, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                if (statType == "contacted") "${stat.total} calls" else "${stat.failed} missed/declined",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (statType == "failed" && stat.failed > 0) Danger else Brand
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onToggleExclude, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Exclude", color = muted, fontSize = 11.sp)
            }
        }
    }
}

private fun formatDuration(sec: Long): String {
    if (sec <= 0) return ""
    val m = sec / 60; val s = sec % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
