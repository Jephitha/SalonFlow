package com.salonflow.app

import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

object BookingsComposeFactory {
    @JvmStatic
    fun create(activity: MainActivity, database: SalonDatabase, initialDate: String): View {
        return ComposeView(activity).apply {
            setContent {
                SalonFlowTheme {
                    BookingsScreen(
                        database = database,
                        initialDate = initialDate,
                        onEmptyDateSelected = { activity.openBookingFromCompose(it) },
                        onBookingSelected = { activity.openBookingActionsFromCompose(it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingsScreen(
    database: SalonDatabase,
    initialDate: String,
    onEmptyDateSelected: (String) -> Unit,
    onBookingSelected: (SalonDatabase.Booking) -> Unit,
) {
    val money = NumberFormat.getCurrencyInstance(Locale("en", "KE")).apply { maximumFractionDigits = 0 }
    var selectedDate by remember { mutableStateOf(initialDate) }
    var visibleMonth by remember { mutableStateOf(calendarFrom(initialDate)) }
    val monthStart = SalonUtils.monthStart(visibleMonth)
    val monthEnd = SalonUtils.monthEnd(visibleMonth)
    val monthBookings = database.bookingsBetween(monthStart, monthEnd)
    val pendingDates = monthBookings.filter { it.status == "pending" }.map { it.date }.toSet()
    val today = SalonDatabase.today()
    val isPastDate = selectedDate < today
    val scrollState = rememberScrollState()
    val selectedBookings = database.bookingsForDate(selectedDate)
    val selectedSales = if (isPastDate) database.salesBetween(selectedDate, selectedDate) else emptyList()

    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Bookings", color = MaterialTheme.colorScheme.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            BookingCalendar(
                selectedDate = selectedDate,
                visibleMonth = visibleMonth,
                pendingDates = pendingDates,
                onPreviousMonth = { visibleMonth = (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, -1) } },
                onNextMonth = { visibleMonth = (visibleMonth.clone() as Calendar).apply { add(Calendar.MONTH, 1) } },
                onDateSelected = { date ->
                    selectedDate = date
                    val hasSales = date < today && database.salesBetween(date, date).isNotEmpty()
                    if (database.bookingsForDate(date).isEmpty() && !hasSales) onEmptyDateSelected(date)
                },
            )
            Text("Bookings on $selectedDate", color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (selectedBookings.isEmpty()) {
                Text("No bookings yet. Tap the + button to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            } else {
                selectedBookings.forEach { booking ->
                    BookingCard(
                        booking = booking,
                        amount = money.format(booking.amount / 100.0),
                        onClick = { onBookingSelected(booking) },
                    )
                }
            }
            if (isPastDate && selectedSales.isNotEmpty()) {
                Text("Sales on $selectedDate", color = MaterialTheme.colorScheme.secondary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                selectedSales.forEach { sale ->
                    SaleCard(
                        sale = sale,
                        amount = money.format(sale.amount / 100.0),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookingCalendar(
    selectedDate: String,
    visibleMonth: Calendar,
    pendingDates: Set<String>,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (String) -> Unit,
) {
    val today = SalonDatabase.today()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onPreviousMonth,
                colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(0.3f),
            ) { Text("<") }
            Text(
                SalonUtils.MONTH_YEAR_FMT.get()!!.format(visibleMonth.time),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Button(
                onClick = onNextMonth,
                colors = ButtonDefaults.buttonColors(containerColor = Soft, contentColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(0.3f),
            ) { Text(">") }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { index, label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = if (index >= 5) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                )
            }
        }
        val first = (visibleMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
        val firstOffset = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7
        val days = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        var day = 1
        repeat(6) { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(7) { dow ->
                    if ((week == 0 && dow < firstOffset) || day > days) {
                        Box(modifier = Modifier.weight(1f).height(44.dp))
                    } else {
                        val currentDay = day
                        val date = SalonUtils.formatDate((visibleMonth.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, currentDay) })
                        val selected = date == selectedDate
                        val isToday = date == today
                        val hasPending = pendingDates.contains(date)
                        val isWeekend = dow >= 5
                        val bgColor = when {
                            selected -> Brand
                            isToday -> Brand.copy(alpha = 0.12f)
                            isWeekend -> Rose.copy(alpha = 0.4f)
                            else -> Color(0xFFF8FAF8)
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(bgColor)
                                .then(
                                    if (isToday && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(7.dp))
                                    else Modifier
                                )
                                .clickable { onDateSelected(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                currentDay.toString(),
                                color = if (selected) Color.White else if (isToday) Brand else if (isWeekend) (if (isSystemInDarkTheme()) Color.White else Accent) else Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                            if (hasPending) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(top = 5.dp, end = 5.dp)
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondary),
                                )
                            }
                        }
                        day++
                    }
                }
            }
        }
    }
}

private fun calendarFrom(date: String): Calendar {
    return Calendar.getInstance().apply {
        time = SalonUtils.DATE_FMT.get()!!.parse(date) ?: time
    }
}

@Composable
private fun BookingCard(booking: SalonDatabase.Booking, amount: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${booking.time}  ${booking.client}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${booking.serviceName} with ${booking.stylistName} • ${booking.payment}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                StatusPill(booking.status)
            }
        }
    }
}

@Composable
private fun SaleCard(sale: SalonDatabase.Sale, amount: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Warm),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Sale: ${sale.description}", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${sale.payment}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("sale", color = MaterialTheme.colorScheme.secondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val background = when (status) {
        "completed" -> Soft
        "pending" -> Warm
        else -> Rose
    }
    val textColor = when (status) {
        "completed" -> MaterialTheme.colorScheme.primary
        "pending" -> Amber
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        text = status,
        color = textColor,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}
