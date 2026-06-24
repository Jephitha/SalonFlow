@file:Suppress("EXPOSED_PARAMETER_TYPE")

package com.salonflow.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object SalonUtils {
    @JvmField
    val DATE_FMT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
    @JvmField
    val MONTH_YEAR_FMT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
    }

    @JvmStatic
    fun formatDate(c: Calendar): String = DATE_FMT.get()!!.format(c.time)

    @JvmStatic
    fun monthStart(c: Calendar): String {
        val copy = c.clone() as Calendar
        copy.set(Calendar.DAY_OF_MONTH, 1)
        return formatDate(copy)
    }

    @JvmStatic
    fun monthEnd(c: Calendar): String {
        val copy = c.clone() as Calendar
        copy.set(Calendar.DAY_OF_MONTH, copy.getActualMaximum(Calendar.DAY_OF_MONTH))
        return formatDate(copy)
    }

    @JvmStatic
    fun bookingsGross(bookings: List<SalonDatabase.Booking>): Double =
        bookings.filter { it.status != "cancelled" }.sumOf { it.amount }

    @JvmStatic
    fun salesGross(sales: List<SalonDatabase.Sale>): Double =
        sales.sumOf { it.amount }

    @JvmStatic
    fun grossSales(bookings: List<SalonDatabase.Booking>, sales: List<SalonDatabase.Sale>): Double =
        bookingsGross(bookings) + salesGross(sales)

    @JvmStatic
    fun collectedSales(bookings: List<SalonDatabase.Booking>, sales: List<SalonDatabase.Sale>): Double =
        bookings.filter { it.status == "completed" }.sumOf { it.paidAmount } + sales.sumOf { it.paidAmount }

    @JvmStatic
    fun commissionDue(bookings: List<SalonDatabase.Booking>, sales: List<SalonDatabase.Sale>): Double =
        bookings.filter { it.status == "completed" }.sumOf { it.serviceCommission }

    @JvmStatic
    fun sumExpenses(expenses: List<SalonDatabase.Expense>): Double =
        expenses.sumOf { it.amount }

    @JvmStatic
    fun outstandingBalance(bookings: List<SalonDatabase.Booking>, sales: List<SalonDatabase.Sale>): Double =
        bookings.filter { it.status != "cancelled" }.sumOf { maxOf(0.0, it.amount - it.paidAmount) } +
                sales.sumOf { maxOf(0.0, it.amount - it.paidAmount) }
}
