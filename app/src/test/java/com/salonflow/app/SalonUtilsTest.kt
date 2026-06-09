package com.salonflow.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class SalonUtilsTest {

    private fun booking(
        amount: Double,
        status: String = "completed",
        paidAmount: Double = amount
    ): SalonDatabase.Booking = SalonDatabase.Booking(
        0, "", "", "", 0, "", 0, "", amount, status, "", null, paidAmount, "", 0.0
    )

    private fun sale(
        amount: Double,
        paidAmount: Double = amount
    ): SalonDatabase.Sale = SalonDatabase.Sale(
        0, "", "", amount, "", null, "", null, paidAmount, "", null, 0
    )

    private fun expense(amount: Double): SalonDatabase.Expense =
        SalonDatabase.Expense(0, "", "", amount, "")

    @Test
    fun formatDate_formatsCorrectly() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.JUNE, 5, 10, 30, 0)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("2026-06-05", SalonUtils.formatDate(cal))
    }

    @Test
    fun monthStart_returnsFirstOfMonth() {
        val cal = Calendar.getInstance()
        cal.set(2026, Calendar.MARCH, 15, 8, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        assertEquals("2026-03-01", SalonUtils.monthStart(cal))
    }

    @Test
    fun bookingsGross_excludesCancelled() {
        val bookings = listOf(
            booking(amount = 100.0, status = "completed"),
            booking(amount = 50.0, status = "cancelled"),
            booking(amount = 75.0, status = "completed"),
            booking(amount = 25.0, status = "pending"),
        )
        assertEquals(200.0, SalonUtils.bookingsGross(bookings), 0.001)
    }

    @Test
    fun salesGross_sumsAll() {
        val sales = listOf(
            sale(amount = 100.0),
            sale(amount = 50.0),
            sale(amount = 25.0),
        )
        assertEquals(175.0, SalonUtils.salesGross(sales), 0.001)
    }

    @Test
    fun outstandingBalance_returnsCorrectValue() {
        val bookings = listOf(
            booking(amount = 100.0, paidAmount = 60.0, status = "completed"),
            booking(amount = 50.0, paidAmount = 50.0, status = "completed"),
            booking(amount = 80.0, paidAmount = 0.0, status = "cancelled"),
            booking(amount = 40.0, paidAmount = 10.0, status = "pending"),
        )
        val sales = listOf(
            sale(amount = 200.0, paidAmount = 150.0),
            sale(amount = 30.0, paidAmount = 30.0),
        )
        assertEquals(120.0, SalonUtils.outstandingBalance(bookings, sales), 0.001)
    }
}
