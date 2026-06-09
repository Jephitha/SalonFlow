package com.salonflow.app

import android.content.ContentValues
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SalonDatabaseTest {

    private val db = SalonDatabase(RuntimeEnvironment.getApplication())

    @Test
    fun stockPurchasesBetween_returnsSumOfBatchesInRange() {
        val dba = db.writableDatabase
        dba.execSQL("DELETE FROM stock_batches")

        val start = "2024-01-01"
        val end = "2024-06-30"

        val batch1 = ContentValues().apply {
            put("inventoryId", 1L)
            put("quantity", 5)
            put("purchasePrice", 1000.0)
            put("dateAdded", "2024-03-15")
            put("remaining", 5)
        }
        val batch2 = ContentValues().apply {
            put("inventoryId", 1L)
            put("quantity", 3)
            put("purchasePrice", 1500.0)
            put("dateAdded", "2024-03-15")
            put("remaining", 3)
        }
        val batch3 = ContentValues().apply {
            put("inventoryId", 2L)
            put("quantity", 10)
            put("purchasePrice", 500.0)
            put("dateAdded", "2024-03-15")
            put("remaining", 10)
        }
        dba.insert("stock_batches", null, batch1)
        dba.insert("stock_batches", null, batch2)
        dba.insert("stock_batches", null, batch3)

        val result = db.stockPurchasesBetween(start, end)
        assertEquals("5*1000 + 3*1500 + 10*500 = 5000 + 4500 + 5000 = 14500", 14500.0, result, 0.001)
    }

    @Test
    fun stockPurchasesBetween_returnsZeroWhenNoBatches() {
        val dba = db.writableDatabase
        dba.execSQL("DELETE FROM stock_batches")

        val result = db.stockPurchasesBetween("2024-01-01", "2024-12-31")
        assertEquals("no batches", 0.0, result, 0.001)
    }

    @Test
    fun stockPurchasesBetween_onlyIncludesBatchesInDateRange() {
        val dba = db.writableDatabase
        dba.execSQL("DELETE FROM stock_batches")

        val batch = ContentValues().apply {
            put("inventoryId", 1L)
            put("quantity", 5)
            put("purchasePrice", 1000.0)
            put("dateAdded", "2024-06-01")
            put("remaining", 5)
        }
        dba.insert("stock_batches", null, batch)

        val before = db.stockPurchasesBetween("2024-01-01", "2024-05-31")
        assertEquals("before range", 0.0, before, 0.001)

        val after = db.stockPurchasesBetween("2024-06-02", "2024-12-31")
        assertEquals("after range", 0.0, after, 0.001)

        val inside = db.stockPurchasesBetween("2024-06-01", "2024-06-01")
        assertEquals("inside range", 5000.0, inside, 0.001)
    }
}
