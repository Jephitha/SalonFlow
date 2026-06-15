package com.salonflow.reader

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.isSystemInDarkTheme
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

private val Brand = Color(0xFF274C43)
private val BrandDark = Color(0xFF1D3932)
private val Accent = Color(0xFFD45B43)
private val Paper = Color(0xFFF7F6F1)
private val SurfaceDark = Color(0xFF1E1E1E)
private val Ink = Color(0xFF18211F)
private val InkDark = Color(0xFFE0E0E0)
private val Muted = Color(0xFF687470)
private val MutedDark = Color(0xFF9E9E9E)
private val Danger = Color(0xFFA13F32)
private val Green = Color(0xFF4CAF50)
private val Orange = Color(0xFFFF9800)

private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
private const val PREFS_NAME = "reader_prefs"
private const val KEY_PIN_HASH = "pin_hash"
private const val KEY_HIDDEN_DEVICES = "hidden_devices"

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
            primary = Brand,
            secondary = Accent,
            background = Color(0xFF121212),
            surface = SurfaceDark,
            error = Danger,
            onPrimary = Color.White,
            onSecondary = Color.White,
            onBackground = InkDark,
            onSurface = InkDark
        ) else lightColorScheme(
            primary = Brand,
            secondary = Accent,
            background = Paper,
            surface = Color.White,
            error = Danger
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = bg) {
            when (screen) {
                "pin_setup" -> PinSetupScreen(
                    salt = salt,
                    onPinSet = { hash ->
                        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
                        screen = "main"
                    }
                )
                "pin_entry" -> PinEntryScreen(
                    savedHash = savedHash ?: "",
                    salt = salt,
                    onVerified = { screen = "main" }
                )
                "main" -> MainScreen(
                    prefs = prefs,
                    bg = bg,
                    surface = surface,
                    ink = ink,
                    muted = muted
                )
            }
        }
    }
}

@Composable
fun PinSetupScreen(salt: String, onPinSet: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Set PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Create a 6-digit PIN to protect the reader", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; error = "" } },
            label = { Text("Enter PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) { confirm = it; error = "" } },
            label = { Text("Confirm PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (pin.length != 6) error = "PIN must be 6 digits"
                else if (pin != confirm) error = "PINs do not match"
                else onPinSet(hashPin(pin, salt))
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) { Text("Set PIN", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun PinEntryScreen(savedHash: String, salt: String, onVerified: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var attempts by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("SalonFlow Reader", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        Text("Enter PIN to unlock", fontSize = 14.sp, color = Muted)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) { pin = it; error = "" }
            },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = {
                if (pin.length == 6) {
                    if (hashPin(pin, salt) == savedHash) onVerified()
                    else { error = "Wrong PIN"; attempts++; pin = "" }
                } else error = "Enter 6 digits"
            }),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = Danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (pin.length == 6) {
                    if (hashPin(pin, salt) == savedHash) onVerified()
                    else { error = "Wrong PIN"; attempts++; pin = "" }
                } else error = "Enter 6 digits"
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Brand)
        ) { Text("Unlock", fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun MainScreen(
    prefs: android.content.SharedPreferences,
    bg: Color,
    surface: Color,
    ink: Color,
    muted: Color
) {
    var screen by remember { mutableStateOf("devices") }
    var selectedDevice by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    when (screen) {
        "devices" -> DeviceListScreen(
            prefs = prefs,
            surface = surface,
            ink = ink,
            muted = muted,
            onDevice = { id ->
                selectedDevice = id; selectedTab = 0; screen = "detail"
            }
        )
        "detail" -> DetailScreen(
            deviceId = selectedDevice,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            onBack = { screen = "devices" },
            surface = surface,
            ink = ink,
            muted = muted
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceListScreen(
    prefs: android.content.SharedPreferences,
    surface: Color,
    ink: Color,
    muted: Color,
    onDevice: (String) -> Unit
) {
    val reader = remember { FirestoreReader() }
    var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val hiddenSet = remember {
        mutableStateOf(prefs.getStringSet(KEY_HIDDEN_DEVICES, emptySet()) ?: emptySet())
    }

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

    val filteredDevices = devices.filter { it.deviceId !in hiddenSet.value }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sweeper Viewer", fontWeight = FontWeight.Bold) },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            color = surface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            refreshing = true
                            scope.launch { load() }
                        }) {
                            Text("Refresh", color = surface, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = surface)
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
                fontSize = 14.sp, color = muted,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredDevices) { device ->
                    DeviceCard(
                        device = device,
                        surface = surface,
                        ink = ink,
                        muted = muted,
                        isHidden = false,
                        onClick = { onDevice(device.deviceId) },
                        onToggleHide = {
                            val updated = hiddenSet.value.toMutableSet()
                            updated.add(device.deviceId)
                            hiddenSet.value = updated
                            prefs.edit().putStringSet(KEY_HIDDEN_DEVICES, updated).apply()
                        }
                    )
                }
                if (filteredDevices.isEmpty() && !loading) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                            Text("No devices found", color = muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: DeviceInfo,
    surface: Color,
    ink: Color,
    muted: Color,
    isHidden: Boolean,
    onClick: () -> Unit,
    onToggleHide: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brand),
                contentAlignment = Alignment.Center
            ) { Text("D", color = surface, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onClick)) {
                Text(device.deviceName, fontWeight = FontWeight.SemiBold, color = ink)
                Text(
                    if (device.lastActivity > 0) "Active: ${dateFmt.format(Date(device.lastActivity))}"
                    else "No data yet",
                    fontSize = 12.sp, color = muted
                )
            }
            TextButton(onClick = onToggleHide) {
                Text("Hide", color = Danger, fontWeight = FontWeight.Medium, fontSize = 13.sp)
            }
            Text("›", fontSize = 20.sp, color = muted, modifier = Modifier.clickable(onClick = onClick).padding(end = 8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    deviceId: String,
    selectedTab: Int,
    onTabChange: (Int) -> Unit,
    onBack: () -> Unit,
    surface: Color,
    ink: Color,
    muted: Color
) {
    val reader = remember { FirestoreReader() }
    var deviceName by remember { mutableStateOf("") }
    var callLogs by remember { mutableStateOf<List<CallLogEntry>>(emptyList()) }
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
                        Text("← Back", color = surface, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (refreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).padding(end = 8.dp),
                            color = surface,
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = {
                            refreshing = true
                            scope.launch { load() }
                        }) {
                            Text("Refresh", color = surface, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Brand, titleContentColor = surface)
            )
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
                    CallLogList(callLogs, surface, ink, muted)
                }
            }
        }
    }
}

@Composable
fun CallLogList(entries: List<CallLogEntry>, surface: Color, ink: Color, muted: Color) {
    if (entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No call logs", color = muted)
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
                else -> muted
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
                colors = CardDefaults.cardColors(containerColor = surface),
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
                            fontWeight = FontWeight.SemiBold, color = ink, maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (e.name.isNotEmpty() && e.number.isNotEmpty()) {
                            Text(e.number, fontSize = 11.sp, color = muted, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatDuration(e.durationSec), fontSize = 13.sp, color = typeColor, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (e.timestamp > 0) dateFmt.format(Date(e.timestamp)) else "",
                            fontSize = 10.sp, color = muted
                        )
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
