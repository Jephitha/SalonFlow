package com.salonflow.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SalonDatabase extends SQLiteOpenHelper {
    static final String DB_NAME = "salonflow.db";
    private static final int DB_VERSION = 7;
    private final Context context;
    private final DataCipher cipher;
    private static final ThreadLocal<SimpleDateFormat> DATE_FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("yyyy-MM-dd", Locale.US); }
    };
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US); }
    };
    private static final ThreadLocal<SimpleDateFormat> MONTH_YEAR_FMT = new ThreadLocal<SimpleDateFormat>() {
        @Override protected SimpleDateFormat initialValue() { return new SimpleDateFormat("MMMM yyyy", Locale.US); }
    };

    SalonDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        this.context = context;
        this.cipher = new DataCipher(context);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE stylists (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, commission REAL NOT NULL, phone TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE services (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, price REAL NOT NULL, commission REAL NOT NULL)");
        db.execSQL("CREATE TABLE clients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL)");
        db.execSQL("CREATE TABLE inventory (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, category TEXT NOT NULL, on_hand INTEGER NOT NULL, reorder_at INTEGER NOT NULL, cost REAL NOT NULL, sellingPrice REAL DEFAULT 0)");
        db.execSQL("CREATE TABLE stock_batches (id INTEGER PRIMARY KEY AUTOINCREMENT, inventoryId INTEGER, quantity INTEGER, purchasePrice REAL, dateAdded TEXT, remaining INTEGER)");
        db.execSQL("CREATE TABLE sale_stock_usage (id INTEGER PRIMARY KEY AUTOINCREMENT, saleId INTEGER, inventoryId INTEGER, batchId INTEGER, quantity INTEGER, purchasePrice REAL, sellingPrice REAL)");
        db.execSQL("CREATE TABLE bookings (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, time TEXT NOT NULL, client TEXT NOT NULL, service_id INTEGER NOT NULL, stylist_id INTEGER NOT NULL, amount REAL NOT NULL, status TEXT NOT NULL, payment TEXT NOT NULL, client_id INTEGER, paid_amount REAL NOT NULL DEFAULT 0, due_date TEXT NOT NULL DEFAULT '')");
        db.execSQL("CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, description TEXT NOT NULL, amount REAL NOT NULL, payment TEXT NOT NULL, stylist_id INTEGER, client_id INTEGER, paid_amount REAL NOT NULL DEFAULT 0, due_date TEXT NOT NULL DEFAULT '', inventory_id INTEGER, quantity INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT, date TEXT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, note TEXT)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_date ON bookings(date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_client ON bookings(client_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_stylist ON bookings(stylist_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(date)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_client ON sales(client_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_stylist ON sales(stylist_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS clients (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, phone TEXT NOT NULL)");
            addColumn(db, "bookings", "client_id", "INTEGER");
            addColumn(db, "bookings", "paid_amount", "REAL NOT NULL DEFAULT 0");
            addColumn(db, "bookings", "due_date", "TEXT NOT NULL DEFAULT ''");
            addColumn(db, "sales", "client_id", "INTEGER");
            addColumn(db, "sales", "paid_amount", "REAL NOT NULL DEFAULT 0");
            addColumn(db, "sales", "due_date", "TEXT NOT NULL DEFAULT ''");
            db.execSQL("UPDATE bookings SET paid_amount=amount WHERE status='paid' AND paid_amount=0");
            db.execSQL("UPDATE sales SET paid_amount=amount WHERE paid_amount=0");
            // Preserve existing user data only; no demo seeding.
        }
        if (oldVersion < 3) {
            addColumn(db, "sales", "inventory_id", "INTEGER");
            addColumn(db, "sales", "quantity", "INTEGER NOT NULL DEFAULT 0");
        }
        if (oldVersion < 4) {
            addColumn(db, "stylists", "phone", "TEXT NOT NULL DEFAULT ''");
        }
        if (oldVersion < 5) {
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_date ON bookings(date)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_client ON bookings(client_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_bookings_stylist ON bookings(stylist_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_date ON sales(date)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_client ON sales(client_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_sales_stylist ON sales(stylist_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expenses_date ON expenses(date)");
        }
        if (oldVersion < 6) {
            // Convert monetary values from dollars to cents (multiply by 100)
            db.execSQL("UPDATE services SET price = ROUND(price * 100), commission = commission");
            db.execSQL("UPDATE inventory SET cost = ROUND(cost * 100)");
            db.execSQL("UPDATE bookings SET amount = ROUND(amount * 100), paid_amount = ROUND(paid_amount * 100)");
            db.execSQL("UPDATE sales SET amount = ROUND(amount * 100), paid_amount = ROUND(paid_amount * 100)");
            db.execSQL("UPDATE expenses SET amount = ROUND(amount * 100)");
        }
        if (oldVersion < 7) {
            addColumn(db, "inventory", "sellingPrice", "REAL DEFAULT 0");
            db.execSQL("CREATE TABLE IF NOT EXISTS stock_batches (id INTEGER PRIMARY KEY AUTOINCREMENT, inventoryId INTEGER, quantity INTEGER, purchasePrice REAL, dateAdded TEXT, remaining INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS sale_stock_usage (id INTEGER PRIMARY KEY AUTOINCREMENT, saleId INTEGER, inventoryId INTEGER, batchId INTEGER, quantity INTEGER, purchasePrice REAL, sellingPrice REAL)");
            Cursor c = db.rawQuery("SELECT id, on_hand, cost FROM inventory", null);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                int onHand = c.getInt(1);
                double cost = c.getDouble(2);
                if (onHand > 0) {
                    ContentValues vals = new ContentValues();
                    vals.put("inventoryId", id);
                    vals.put("quantity", onHand);
                    vals.put("purchasePrice", cost);
                    vals.put("dateAdded", today());
                    vals.put("remaining", onHand);
                    db.insert("stock_batches", null, vals);
                }
                ContentValues up = new ContentValues();
                up.put("sellingPrice", cost > 0 ? cost : 0);
                db.update("inventory", up, "id=?", new String[]{String.valueOf(id)});
            }
            c.close();
            // Migrate existing sales with inventory data into sale_stock_usage
            Cursor sc = db.rawQuery("SELECT s.id,s.inventory_id,s.quantity,i.cost,i.sellingPrice FROM sales s JOIN inventory i ON i.id=s.inventory_id WHERE s.inventory_id IS NOT NULL AND s.quantity>0", null);
            while (sc.moveToNext()) {
                long saleId = sc.getLong(0);
                long invId = sc.getLong(1);
                int qty = sc.getInt(2);
                double cost = sc.getDouble(3);
                double sellPrice = sc.getDouble(4);
                ContentValues vals = new ContentValues();
                vals.put("saleId", saleId);
                vals.put("inventoryId", invId);
                vals.put("batchId", -1L);
                vals.put("quantity", qty);
                vals.put("purchasePrice", cost);
                vals.put("sellingPrice", sellPrice);
                db.insert("sale_stock_usage", null, vals);
            }
            sc.close();
        }
    }

    private void addColumn(SQLiteDatabase db, String table, String column, String definition) {
        try {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (Exception ignored) {
        }
    }

    long saveBooking(Booking booking) {
        ContentValues values = new ContentValues();
        values.put("date", booking.date);
        values.put("time", booking.time);
        values.put("client", enc(booking.client));
        values.put("service_id", booking.serviceId);
        values.put("stylist_id", booking.stylistId);
        values.put("amount", booking.amount);
        values.put("status", booking.status);
        values.put("payment", booking.payment);
        if (booking.clientId == null) values.putNull("client_id"); else values.put("client_id", booking.clientId);
        values.put("paid_amount", booking.paidAmount);
        values.put("due_date", booking.dueDate);
        SQLiteDatabase db = getWritableDatabase();
        if (booking.id == 0) return db.insert("bookings", null, values);
        db.update("bookings", values, "id=?", new String[]{String.valueOf(booking.id)});
        return booking.id;
    }

    void deleteBooking(long id) {
        getWritableDatabase().delete("bookings", "id=?", new String[]{String.valueOf(id)});
    }

    long saveSale(Sale sale) {
        ContentValues values = new ContentValues();
        values.put("date", sale.date);
        values.put("description", enc(sale.description));
        values.put("amount", sale.amount);
        values.put("payment", sale.payment);
        if (sale.stylistId == null) values.putNull("stylist_id"); else values.put("stylist_id", sale.stylistId);
        if (sale.clientId == null) values.putNull("client_id"); else values.put("client_id", sale.clientId);
        values.put("paid_amount", sale.paidAmount);
        values.put("due_date", sale.dueDate);
        if (sale.inventoryId == null) values.putNull("inventory_id"); else values.put("inventory_id", sale.inventoryId);
        values.put("quantity", sale.quantity);
        SQLiteDatabase db = getWritableDatabase();
        if (sale.id == 0) return db.insert("sales", null, values);
        db.update("sales", values, "id=?", new String[]{String.valueOf(sale.id)});
        return sale.id;
    }

    void deleteSale(long id) {
        getWritableDatabase().delete("sales", "id=?", new String[]{String.valueOf(id)});
    }

    long saveClient(Client client) {
        ContentValues values = new ContentValues();
        values.put("name", enc(client.name));
        values.put("phone", enc(client.phone));
        SQLiteDatabase db = getWritableDatabase();
        if (client.id == 0) return db.insert("clients", null, values);
        db.update("clients", values, "id=?", new String[]{String.valueOf(client.id)});
        return client.id;
    }

    void deleteClient(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("bookings", "client_id=? AND status='pending'", new String[]{String.valueOf(id)});
        ContentValues bookingValues = new ContentValues();
        bookingValues.putNull("client_id");
        bookingValues.put("client", enc("Deleted User"));
        db.update("bookings", bookingValues, "client_id=?", new String[]{String.valueOf(id)});
        ContentValues saleValues = new ContentValues();
        saleValues.putNull("client_id");
        db.update("sales", saleValues, "client_id=?", new String[]{String.valueOf(id)});
        db.delete("clients", "id=?", new String[]{String.valueOf(id)});
    }

    long saveExpense(Expense expense) {
        ContentValues values = new ContentValues();
        values.put("date", expense.date);
        values.put("category", enc(expense.category));
        values.put("amount", expense.amount);
        values.put("note", enc(expense.note));
        SQLiteDatabase db = getWritableDatabase();
        if (expense.id == 0) return db.insert("expenses", null, values);
        db.update("expenses", values, "id=?", new String[]{String.valueOf(expense.id)});
        return expense.id;
    }

    void deleteExpense(long id) {
        getWritableDatabase().delete("expenses", "id=?", new String[]{String.valueOf(id)});
    }

    long saveStylist(Stylist stylist) {
        ContentValues values = new ContentValues();
        values.put("name", enc(stylist.name));
        values.put("commission", stylist.commission);
        values.put("phone", enc(stylist.phone));
        SQLiteDatabase db = getWritableDatabase();
        if (stylist.id == 0) return db.insert("stylists", null, values);
        db.update("stylists", values, "id=?", new String[]{String.valueOf(stylist.id)});
        return stylist.id;
    }

    void deleteStylist(long id) {
        getWritableDatabase().delete("stylists", "id=?", new String[]{String.valueOf(id)});
    }

    long saveService(Service service) {
        ContentValues values = new ContentValues();
        values.put("name", enc(service.name));
        values.put("price", service.price);
        values.put("commission", service.commission);
        SQLiteDatabase db = getWritableDatabase();
        if (service.id == 0) return db.insert("services", null, values);
        db.update("services", values, "id=?", new String[]{String.valueOf(service.id)});
        return service.id;
    }

    void deleteService(long id) {
        getWritableDatabase().delete("services", "id=?", new String[]{String.valueOf(id)});
    }

    long saveInventory(InventoryItem item) {
        ContentValues values = new ContentValues();
        values.put("name", enc(item.name));
        values.put("category", enc(item.category));
        values.put("on_hand", item.onHand);
        values.put("reorder_at", item.reorderAt);
        values.put("cost", item.cost);
        values.put("sellingPrice", item.sellingPrice);
        SQLiteDatabase db = getWritableDatabase();
        if (item.id == 0) return db.insert("inventory", null, values);
        db.update("inventory", values, "id=?", new String[]{String.valueOf(item.id)});
        return item.id;
    }

    void adjustInventoryStock(long inventoryId, int delta) {
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE inventory SET on_hand = MAX(0, on_hand + ?) WHERE id = ?", new Object[]{delta, inventoryId});
    }

    List<StockBatch> stockBatches(long inventoryId) {
        List<StockBatch> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,inventoryId,quantity,purchasePrice,dateAdded,remaining FROM stock_batches WHERE inventoryId=? AND remaining>0 ORDER BY id", new String[]{String.valueOf(inventoryId)});
        while (c.moveToNext()) rows.add(new StockBatch(c.getLong(0), c.getLong(1), c.getInt(2), c.getDouble(3), c.getString(4), c.getInt(5)));
        c.close();
        return rows;
    }

    void deductFromBatches(long inventoryId, int quantity, long saleId) {
        SQLiteDatabase db = getWritableDatabase();
        InventoryItem item = inventoryItem(inventoryId);
        if (item == null) return;
        double sellPrice = item.sellingPrice;
        int remaining = quantity;
        Cursor c = db.rawQuery("SELECT id,remaining,purchasePrice FROM stock_batches WHERE inventoryId=? AND remaining>0 ORDER BY id", new String[]{String.valueOf(inventoryId)});
        List<long[]> batchUpdates = new ArrayList<>();
        List<ContentValues> usageRows = new ArrayList<>();
        while (c.moveToNext() && remaining > 0) {
            long batchId = c.getLong(0);
            int batchRemaining = c.getInt(1);
            double purchasePrice = c.getDouble(2);
            int take = Math.min(remaining, batchRemaining);
            batchUpdates.add(new long[]{batchId, batchRemaining - take});
            ContentValues vals = new ContentValues();
            vals.put("saleId", saleId);
            vals.put("inventoryId", inventoryId);
            vals.put("batchId", batchId);
            vals.put("quantity", take);
            vals.put("purchasePrice", purchasePrice);
            vals.put("sellingPrice", sellPrice);
            usageRows.add(vals);
            remaining -= take;
        }
        c.close();
        // If any quantity remains (no batches or insufficient batches), use item's cost as fallback
        if (remaining > 0) {
            ContentValues vals = new ContentValues();
            vals.put("saleId", saleId);
            vals.put("inventoryId", inventoryId);
            vals.put("batchId", -1L);
            vals.put("quantity", remaining);
            vals.put("purchasePrice", item.cost);
            vals.put("sellingPrice", sellPrice);
            usageRows.add(vals);
        }
        for (long[] update : batchUpdates) {
            db.execSQL("UPDATE stock_batches SET remaining=? WHERE id=?", new Object[]{update[1], update[0]});
        }
        for (ContentValues vals : usageRows) {
            db.insert("sale_stock_usage", null, vals);
        }
        // Decrement on_hand by actual quantity sold
        db.execSQL("UPDATE inventory SET on_hand = MAX(0, on_hand - ?) WHERE id = ?", new Object[]{quantity, inventoryId});
    }

    void restoreBatchDeductions(long saleId) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT inventoryId,batchId,quantity FROM sale_stock_usage WHERE saleId=?", new String[]{String.valueOf(saleId)});
        boolean hasUsage = c.moveToFirst();
        if (hasUsage) {
            do {
                long inventoryId = c.getLong(0);
                long batchId = c.getLong(1);
                int qty = c.getInt(2);
                if (batchId >= 0) {
                    db.execSQL("UPDATE stock_batches SET remaining=remaining+? WHERE id=?", new Object[]{qty, batchId});
                }
                db.execSQL("UPDATE inventory SET on_hand = on_hand + ? WHERE id = ?", new Object[]{qty, inventoryId});
            } while (c.moveToNext());
        }
        c.close();
        if (!hasUsage) {
            Cursor sc = db.rawQuery("SELECT inventory_id,quantity FROM sales WHERE id=? AND inventory_id IS NOT NULL AND quantity>0", new String[]{String.valueOf(saleId)});
            if (sc.moveToFirst()) {
                long invId = sc.getLong(0);
                int qty = sc.getInt(1);
                db.execSQL("UPDATE inventory SET on_hand = on_hand + ? WHERE id = ?", new Object[]{qty, invId});
            }
            sc.close();
        }
        db.delete("sale_stock_usage", "saleId=?", new String[]{String.valueOf(saleId)});
    }

    double costOfGoodsSold(String start, String end) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(su.purchasePrice * su.quantity),0) FROM sale_stock_usage su JOIN sales s ON s.id=su.saleId WHERE s.date BETWEEN ? AND ?", new String[]{start, end});
        double total = c.moveToFirst() ? c.getDouble(0) : 0;
        c.close();
        return total;
    }

    double stockPurchasesBetween(String start, String end) {
        Cursor c = getReadableDatabase().rawQuery("SELECT COALESCE(SUM(purchasePrice * quantity),0) FROM stock_batches WHERE dateAdded BETWEEN ? AND ?", new String[]{start, end});
        double total = c.moveToFirst() ? c.getDouble(0) : 0;
        c.close();
        return total;
    }

    void deleteInventory(long id) {
        getWritableDatabase().delete("inventory", "id=?", new String[]{String.valueOf(id)});
    }

    List<Stylist> stylists() {
        List<Stylist> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,commission,phone FROM stylists", null);
        while (c.moveToNext()) rows.add(new Stylist(c.getLong(0), dec(c.getString(1)), c.getDouble(2), dec(c.getString(3))));
        c.close();
        rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return rows;
    }

    List<Service> services() {
        List<Service> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,price,commission FROM services", null);
        while (c.moveToNext()) rows.add(new Service(c.getLong(0), dec(c.getString(1)), c.getDouble(2), c.getDouble(3)));
        c.close();
        rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return rows;
    }

    List<Client> clients() {
        List<Client> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,phone FROM clients", null);
        while (c.moveToNext()) rows.add(new Client(c.getLong(0), dec(c.getString(1)), dec(c.getString(2))));
        c.close();
        rows.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return rows;
    }

    List<InventoryItem> inventory() {
        List<InventoryItem> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,category,on_hand,reorder_at,cost,sellingPrice FROM inventory", null);
        while (c.moveToNext()) rows.add(new InventoryItem(c.getLong(0), dec(c.getString(1)), dec(c.getString(2)), c.getInt(3), c.getInt(4), c.getDouble(5), c.getDouble(6)));
        c.close();
        rows.sort((a, b) -> {
            int cat = a.category.compareToIgnoreCase(b.category);
            return cat != 0 ? cat : a.name.compareToIgnoreCase(b.name);
        });
        return rows;
    }

    InventoryItem inventoryItem(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT id,name,category,on_hand,reorder_at,cost,sellingPrice FROM inventory WHERE id=?", new String[]{String.valueOf(id)});
        InventoryItem item = c.moveToFirst() ? new InventoryItem(c.getLong(0), dec(c.getString(1)), dec(c.getString(2)), c.getInt(3), c.getInt(4), c.getDouble(5), c.getDouble(6)) : null;
        c.close();
        return item;
    }

    List<Booking> bookingsForDate(String date) {
        List<Booking> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT b.id,b.date,b.time,b.client,b.service_id,s.name,b.stylist_id,st.name,b.amount,b.status,b.payment,b.client_id,b.paid_amount,b.due_date,s.commission * b.amount / 100.0 " +
                        "FROM bookings b JOIN services s ON s.id=b.service_id JOIN stylists st ON st.id=b.stylist_id " +
                        "WHERE b.date=? ORDER BY CASE b.status WHEN 'pending' THEN 0 WHEN 'completed' THEN 1 ELSE 2 END, b.time",
                new String[]{date});
        while (c.moveToNext()) rows.add(bookingFrom(c));
        c.close();
        return rows;
    }

    Map<String, Double> monthlyGrossSales(int monthsBack) {
        Map<String, Double> result = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        String end = DATE_FMT.get().format(cal.getTime());
        cal.add(Calendar.MONTH, -(monthsBack - 1));
        cal.set(Calendar.DAY_OF_MONTH, 1);
        String start = DATE_FMT.get().format(cal.getTime());
        Cursor c = getReadableDatabase().rawQuery(
            "SELECT substr(date,1,7) as ym, SUM(amount) FROM (" +
            "  SELECT date,amount FROM bookings WHERE date BETWEEN ? AND ? AND status != 'cancelled'" +
            "  UNION ALL" +
            "  SELECT date,amount FROM sales WHERE date BETWEEN ? AND ?" +
            ") GROUP BY ym ORDER BY ym",
            new String[]{start, end, start, end});
        while (c.moveToNext()) result.put(c.getString(0), c.getDouble(1));
        c.close();
        return result;
    }

    List<Booking> bookingsBetween(String start, String end) {
        List<Booking> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT b.id,b.date,b.time,b.client,b.service_id,s.name,b.stylist_id,st.name,b.amount,b.status,b.payment,b.client_id,b.paid_amount,b.due_date,s.commission * b.amount / 100.0 " +
                        "FROM bookings b JOIN services s ON s.id=b.service_id JOIN stylists st ON st.id=b.stylist_id " +
                        "WHERE b.date BETWEEN ? AND ? ORDER BY b.date,b.time",
                new String[]{start, end});
        while (c.moveToNext()) rows.add(bookingFrom(c));
        c.close();
        return rows;
    }

    private Booking bookingFrom(Cursor c) {
        Long clientId = c.isNull(11) ? null : c.getLong(11);
        return new Booking(c.getLong(0), c.getString(1), c.getString(2), dec(c.getString(3)), c.getLong(4), dec(c.getString(5)), c.getLong(6), dec(c.getString(7)), c.getDouble(8), c.getString(9), c.getString(10), clientId, c.getDouble(12), c.getString(13), c.getDouble(14));
    }

    List<Sale> salesBetween(String start, String end) {
        List<Sale> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.id,s.date,s.description,s.amount,s.payment,s.stylist_id,COALESCE(st.name,''),s.client_id,s.paid_amount,s.due_date,s.inventory_id,s.quantity " +
                        "FROM sales s LEFT JOIN stylists st ON st.id=s.stylist_id WHERE s.date BETWEEN ? AND ? ORDER BY s.date DESC,s.id DESC",
                new String[]{start, end});
        while (c.moveToNext()) {
            Long stylistId = c.isNull(5) ? null : c.getLong(5);
            Long clientId = c.isNull(7) ? null : c.getLong(7);
            Long inventoryId = c.isNull(10) ? null : c.getLong(10);
            rows.add(new Sale(c.getLong(0), c.getString(1), dec(c.getString(2)), c.getDouble(3), c.getString(4), stylistId, dec(c.getString(6)), clientId, c.getDouble(8), c.getString(9), inventoryId, c.getInt(11)));
        }
        c.close();
        return rows;
    }

    List<Booking> bookingsForClient(long clientId) {
        List<Booking> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT b.id,b.date,b.time,b.client,b.service_id,s.name,b.stylist_id,st.name,b.amount,b.status,b.payment,b.client_id,b.paid_amount,b.due_date,s.commission * b.amount / 100.0 " +
                        "FROM bookings b JOIN services s ON s.id=b.service_id JOIN stylists st ON st.id=b.stylist_id WHERE b.client_id=? ORDER BY b.date DESC,b.time",
                new String[]{String.valueOf(clientId)});
        while (c.moveToNext()) rows.add(bookingFrom(c));
        c.close();
        return rows;
    }

    List<Sale> salesForClient(long clientId) {
        List<Sale> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.id,s.date,s.description,s.amount,s.payment,s.stylist_id,COALESCE(st.name,''),s.client_id,s.paid_amount,s.due_date,s.inventory_id,s.quantity " +
                        "FROM sales s LEFT JOIN stylists st ON st.id=s.stylist_id WHERE s.client_id=? ORDER BY s.date DESC,s.id DESC",
                new String[]{String.valueOf(clientId)});
        while (c.moveToNext()) {
            Long stylistId = c.isNull(5) ? null : c.getLong(5);
            Long rowClientId = c.isNull(7) ? null : c.getLong(7);
            Long inventoryId = c.isNull(10) ? null : c.getLong(10);
            rows.add(new Sale(c.getLong(0), c.getString(1), dec(c.getString(2)), c.getDouble(3), c.getString(4), stylistId, dec(c.getString(6)), rowClientId, c.getDouble(8), c.getString(9), inventoryId, c.getInt(11)));
        }
        c.close();
        return rows;
    }

    Map<Long, List<Booking>> bookingsForClientIds(List<Long> clientIds) {
        Map<Long, List<Booking>> result = new HashMap<>();
        if (clientIds.isEmpty()) return result;
        for (Long id : clientIds) result.put(id, new ArrayList<>());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < clientIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String[] args = new String[clientIds.size()];
        for (int i = 0; i < clientIds.size(); i++) args[i] = String.valueOf(clientIds.get(i));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT b.id,b.date,b.time,b.client,b.service_id,s.name,b.stylist_id,st.name,b.amount,b.status,b.payment,b.client_id,b.paid_amount,b.due_date,s.commission * b.amount / 100.0 " +
                        "FROM bookings b JOIN services s ON s.id=b.service_id JOIN stylists st ON st.id=b.stylist_id WHERE b.client_id IN (" + placeholders + ") ORDER BY b.date DESC",
                args);
        while (c.moveToNext()) {
            Booking booking = bookingFrom(c);
            Long cid = booking.clientId;
            if (cid != null && result.containsKey(cid)) result.get(cid).add(booking);
        }
        c.close();
        return result;
    }

    Map<Long, List<Sale>> salesForClientIds(List<Long> clientIds) {
        Map<Long, List<Sale>> result = new HashMap<>();
        if (clientIds.isEmpty()) return result;
        for (Long id : clientIds) result.put(id, new ArrayList<>());
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < clientIds.size(); i++) {
            if (i > 0) placeholders.append(",");
            placeholders.append("?");
        }
        String[] args = new String[clientIds.size()];
        for (int i = 0; i < clientIds.size(); i++) args[i] = String.valueOf(clientIds.get(i));
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT s.id,s.date,s.description,s.amount,s.payment,s.stylist_id,COALESCE(st.name,''),s.client_id,s.paid_amount,s.due_date,s.inventory_id,s.quantity " +
                        "FROM sales s LEFT JOIN stylists st ON st.id=s.stylist_id WHERE s.client_id IN (" + placeholders + ") ORDER BY s.date DESC,s.id DESC",
                args);
        while (c.moveToNext()) {
            Long stylistId = c.isNull(5) ? null : c.getLong(5);
            Long rowClientId = c.isNull(7) ? null : c.getLong(7);
            Long inventoryId = c.isNull(10) ? null : c.getLong(10);
            Sale sale = new Sale(c.getLong(0), c.getString(1), dec(c.getString(2)), c.getDouble(3), c.getString(4), stylistId, dec(c.getString(6)), rowClientId, c.getDouble(8), c.getString(9), inventoryId, c.getInt(11));
            if (rowClientId != null && result.containsKey(rowClientId)) result.get(rowClientId).add(sale);
        }
        c.close();
        return result;
    }

    List<Expense> expensesBetween(String start, String end) {
        List<Expense> rows = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,date,category,amount,note FROM expenses WHERE date BETWEEN ? AND ? ORDER BY date DESC,id DESC", new String[]{start, end});
        while (c.moveToNext()) rows.add(new Expense(c.getLong(0), c.getString(1), dec(c.getString(2)), c.getDouble(3), dec(c.getString(4))));
        c.close();
        return rows;
    }

    File backupNow() throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();
        File backupDir = backupDir();
        if (!backupDir.exists()) backupDir.mkdirs();
        String stamp = DATE_TIME_FMT.get().format(new Date());
        File backup = new File(backupDir, "salonflow-" + stamp + ".db.enc");
        cipher.encryptFile(databaseFile(), backup);
        return backup;
    }

    void backupDailyAtEight() {
        Calendar now = Calendar.getInstance();
        if (now.get(Calendar.HOUR_OF_DAY) < 20) return;
        String today = today();
        File marker = new File(context.getExternalFilesDir(null), "last-backup-" + today + ".txt");
        if (marker.exists()) return;
        try {
            backupNow();
            marker.createNewFile();
        } catch (Exception e) {
            Log.e("SalonDatabase", "Daily backup failed", e);
        }
    }

    List<File> backups() {
        File backupDir = backupDir();
        File[] files = backupDir.listFiles((dir, name) -> name.endsWith(".db.enc"));
        List<File> result = new ArrayList<>();
        if (files != null) {
            for (File file : files) result.add(file);
        }
        return result;
    }

    void deleteOldBackups(File keep) {
        File dir = backupDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".db.enc"));
        if (files == null) return;
        for (File f : files) {
            if (!f.equals(keep)) {
                f.delete();
            }
        }
    }

    File latestBackup() {
        List<File> files = backups();
        File latest = null;
        for (File file : files) {
            if (latest == null || file.lastModified() > latest.lastModified()) latest = file;
        }
        return latest;
    }

    void restore(File backup) throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.close();
        cipher.decryptFile(backup, databaseFile());
    }

    File databaseFile() {
        return context.getDatabasePath(DB_NAME);
    }

    private File backupDir() {
        File[] mediaDirs = context.getExternalMediaDirs();
        if (mediaDirs != null && mediaDirs.length > 0 && mediaDirs[0] != null) {
            File dir = new File(mediaDirs[0], "SalonFlowBackups");
            if (!dir.exists()) dir.mkdirs();
            if (dir.canWrite()) return dir;
        }
        return new File(context.getExternalFilesDir(null), "SalonFlowBackups");
    }

    boolean isEmpty() {
        SQLiteDatabase db = getReadableDatabase();
        String[] tables = {"stylists", "services", "clients", "inventory", "bookings", "sales", "expenses"};
        for (String table : tables) {
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
                c.moveToFirst();
                if (c.getLong(0) > 0) return false;
            } catch (Exception e) {
                return false;
            } finally {
                if (c != null) c.close();
            }
        }
        return true;
    }

    private String enc(String value) {
        return cipher.encryptString(value == null ? "" : value);
    }

    private String dec(String value) {
        return cipher.decryptString(value == null ? "" : value);
    }

    static String today() {
        return DATE_FMT.get().format(new Date());
    }

    static class Stylist {
        long id;
        String name;
        double commission;
        String phone;
        Stylist(long id, String name, double commission) { this(id, name, commission, ""); }
        Stylist(long id, String name, double commission, String phone) { this.id = id; this.name = name; this.commission = commission; this.phone = phone == null ? "" : phone; }
    }

    static class Service {
        long id;
        String name;
        double price;
        double commission;
        Service(long id, String name, double price, double commission) { this.id = id; this.name = name; this.price = price; this.commission = commission; }
    }

    static class InventoryItem {
        long id;
        String name;
        String category;
        int onHand;
        int reorderAt;
        double cost;
        double sellingPrice;
        InventoryItem(long id, String name, String category, int onHand, int reorderAt, double cost, double sellingPrice) {
            this.id = id; this.name = name; this.category = category; this.onHand = onHand; this.reorderAt = reorderAt; this.cost = cost; this.sellingPrice = sellingPrice;
        }
    }

    static class StockBatch {
        long id;
        long inventoryId;
        int quantity;
        double purchasePrice;
        String dateAdded;
        int remaining;
        StockBatch(long id, long inventoryId, int quantity, double purchasePrice, String dateAdded, int remaining) {
            this.id = id; this.inventoryId = inventoryId; this.quantity = quantity; this.purchasePrice = purchasePrice; this.dateAdded = dateAdded; this.remaining = remaining;
        }
    }

    static class SaleStockUsage {
        long id;
        long saleId;
        long inventoryId;
        long batchId;
        int quantity;
        double purchasePrice;
        double sellingPrice;
        SaleStockUsage(long id, long saleId, long inventoryId, long batchId, int quantity, double purchasePrice, double sellingPrice) {
            this.id = id; this.saleId = saleId; this.inventoryId = inventoryId; this.batchId = batchId; this.quantity = quantity; this.purchasePrice = purchasePrice; this.sellingPrice = sellingPrice;
        }
    }

    static class Client {
        long id;
        String name;
        String phone;
        Client(long id, String name, String phone) { this.id = id; this.name = name; this.phone = phone; }
    }

    static class Booking {
        long id;
        String date;
        String time;
        String client;
        long serviceId;
        String serviceName;
        long stylistId;
        String stylistName;
        double amount;
        String status;
        String payment;
        Long clientId;
        double paidAmount;
        String dueDate;
        double serviceCommission;
        Booking(long id, String date, String time, String client, long serviceId, String serviceName, long stylistId, String stylistName, double amount, String status, String payment) {
            this(id, date, time, client, serviceId, serviceName, stylistId, stylistName, amount, status, payment, null, "completed".equals(status) ? amount : 0, "", 0);
        }
        Booking(long id, String date, String time, String client, long serviceId, String serviceName, long stylistId, String stylistName, double amount, String status, String payment, Long clientId, double paidAmount, String dueDate) {
            this(id, date, time, client, serviceId, serviceName, stylistId, stylistName, amount, status, payment, clientId, paidAmount, dueDate, 0);
        }
        Booking(long id, String date, String time, String client, long serviceId, String serviceName, long stylistId, String stylistName, double amount, String status, String payment, Long clientId, double paidAmount, String dueDate, double serviceCommission) {
            this.id = id; this.date = date; this.time = time; this.client = client; this.serviceId = serviceId; this.serviceName = serviceName; this.stylistId = stylistId; this.stylistName = stylistName; this.amount = amount; this.status = status; this.payment = payment; this.clientId = clientId; this.paidAmount = paidAmount; this.dueDate = dueDate == null ? "" : dueDate; this.serviceCommission = serviceCommission;
        }
    }

    static class Sale {
        long id;
        String date;
        String description;
        double amount;
        String payment;
        Long stylistId;
        String stylistName;
        Long clientId;
        double paidAmount;
        String dueDate;
        Long inventoryId;
        int quantity;
        Sale(long id, String date, String description, double amount, String payment, Long stylistId, String stylistName) {
            this(id, date, description, amount, payment, stylistId, stylistName, null, amount, "", null, 0);
        }
        Sale(long id, String date, String description, double amount, String payment, Long stylistId, String stylistName, Long clientId, double paidAmount, String dueDate) {
            this(id, date, description, amount, payment, stylistId, stylistName, clientId, paidAmount, dueDate, null, 0);
        }
        Sale(long id, String date, String description, double amount, String payment, Long stylistId, String stylistName, Long clientId, double paidAmount, String dueDate, Long inventoryId, int quantity) {
            this.id = id; this.date = date; this.description = description; this.amount = amount; this.payment = payment; this.stylistId = stylistId; this.stylistName = stylistName; this.clientId = clientId; this.paidAmount = paidAmount; this.dueDate = dueDate == null ? "" : dueDate; this.inventoryId = inventoryId; this.quantity = quantity;
        }
    }

    static class Expense {
        long id;
        String date;
        String category;
        double amount;
        String note;
        Expense(long id, String date, String category, double amount, String note) { this.id = id; this.date = date; this.category = category; this.amount = amount; this.note = note; }
    }
}
