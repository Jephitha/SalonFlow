package com.salonflow.app

import android.view.View
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

object ClientsComposeFactory {
    @JvmStatic
    fun create(activity: MainActivity, database: SalonDatabase): View {
        return ComposeView(activity).apply {
            setContent {
                SalonFlowTheme {
                    ClientsScreen(
                        database = database,
                        onClientClick = { client -> activity.showClientOptions(client) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientsScreen(
    database: SalonDatabase,
    onClientClick: (SalonDatabase.Client) -> Unit,
) {
    val money = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply { maximumFractionDigits = 0 }
    var searchQuery by remember { mutableStateOf("") }
    val allClients = remember { database.clients() }
    val q = searchQuery.lowercase().trim()
    val filtered = remember(allClients, q) {
        if (q.isEmpty()) allClients
        else allClients.filter { it.name.lowercase().contains(q) || it.phone.lowercase().contains(q) }
    }
    val clientIds = remember(filtered) { filtered.map { it.id } }
    val allBookings = remember(clientIds) { database.bookingsForClientIds(clientIds) }
    val allSales = remember(clientIds) { database.salesForClientIds(clientIds) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Clients",
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Add customers, create sales or bookings, and track balances.",
                color = Muted,
                fontSize = 13.sp,
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search by name or phone...", fontSize = 14.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Brand,
                    cursorColor = Brand,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
            )

            if (filtered.isEmpty()) {
                Text(
                    text = if (q.isEmpty()) "No clients yet." else "No clients matching \"$searchQuery\"",
                    color = Muted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filtered, key = { it.id }) { client ->
                        val bookings = allBookings[client.id] ?: emptyList()
                        val sales = allSales[client.id] ?: emptyList()
                        val bal = SalonUtils.outstandingBalance(bookings, sales)
                        val visits = bookings.size + sales.size

                        ClientCard(
                            client = client,
                            visits = visits,
                            balance = bal,
                            money = money,
                            onClick = { onClientClick(client) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientCard(
    client: SalonDatabase.Client,
    visits: Int,
    balance: Double,
    money: NumberFormat,
    onClick: () -> Unit,
) {
    val balanceColor = if (balance > 0) Danger else Brand

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = client.name,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                val sub = if (client.phone.isEmpty()) "No phone" else client.phone
                Text(
                    text = "$sub • $visits visits",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = fmt(money, balance),
                color = balanceColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun fmt(money: NumberFormat, v: Double): String = money.format(v / 100.0)
