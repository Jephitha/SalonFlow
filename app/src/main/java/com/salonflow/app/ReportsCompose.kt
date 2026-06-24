package com.salonflow.app

import android.view.View
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import kotlin.math.max
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object ReportsComposeFactory {
    @JvmStatic
    fun create(activity: MainActivity, database: SalonDatabase): View {
        return ComposeView(activity).apply {
            setContent {
                SalonFlowTheme {
                    ReportsScreen(database = database)
                }
            }
        }
    }
}

@Composable
private fun ReportsScreen(database: SalonDatabase) {
    val context = LocalContext.current
    val money = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply { maximumFractionDigits = 0 }
    var reportMonth by remember { mutableStateOf(Calendar.getInstance()) }
    var showOutstanding by remember { mutableStateOf(false) }
    var showSales by remember { mutableStateOf(false) }
    var showExpenses by remember { mutableStateOf(false) }
    var showCommission by remember { mutableStateOf(false) }

    if (showOutstanding) {
        OutstandingScreen(database = database, money = money, onBack = { showOutstanding = false })
        return
    }
    if (showSales) {
        SalesDetailScreen(database = database, reportMonth = reportMonth, money = money, onBack = { showSales = false })
        return
    }
    if (showExpenses) {
        ExpensesDetailScreen(database = database, reportMonth = reportMonth, money = money, onBack = { showExpenses = false })
        return
    }
    if (showCommission) {
        CommissionDetailScreen(database = database, reportMonth = reportMonth, money = money, onBack = { showCommission = false })
        return
    }

    val monthStart = SalonUtils.monthStart(reportMonth)
    val monthEnd = SalonUtils.monthEnd(reportMonth)
    val today = SalonDatabase.today()

    val monthBookings = database.bookingsBetween(monthStart, monthEnd)
    val monthSales = database.salesBetween(monthStart, monthEnd)
    val monthExpenses = database.expensesBetween(monthStart, monthEnd)

    val grossBookings = SalonUtils.bookingsGross(monthBookings)
    val grossSales = SalonUtils.salesGross(monthSales)
    val gross = grossBookings + grossSales
    val collected = SalonUtils.collectedSales(monthBookings, monthSales)
    val commission = SalonUtils.commissionDue(monthBookings, monthSales)
    val expenses = SalonUtils.sumExpenses(monthExpenses)
    val outstanding = SalonUtils.outstandingBalance(monthBookings, monthSales)
    val cogs = database.costOfGoodsSold(monthStart, monthEnd)
    val stockPurchases = database.stockPurchasesBetween(monthStart, monthEnd)
    val profit = grossSales - cogs
    val businessRevenue = collected - expenses - commission - cogs

    val hasAnyData = monthBookings.isNotEmpty() || monthSales.isNotEmpty() || monthExpenses.isNotEmpty()

    // Year-to-date
    val yearStart = Calendar.getInstance().apply { set(Calendar.DAY_OF_YEAR, 1) }
    val yStart = SalonUtils.formatDate(yearStart)
    val yearBookings = database.bookingsBetween(yStart, today)
    val yearSales = database.salesBetween(yStart, today)
    val yearExpenses = database.expensesBetween(yStart, today)
    val yGross = SalonUtils.grossSales(yearBookings, yearSales)
    val yCollected = SalonUtils.collectedSales(yearBookings, yearSales)
    val yCommission = SalonUtils.commissionDue(yearBookings, yearSales)
    val yExpenses = SalonUtils.sumExpenses(yearExpenses)
    val yCOGS = database.costOfGoodsSold(yStart, today)
    val yStockPurchases = database.stockPurchasesBetween(yStart, today)
    val yProfit = SalonUtils.salesGross(yearSales) - yCOGS
    val yBusinessRevenue = yCollected - yExpenses - yCommission - yCOGS

    // Monthly trend (last 6 months)
    val grossByMonth = database.monthlyGrossSales(6)
    val trendCal = Calendar.getInstance()
    val months = mutableListOf<Pair<String, Double>>()
    var maxGross = 1.0
    for (i in 5 downTo 0) {
        val m = trendCal.clone() as Calendar
        m.add(Calendar.MONTH, -i)
        val ym = String.format(Locale.US, "%04d-%02d", m.get(Calendar.YEAR), m.get(Calendar.MONTH) + 1)
        val g = grossByMonth[ym] ?: 0.0
        maxGross = max(maxGross, g)
        val label = SalonUtils.MONTH_YEAR_FMT.get()!!.format(m.time).take(3)
        months.add(label to g)
    }

    // Service revenue
    val svcRevenueMap = mutableMapOf<String, Double>()
    for (b in monthBookings) {
        if (b.status == "cancelled") continue
        svcRevenueMap[b.serviceName] = (svcRevenueMap[b.serviceName] ?: 0.0) + b.amount
    }
    val svcRevenue = svcRevenueMap.entries.sortedByDescending { it.value }.associate { it.key to money.format(it.value / 100.0) }

    // Stylist commission
    val stylistCommMap = mutableMapOf<String, Double>()
    for (s in database.stylists()) stylistCommMap[s.name] = 0.0
    for (b in monthBookings) {
        if (b.status == "completed") stylistCommMap[b.stylistName] = (stylistCommMap[b.stylistName] ?: 0.0) + b.serviceCommission
    }
    val stylistComm = stylistCommMap.filter { it.value > 0 }.entries.sortedByDescending { it.value }.associate { it.key to money.format(it.value / 100.0) }

    // Expense breakdown
    val expMap = mutableMapOf<String, Double>()
    for (e in monthExpenses) expMap[e.category] = (expMap[e.category] ?: 0.0) + e.amount
    val expBreakdown = expMap.entries.sortedByDescending { it.value }.associate { it.key to money.format(it.value / 100.0) }

    // Payment mix
    var cashTotal = 0.0
    var mobileTotal = 0.0
    for (b in monthBookings) {
        if (b.status == "cancelled") continue
        if (b.payment == "Cash") cashTotal += b.paidAmount else mobileTotal += b.paidAmount
    }
    for (s in monthSales) {
        if (s.payment == "Cash") cashTotal += s.paidAmount else mobileTotal += s.paidAmount
    }
    val pmTotal = max(1.0, cashTotal + mobileTotal)
    val cashPct = ((cashTotal / pmTotal) * 100).toInt()
    val mobilePct = 100 - cashPct

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Reports", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold) }

            // Month selector
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { reportMonth = (reportMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(0.3f),
                    ) { Text("<") }
                    Text(
                        SalonUtils.MONTH_YEAR_FMT.get()!!.format(reportMonth.time),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Button(
                        onClick = { reportMonth = (reportMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) } },
                        colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(0.3f),
                    ) { Text(">") }
                }
            }

            if (!hasAnyData) {
                item {
                    Text(
                        "No reports data for this month. Add bookings, sales, or expenses to see your reports.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                }
                return@LazyColumn
            }

            // KPI cards row 1
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiCard(
                        title = "Bookings revenue",
                        value = money.format(grossBookings / 100.0),
                        note = "Service bookings",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Product sales",
                        value = money.format(grossSales / 100.0),
                        note = "Walk-in sales",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (grossSales > 0) showSales = true
                            else Toast.makeText(context, "No sales this month", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            // KPI cards row 2
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiCard(
                        title = "Collected",
                        value = money.format(collected / 100.0),
                        note = "Payments received",
                        valueColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Expenses",
                        value = money.format(expenses / 100.0),
                        note = "Operating costs",
                        valueColor = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (expenses > 0) showExpenses = true
                            else Toast.makeText(context, "No expenses this month", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            // KPI cards row 3
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiCard(
                        title = "Stock purchases",
                        value = money.format(stockPurchases / 100.0),
                        note = "Product costs",
                        valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Outstanding",
                        value = money.format(outstanding / 100.0),
                        note = "Unpaid balances",
                        valueColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (outstanding > 0) showOutstanding = true
                            else Toast.makeText(context, "No outstanding balances", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            // KPI cards row 4
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    KpiCard(
                        title = "Profit on sales",
                        value = money.format(profit / 100.0),
                        note = "Sales \u2212 product cost",
                        valueColor = if (profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    KpiCard(
                        title = "Commission",
                        value = money.format(commission / 100.0),
                        note = "Stylist payouts",
                        valueColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (commission > 0) showCommission = true
                            else Toast.makeText(context, "No commissions this month", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            // Business revenue card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Business Revenue", color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text("COGS: ${money.format(cogs / 100.0)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            Text("Stock: ${money.format(stockPurchases / 100.0)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Text(
                            money.format(businessRevenue / 100.0),
                            color = if (businessRevenue >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        if (outstanding > 0) {
                            Text(
                                "Outstanding: ${money.format(outstanding / 100.0)}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // Service breakdown
            if (svcRevenue.isNotEmpty()) {
                item {
                    MiniReportCard(title = "Top services by revenue", rows = svcRevenue)
                }
            }

            // Commission per stylist
            if (stylistComm.isNotEmpty()) {
                item {
                    MiniReportCard(title = "Commission per stylist", rows = stylistComm)
                }
            }

            // Expense breakdown
            if (expBreakdown.isNotEmpty()) {
                item {
                    MiniReportCard(title = "Expense breakdown", rows = expBreakdown)
                }
            }

            // Payment mix
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Payment mix", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        PaymentMixBar("Cash", cashPct, money.format(cashTotal / 100.0))
                        PaymentMixBar("Mobile money", mobilePct, money.format(mobileTotal / 100.0))
                    }
                }
            }

            // Year-to-date summary
            item {
                MiniReportCard(
                    title = "Year-to-date (${String.format(Locale.US, "%04d", yearStart.get(Calendar.YEAR))})",
                    rows = mapOf(
                        "Gross sales" to money.format(yGross / 100.0),
                        "Collected" to money.format(yCollected / 100.0),
                        "Expenses" to money.format(yExpenses / 100.0),
                        "Commission" to money.format(yCommission / 100.0),
                        "COGS" to money.format(yCOGS / 100.0),
                        "Stock purchases" to money.format(yStockPurchases / 100.0),
                        "Profit on sales" to money.format(yProfit / 100.0),
                        "Business Revenue" to money.format(yBusinessRevenue / 100.0),
                    ),
                )
            }

            // Monthly gross sales trend
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Monthly gross sales trend", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        months.forEach { (label, value) ->
                            val barLen = max(1, ((value / maxGross) * 20).toInt())
                            val bar = buildString { repeat(barLen) { append('\u2588') } }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(bar, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.width(4.dp))
                                Text(money.format(value / 100.0), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }


        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    note: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = (if (onClick != null) modifier.clickable(onClick = onClick) else modifier),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            Text(value, color = valueColor, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            Text(note, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
    }
}

@Composable
private fun PaymentMixBar(label: String, pct: Int, formattedAmount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(
            "$pct% ($formattedAmount)",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MiniReportCard(title: String, rows: Map<String, String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            rows.forEach { (label, value) ->
                ReportLine(label, value)
            }
        }
    }
}

@Composable
private fun OutstandingScreen(database: SalonDatabase, money: NumberFormat, onBack: () -> Unit) {
    val clients = remember { database.clients() }
    val clientIds = remember(clients) { clients.map { it.id } }
    val allBookings = remember(clientIds) { database.bookingsForClientIds(clientIds) }
    val allSales = remember(clientIds) { database.salesForClientIds(clientIds) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u2190", color = Brand, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
                Text("Outstanding Balances", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            val outstandingClients = clients.filter { client ->
                val bookings = allBookings[client.id] ?: emptyList()
                val sales = allSales[client.id] ?: emptyList()
                SalonUtils.outstandingBalance(bookings, sales) > 0
            }
            if (outstandingClients.isEmpty()) {
                Text("No outstanding balances.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(outstandingClients, key = { it.id }) { client ->
                        val bookings = allBookings[client.id] ?: emptyList()
                        val sales = allSales[client.id] ?: emptyList()
                        val bal = SalonUtils.outstandingBalance(bookings, sales)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(client.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(if (client.phone.isEmpty()) "" else client.phone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text(money.format(bal / 100.0), color = if (bal > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesDetailScreen(database: SalonDatabase, reportMonth: Calendar, money: NumberFormat, onBack: () -> Unit) {
    val monthStart = SalonUtils.monthStart(reportMonth)
    val monthEnd = SalonUtils.monthEnd(reportMonth)
    val sales = remember(monthStart, monthEnd) { database.salesBetween(monthStart, monthEnd) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u2190", color = Brand, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
                Text("Product Sales", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (sales.isEmpty()) {
                Text("No sales this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Text("${sales.size} sale(s) this month", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(sales, key = { it.id }) { sale ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sale.description, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("${sale.date} \u2022 ${sale.payment}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text(money.format(sale.amount / 100.0), color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesDetailScreen(database: SalonDatabase, reportMonth: Calendar, money: NumberFormat, onBack: () -> Unit) {
    val monthStart = SalonUtils.monthStart(reportMonth)
    val monthEnd = SalonUtils.monthEnd(reportMonth)
    val expenses = remember(monthStart, monthEnd) { database.expensesBetween(monthStart, monthEnd) }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u2190", color = Brand, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
                Text("Expenses", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (expenses.isEmpty()) {
                Text("No expenses this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Text("${expenses.size} expense(s) this month", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(expenses, key = { it.id }) { expense ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(expense.category, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text(if (expense.note.isBlank()) expense.date else "${expense.date} \u2022 ${expense.note}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text(money.format(expense.amount / 100.0), color = MaterialTheme.colorScheme.error, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommissionDetailScreen(database: SalonDatabase, reportMonth: Calendar, money: NumberFormat, onBack: () -> Unit) {
    val monthStart = SalonUtils.monthStart(reportMonth)
    val monthEnd = SalonUtils.monthEnd(reportMonth)
    val bookings = remember(monthStart, monthEnd) { database.bookingsBetween(monthStart, monthEnd) }
    val stylists = remember { database.stylists() }

    val stylistCommMap = remember(bookings) {
        val map = mutableMapOf<String, Double>()
        for (s in stylists) map[s.name] = 0.0
        for (b in bookings) {
            if (b.status == "completed") map[b.stylistName] = (map[b.stylistName] ?: 0.0) + b.serviceCommission
        }
        map.filter { it.value > 0 }.entries.sortedByDescending { it.value }
    }

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("\u2190", color = Brand, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onBack))
                Text("Commission", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            if (stylistCommMap.isEmpty()) {
                Text("No commissions this month.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                Text("${stylistCommMap.size} stylist(s) with commissions", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(stylistCommMap, key = { it.key }) { (name, total) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(name, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(money.format(total / 100.0), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
