package com.salonflow.app

import android.content.Context
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object HomeComposeFactory {
    @JvmStatic
    fun create(context: Context, database: SalonDatabase): View {
        return ComposeView(context).apply {
            setContent {
                SalonFlowTheme {
                    HomeScreen(database = database)
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(database: SalonDatabase) {
    val money = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply {
        maximumFractionDigits = 0
    }
    var range by remember { mutableStateOf("today") }
    val end = SalonDatabase.today()
    val start = if (range == "today") end else daysAgo(6)
    val bookings = database.bookingsBetween(start, end)
    val sales = database.salesBetween(start, end)
    val expenses = database.expensesBetween(start, end)
    val todayBookings = database.bookingsForDate(SalonDatabase.today()).size
    val bookingsGross = SalonUtils.bookingsGross(bookings)
    val salesGross = SalonUtils.salesGross(sales)
    val collected = SalonUtils.collectedSales(bookings, sales)
    val expensesTotal = SalonUtils.sumExpenses(expenses)
    val commission = SalonUtils.commissionDue(bookings, sales)
    val clients = database.clients()
    val outstanding = SalonUtils.outstandingBalance(bookings, sales)
    val cogs = database.costOfGoodsSold(start, end)
    val stockPurchases = database.stockPurchasesBetween(start, end)
    val businessRevenue = collected - expensesTotal - commission - cogs
    var expandedStylistId by remember { mutableStateOf<Long?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper),
        color = Color.White,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Home",
                color = Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(
                    onClick = { range = "today" },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (range == "today") Brand else Soft, contentColor = if (range == "today") Color.White else Brand),
                ) { Text("Today") }
                androidx.compose.material3.Button(
                    onClick = { range = "7" },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = if (range == "7") Brand else Soft, contentColor = if (range == "7") Color.White else Brand),
                ) { Text("Last 7 days") }
            }
            MetricRow(
                Metric("Bookings", if (range == "today") todayBookings.toString() else bookings.size.toString(), if (range == "today") "Today" else "Last 7 days", Brand),
                Metric("Collected", fmt(money, collected), if (range == "today") "Today" else "Last 7 days", Brand),
            )
            MetricRow(
                Metric("Balances", fmt(money, outstanding), "Outstanding", if (outstanding > 0) Danger else Brand),
                Metric("Business revenue", fmt(money, businessRevenue), "Collected \u2212 costs", if (businessRevenue < 0) Danger else Brand),
            )
            MetricRow(
                Metric("Bookings revenue", fmt(money, bookingsGross), if (range == "today") "Today" else "Last 7 days", Brand),
                Metric("Product sales", fmt(money, salesGross), if (range == "today") "Today" else "Last 7 days", Brand),
            )
            MetricRow(
                Metric("COGS", fmt(money, cogs), if (range == "today") "Today" else "Last 7 days", Muted),
                Metric("Stock purchases", fmt(money, stockPurchases), "Product costs", Muted),
            )
            CardBlock("Commissions") {
                val paidBookings = database.bookingsBetween(start, end).filter { it.status == "completed" }
                val stylists = database.stylists()
                val commissionRows = stylists.mapNotNull { stylist ->
                    val stylistBookings = paidBookings.filter { it.stylistId == stylist.id }
                    val total = stylistBookings.sumOf { it.serviceCommission }
                    if (stylistBookings.isEmpty()) null else StylistCommissionRow(stylist.id, stylist.name, total, stylistBookings)
                }
                if (commissionRows.isEmpty()) {
                    DetailLine("Commission due", fmt(money, 0.0))
                } else {
                    commissionRows.forEach { row ->
                        ExpandableCommissionLine(
                            row = row,
                            money = money,
                            expanded = expandedStylistId == row.stylistId,
                            onToggle = { expandedStylistId = if (expandedStylistId == row.stylistId) null else row.stylistId },
                        )
                    }
                }
                }
            Text("Recent transactions", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            val recent = recentTransactions(bookings, sales, expenses, clients, money)
            if (recent.isEmpty()) {
                Text("No transactions yet. Start by adding a booking or recording a sale.", color = Muted, fontSize = 14.sp)
            } else {
                recent.take(8).forEach { RecentTransactionRow(it) }
            }
        }
    }
}

@Composable
private fun ExpandableCommissionLine(
    row: StylistCommissionRow,
    money: NumberFormat,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(row.stylistName, color = Brand, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(fmt(money, row.totalCommission), color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (expanded) {
            val serviceRows = row.bookings
                .groupBy { it.serviceName }
                .map { (service, bookings) ->
                    val value = bookings.sumOf { it.amount }
                    val commission = bookings.sumOf { it.serviceCommission }
                    ServiceCommissionRow(service, value, commission)
                }
            serviceRows.forEach {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(it.serviceName, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Value ${fmt(money, it.totalValue)}", color = Muted, fontSize = 11.sp)
                    }
                    Text(fmt(money, it.commission), color = Brand, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total commission", color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(fmt(money, row.totalCommission), color = Brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RecentTransactionRow(item: RecentTransaction) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(item.subtitle, color = Muted, fontSize = 11.sp)
            }
            Text(item.amount, color = if (item.expense) Danger else Brand, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricRow(first: Metric, second: Metric) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MetricCard(metric = first, modifier = Modifier.weight(1f))
        MetricCard(metric = second, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(metric: Metric, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(metric.label, color = Muted, fontSize = 12.sp)
            Text(metric.value, color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(metric.note, color = metric.noteColor, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CardBlock(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, isStrong: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Muted, fontSize = 14.sp)
        Text(value, color = Ink, fontSize = 14.sp, fontWeight = if (isStrong) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun recentTransactions(
    bookings: List<SalonDatabase.Booking>,
    sales: List<SalonDatabase.Sale>,
    expenses: List<SalonDatabase.Expense>,
    clients: List<SalonDatabase.Client>,
    money: NumberFormat,
): List<RecentTransaction> {
    val rows = mutableListOf<RecentTransaction>()
    val clientMap = clients.associateBy({ it.id }, { it.name })
    bookings.filter { it.status == "completed" }.forEach {
        rows.add(RecentTransaction(it.date, "Booking: ${it.client}", "${it.serviceName} • paid ${fmt(money, it.paidAmount)}", fmt(money, it.paidAmount), false, it.id))
    }
    sales.forEach {
        val title = if (it.clientId != null) (clientMap[it.clientId] ?: "Sale") else it.description
        rows.add(RecentTransaction(it.date, title, "${it.payment} • paid ${fmt(money, it.paidAmount)}", fmt(money, it.paidAmount), false, it.id))
    }
    expenses.forEach {
        rows.add(RecentTransaction(it.date, it.category, if (it.note.isBlank()) "Expense" else it.note, "-${fmt(money, it.amount)}", true, it.id))
    }
    return rows.sortedWith(compareByDescending<RecentTransaction> { it.date }.thenByDescending { it.id })
}

private data class RecentTransaction(
    val date: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val expense: Boolean,
    val id: Long,
)

private data class StylistCommissionRow(
    val stylistId: Long,
    val stylistName: String,
    val totalCommission: Double,
    val bookings: List<SalonDatabase.Booking>,
)

private data class ServiceCommissionRow(
    val serviceName: String,
    val totalValue: Double,
    val commission: Double,
)

private fun fmt(money: NumberFormat, v: Double): String = money.format(v / 100.0)

private fun daysAgo(days: Int): String {
    val copy = Calendar.getInstance()
    copy.add(Calendar.DAY_OF_YEAR, -days)
    return SalonUtils.DATE_FMT.get()!!.format(copy.time)
}

private data class Metric(
    val label: String,
    val value: String,
    val note: String,
    val noteColor: Color,
)


