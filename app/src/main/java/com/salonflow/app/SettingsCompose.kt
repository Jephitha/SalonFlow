package com.salonflow.app

import android.view.View
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SettingsComposeFactory {
    @JvmStatic
    fun create(activity: MainActivity, database: SalonDatabase, settings: AppSettings, initialSection: String): View {
        return ComposeView(activity).apply {
            setContent {
                SalonFlowTheme {
                    SettingsScreen(
                        activity = activity,
                        database = database,
                        settings = settings,
                        initialSection = initialSection,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    activity: MainActivity,
    database: SalonDatabase,
    settings: AppSettings,
    initialSection: String,
) {
    var currentSection by remember { mutableStateOf(initialSection) }

    BackHandler(enabled = currentSection != "home") {
        currentSection = activity.popSettingsSection()
    }

    LaunchedEffect(currentSection) {
        activity.updateFabForSettingsSection(currentSection)
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (currentSection != "home") {
                    Text(
                        text = "\u2190",
                        color = Brand,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            currentSection = activity.popSettingsSection()
                        },
                    )
                }
                val title = when (currentSection) {
                    "home" -> "Settings"
                    "services" -> "Services"
                    "inventory" -> "Inventory"
                    "stylists" -> "Stylists"
                    "security" -> "Security"
                    "notifications" -> "Notifications"
                    "backup" -> "Backup & Restore"
                    else -> "Settings"
                }
                Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (currentSection) {
                "home" -> SettingsHub(
                    onNavigate = { section ->
                        activity.pushSettingsSection(currentSection)
                        currentSection = section
                    },
                )
                "stylists" -> StylistsScreen(
                    database = database,
                    onAdd = { activity.showStylistDialog(null) },
                    onEdit = { activity.showStylistDialog(it) },
                )
                "services" -> ServicesScreen(
                    database = database,
                    onAdd = { activity.showServiceDialog(null) },
                    onEdit = { activity.showServiceDialog(it) },
                )
                "inventory" -> InventoryScreen(
                    database = database,
                    onAdd = { activity.showInventoryDialog(null) },
                    onEdit = { activity.showInventoryDialog(it) },
                )
                "security" -> SecurityScreen(
                    activity = activity,
                    settings = settings,
                    onCreatePin = { activity.showSetPinDialog() },
                    onToggleSettingsPin = {
                        if (settings.isSettingsPinRequired()) {
                            activity.promptForSettingsPinAndThen {
                                settings.setSettingsPinRequired(false)
                                activity.rerender()
                            }
                        } else {
                            settings.setSettingsPinRequired(true)
                            activity.rerender()
                        }
                    },
                    onToggleAppLock = {
                        if (settings.isAppLockEnabled()) {
                            activity.promptForSettingsPinAndThen {
                                settings.setAppLockEnabled(false)
                                activity.rerender()
                            }
                        } else {
                            settings.setAppLockEnabled(true)
                            activity.rerender()
                        }
                    },
                )
                "notifications" -> NotificationsScreen(
                    settings = settings,
                    onToggleMorning = {
                        settings.setNotifyMorningBookings(!settings.notifyMorningBookings())
                        activity.scheduleDailyReminders()
                        activity.rerender()
                    },
                    onToggleOverdue = {
                        settings.setNotifyOverdueSevenDays(!settings.notifyOverdueSevenDays())
                        activity.scheduleDailyReminders()
                        activity.rerender()
                    },
                )
                "backup" -> BackupScreen(
                    activity = activity,
                    database = database,
                )
            }
        }
    }
    }
}

@Composable
private fun SettingsHub(onNavigate: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Admin controls", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        SettingsRow("Services", "Manage salon service catalog", onClick = { onNavigate("services") })
        SettingsRow("Inventory", "Track products and reorder points", onClick = { onNavigate("inventory") })
        SettingsRow("Stylists", "Manage team members", onClick = { onNavigate("stylists") })
        SettingsRow("Security", "Configure app lock and PIN", onClick = { onNavigate("security") })
        SettingsRow("Notifications", "Morning and overdue alerts", onClick = { onNavigate("notifications") })
        SettingsRow("Backup & Restore", "Local backup tools", onClick = { onNavigate("backup") })
        Text(
            "Version 0.0.1",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            modifier = Modifier.padding(top = 10.dp, bottom = 14.dp).align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

@Composable
private fun StylistsScreen(
    database: SalonDatabase,
    onAdd: () -> Unit,
    onEdit: (SalonDatabase.Stylist) -> Unit,
) {
    val stylists = remember { database.stylists() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (stylists.isEmpty()) {
                Text("No stylists yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(stylists, key = { it.id }) { stylist ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEdit(stylist) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(stylist.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(if (stylist.phone.isEmpty()) "Phone not set" else stylist.phone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicesScreen(
    database: SalonDatabase,
    onAdd: () -> Unit,
    onEdit: (SalonDatabase.Service) -> Unit,
) {
    val services = remember { database.services() }
    val money = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply { maximumFractionDigits = 0 }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (services.isEmpty()) {
                Text("No services yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(services, key = { it.id }) { service ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEdit(service) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(service.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${fmt(money, service.price)} \u2022 commission ${service.commission.toInt()}%",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryScreen(
    database: SalonDatabase,
    onAdd: () -> Unit,
    onEdit: (SalonDatabase.InventoryItem) -> Unit,
) {
    val items = remember { database.inventory() }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (items.isEmpty()) {
                Text("No inventory items yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.id }) { item ->
                        val status = if (item.onHand <= item.reorderAt) "Reorder" else "Healthy"
                        val statusColor = if (item.onHand <= item.reorderAt) Danger else Brand
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { onEdit(item) },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(0.6f)) {
                                    Text(item.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(item.category, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                                Text("${item.onHand} left", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.3f))
                                Text(status, color = statusColor, fontSize = 14.sp, modifier = Modifier.weight(0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecurityScreen(
    activity: MainActivity,
    settings: AppSettings,
    onCreatePin: () -> Unit,
    onToggleSettingsPin: () -> Unit,
    onToggleAppLock: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!settings.hasPin()) {
            Text("No PIN set. Create one to secure the app.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Button(
                onClick = onCreatePin,
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Create PIN") }
        } else {
            SecurityToggleRow("Require PIN for Settings", settings.isSettingsPinRequired(), onToggleSettingsPin)
            SecurityToggleRow("App Lock", settings.isAppLockEnabled(), onToggleAppLock)
            Button(
                onClick = { activity.showSetPinDialog() },
                colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = Brand),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Change PIN") }
        }
    }
}

@Composable
private fun SecurityToggleRow(label: String, checked: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = checked,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(checkedTrackColor = Brand),
            )
        }
    }
}

@Composable
private fun NotificationsScreen(
    settings: AppSettings,
    onToggleMorning: () -> Unit,
    onToggleOverdue: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SecurityToggleRow("Morning booking reminders", settings.notifyMorningBookings(), onToggleMorning)
        SecurityToggleRow("7-day overdue alerts", settings.notifyOverdueSevenDays(), onToggleOverdue)
    }
}

@Composable
private fun BackupScreen(
    activity: MainActivity,
    database: SalonDatabase,
) {
    val settings = remember { activity.getAppSettings() }
    val driveManager = remember { activity.getDriveBackupManager() }
    var driveSignedIn by remember { mutableStateOf(driveManager.isSignedIn()) }
    var driveEmail by remember { mutableStateOf(driveManager.getSignedInEmail()) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf("") }
    var refreshTick by remember { mutableStateOf(0L) }

    val latest = remember(refreshTick) { database.latestBackup() }
    val lastBackup = if (latest == null) "Never"
                     else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(latest.lastModified()))
    val lastDriveBackup = settings.getLastDriveBackupTime()
    val lastDriveStr = if (lastDriveBackup == 0L) "Never"
                       else SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(lastDriveBackup))

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinDialog = false
                pinInput = ""
                pinError = ""
            },
            title = { Text("Backup data") },
            text = {
                Column {
                    Text("Enter your PIN to encrypt the backup.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = "" },
                        label = { Text("PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { /* confirm button handles it */ }),
                        singleLine = true,
                        isError = pinError.isNotEmpty(),
                        supportingText = if (pinError.isNotEmpty()) {{ Text(pinError, color = Danger) }} else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!settings.checkPin(pinInput.trim())) {
                        pinError = "Wrong PIN"
                        return@TextButton
                    }
                    showPinDialog = false
                    val enteredPin = pinInput.trim()
                    pinInput = ""
                    pinError = ""
                    settings.cacheDriveKey(enteredPin)
                    activity.backupNow()
                    refreshTick = System.currentTimeMillis()
                }) { Text("Backup") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinDialog = false
                    pinInput = ""
                    pinError = ""
                }) { Text("Cancel") }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Local Backup", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Last backup: $lastBackup", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (settings.hasPin()) {
                        pinInput = ""
                        pinError = ""
                        showPinDialog = true
                    } else {
                        Toast.makeText(activity, "Set a PIN in Security settings first", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                modifier = Modifier.weight(1f),
            ) { Text("Backup data") }
            Button(
                onClick = { activity.restoreBackup() },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                modifier = Modifier.weight(1f),
            ) { Text("Restore data") }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
        Spacer(modifier = Modifier.height(8.dp))

        Text("Google Drive", color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Last Drive upload: $lastDriveStr", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        if (driveSignedIn) {
            Text("Signed in: $driveEmail", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Button(
                onClick = {
                    activity.signOutOfDrive()
                    driveSignedIn = false
                    driveEmail = null
                },
                colors = ButtonDefaults.buttonColors(containerColor = Danger),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign out") }
        } else {
            Text("Not signed in", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Button(
                onClick = {
                    activity.signInToDrive(Runnable {
                        driveSignedIn = driveManager.isSignedIn()
                        driveEmail = driveManager.getSignedInEmail()
                    })
                },
                colors = ButtonDefaults.buttonColors(containerColor = Brand),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sign in to Google Drive") }
        }
    }
}

private fun fmt(money: NumberFormat, v: Double): String = money.format(v / 100.0)
