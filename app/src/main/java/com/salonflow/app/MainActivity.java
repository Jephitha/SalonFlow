package com.salonflow.app;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.activity.ComponentActivity;

import com.salonflow.app.R;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;

import java.text.NumberFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends ComponentActivity {
    private static final int BRAND = Color.rgb(39, 76, 67);
    private static final int ACCENT = Color.rgb(212, 91, 67);
    private static final int PAPER = Color.rgb(247, 246, 241);
    private static final int MUTED = Color.rgb(104, 116, 112);
    private static final int DANGER = Color.rgb(161, 63, 50);

    private static final String[] SETTINGS_SECTIONS = {"services", "inventory", "stylists", "security", "notifications", "backup"};
 
    private final NumberFormat money = NumberFormat.getCurrencyInstance(new Locale("en", "KE"));
    private SalonDatabase db;
    private AppSettings settings;
    private LinearLayout root;
    private LinearLayout bottomNav;
    private LinearLayout content;
    private Button floatingActionButton;
    private int selectedTab = 0;
    private Calendar visibleMonth = Calendar.getInstance();
    private Calendar reportMonth = Calendar.getInstance();
    private String selectedDate = SalonDatabase.today();
    private boolean appUnlocked = false;
    private boolean viewingSettings = false;
    private String settingsSection = "home";
    private static final ThreadLocal<SimpleDateFormat> MONTH_YEAR_FMT = ThreadLocal.withInitial(() -> new SimpleDateFormat("MMMM yyyy", Locale.US));
    private static final ThreadLocal<SimpleDateFormat> YEAR_FMT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy", Locale.US));
    private static final ThreadLocal<SimpleDateFormat> MONTH_FMT = ThreadLocal.withInitial(() -> new SimpleDateFormat("MMM", Locale.US));
    private static final ThreadLocal<SimpleDateFormat> DATE_TIME_FMT = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US));
    private final Deque<String> navHistory = new ArrayDeque<>();
    private final Deque<String> settingsNavStack = new ArrayDeque<>();
    private long lastBackPressMs = 0;

    private DriveBackupManager driveBackupManager;
    private Runnable driveSignInCallback;
    private final ActivityResultLauncher<Intent> driveSignInLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                try {
                    if (result.getData() == null) {
                        Toast.makeText(this, "Sign-in cancelled", Toast.LENGTH_SHORT).show();
                    } else {
                        GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData()).getResult();
                        Toast.makeText(this, "Drive: signed in as " + account.getEmail(), Toast.LENGTH_LONG).show();
                    }
                } catch (Exception e) {
                    String msg;
                    int statusCode = 0;
                    Throwable cause = e.getCause();
                    if (cause instanceof ApiException) {
                        statusCode = ((ApiException) cause).getStatusCode();
                    }
                    switch (statusCode) {
                        case 10:
                            msg = "Drive sign-in requires a Google Cloud project with Drive API enabled and the app's SHA-1 fingerprint registered. See HANDOVER.md";
                            break;
                        case 12501:
                            msg = "Sign-in cancelled";
                            break;
                        default:
                            String detail = e.getMessage() != null ? e.getMessage() : "unknown";
                            msg = "Drive sign-in failed (" + statusCode + "): " + detail;
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                if (driveSignInCallback != null) {
                    driveSignInCallback.run();
                    driveSignInCallback = null;
                }
            });

    private final String[] tabs = {"Home", "Bookings", "Clients", "Reports", "Settings"};
    private final String[] tabIcons = {"⌂", "□", "◉", "▤", "⚙"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        money.setMaximumFractionDigits(0);
        db = new SalonDatabase(this);
        settings = new AppSettings(this);
        driveBackupManager = new DriveBackupManager(this);
        db.getWritableDatabase();
        db.backupDailyAtEight();
        scheduleDailyBackup();
        scheduleDailyReminders();
        requestNotificationPermissionIfNeeded();
        renderApp();
        maybeStartOnboarding();
        if (!settings.hasPin()) content.post(() -> {
            if (isFinishing()) return;
            new AlertDialog.Builder(this)
                    .setTitle("Create your Security PIN")
                    .setMessage("A PIN is required to access Settings and protect your data. You can also enable App Lock later for full app protection.")
                    .setPositiveButton("Set PIN", (d, w) -> showSetPinDialog())
                    .setNegativeButton("Remind later", null)
                    .setCancelable(false)
                    .show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settings != null && settings.isAppLockEnabled() && !appUnlocked) {
            if (settings.isWithinGracePeriod()) {
                appUnlocked = true;
            } else {
                showPinUnlockDialog();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        appUnlocked = false;
        viewingSettings = false;
    }

    @Override
    public void onBackPressed() {
        if (!navHistory.isEmpty()) {
            applyState(navHistory.pop());
            renderTabs();
            renderSelectedTab();
            return;
        }
        if (viewingSettings) {
            viewingSettings = false;
            selectedTab = 0;
            settingsSection = "home";
            renderTabs();
            renderSelectedTab();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackPressMs < 1500) {
            super.onBackPressed();
            return;
        }
        lastBackPressMs = now;
        Toast.makeText(this, "Tap back again to exit", Toast.LENGTH_SHORT).show();
    }

    private void renderApp() {
        FrameLayout shell = new FrameLayout(this);
        setContentView(shell);

        root = vertical();
        root.setBackgroundColor(PAPER);
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));

        renderHeader();

        content = vertical();
        content.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, 0, 1);
        contentParams.setMargins(dp(10), 0, dp(10), 0);
        root.addView(content, contentParams);

        bottomNav = new LinearLayout(this);
        bottomNav.setOrientation(LinearLayout.HORIZONTAL);
        bottomNav.setPadding(dp(4), dp(4), dp(4), dp(16));
        bottomNav.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(82));
        navParams.setMargins(dp(8), dp(4), dp(8), dp(14));
        root.addView(bottomNav, navParams);

        floatingActionButton = new Button(this);
        floatingActionButton.setText("+");
        floatingActionButton.setTextColor(Color.WHITE);
        floatingActionButton.setTextSize(24);
        floatingActionButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable fabShape = new GradientDrawable();
        fabShape.setShape(GradientDrawable.OVAL);
        fabShape.setColor(BRAND);
        floatingActionButton.setBackground(fabShape);
        floatingActionButton.setVisibility(View.GONE);
        int fabSize = dp(56);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(fabSize, fabSize, Gravity.END | Gravity.BOTTOM);
        fabParams.setMargins(dp(12), dp(12), dp(14), dp(92));
        shell.addView(floatingActionButton, fabParams);

        renderTabs();
        renderSelectedTab();
    }

    private void renderHeader() {
        LinearLayout header = vertical();
        header.setBackgroundResource(R.drawable.header_bg);
        header.setPadding(dp(18), dp(28), dp(18), dp(10));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("SalonFlow", 25, Color.WHITE, Typeface.BOLD);
        top.addView(brand, new LinearLayout.LayoutParams(-1, -2, 1));
        View bellView = NotificationBellFactory.create(this);
        top.addView(bellView, wrapParams());
        header.addView(top);
        root.addView(header);
    }

    private void renderTabs() {
        bottomNav.removeAllViews();
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            LinearLayout tab = vertical();
            tab.setGravity(Gravity.CENTER);
            tab.setBackgroundColor(Color.WHITE);
            TextView icon = text(tabIcons[i], 22, i == selectedTab ? ACCENT : MUTED, Typeface.BOLD);
            icon.setGravity(Gravity.CENTER);
            TextView label = text(tabs[i], 11, i == selectedTab ? ACCENT : MUTED, i == selectedTab ? Typeface.BOLD : Typeface.NORMAL);
            label.setGravity(Gravity.CENTER);
            tab.addView(icon);
            tab.addView(label);
            tab.setOnClickListener(v -> {
                if (viewingSettings) {
                    if (index == 4) {
                        if (!settings.hasPin()) {
                            showSetPinDialog();
                        } else if (settings.isSettingsPinRequired() && !settings.isWithinGracePeriod()) {
                            promptForSettingsPinAndThen(() -> { settingsSection = "home"; renderTabs(); renderSelectedTab(); });
                        }
                        return;
                    }
                    viewingSettings = false;
                    selectedTab = index;
                    renderTabs();
                    renderSelectedTab();
                } else if (index == 4) {
                    if (!settings.hasPin()) {
                        showSetPinDialog();
                    } else if (settings.isSettingsPinRequired() && !settings.isWithinGracePeriod()) {
                        promptForSettingsPinAndThen(() -> { selectedTab = 4; viewingSettings = true; settingsSection = "home"; renderTabs(); renderSelectedTab(); });
                    } else {
                        switchTabSilent(index);
                    }
                } else {
                    switchTabSilent(index);
                }
            });
            bottomNav.addView(tab, weightedParams(1, 0, 0, 0, 0));
        }
    }

    private void renderSelectedTab() {
        content.removeAllViews();
        if (viewingSettings) {
            renderSettingsHub();
            renderFloatingActionButton();
            return;
        }
        if (selectedTab == 0) renderHome();
        if (selectedTab == 1) renderBookings();
        if (selectedTab == 2) renderClients();
        if (selectedTab == 3) renderReports();
        if (selectedTab == 4) { viewingSettings = true; settingsSection = "home"; renderTabs(); renderSelectedTab(); return; }
        renderFloatingActionButton();
    }

    private void switchTabSilent(int newTab) {
        if (newTab < 0 || newTab >= tabs.length || newTab == selectedTab) return;
        pushState();
        selectedTab = newTab;
        viewingSettings = false;
        renderTabs();
        content.setAlpha(0.6f);
        renderSelectedTab();
        content.animate().alpha(1f).setDuration(100).start();
    }

    private void switchTabAnimated(int newTab) {
        if (newTab < 0 || newTab >= tabs.length) return;
        if (newTab == selectedTab) return;

        if (newTab == 4) {
            if (!settings.hasPin()) {
                showSetPinDialog();
                return;
            }
            if (settings.isSettingsPinRequired() && !settings.isWithinGracePeriod()) {
                promptForSettingsPinAndThen(() -> { selectedTab = 4; viewingSettings = true; settingsSection = "home"; renderTabs(); renderSelectedTab(); });
                return;
            }
        }

        boolean goLeft = newTab > selectedTab;
        float exitX = goLeft ? -content.getWidth() : content.getWidth();
        content.animate().translationX(exitX).setDuration(150).withEndAction(() -> {
            content.setTranslationX(0);
            pushState();
            selectedTab = newTab;
            viewingSettings = false;
            renderTabs();
            renderSelectedTab();
            if (selectedTab != 4) {
                content.setTranslationX(-exitX);
                content.animate().translationX(0).setDuration(150).start();
            }
        }).start();
    }

    private void renderHome() {
        content.addView(HomeComposeFactory.create(this, db), new LinearLayout.LayoutParams(-1, -2));
    }

    private void renderBookings() {
        content.addView(BookingsComposeFactory.create(this, db, selectedDate), new LinearLayout.LayoutParams(-1, -2));
    }

    public void openBookingFromCompose(String date) {
        selectedDate = date;
        showBookingDialog(null);
    }

    public void openBookingActionsFromCompose(SalonDatabase.Booking booking) {
        selectedDate = booking.date;
        showBookingActions(booking);
    }

    private void showBookingForClient(SalonDatabase.Client client) {
        showBookingDialog(null, client);
    }

    private void showBookingDialog(SalonDatabase.Booking booking) {
        showBookingDialog(booking, null);
    }

    private void renderMonthControls() {
        LinearLayout controls = row();
        Button prev = button("<", false);
        Button next = button(">", false);
        TextView month = text(MONTH_YEAR_FMT.get().format(visibleMonth.getTime()), 18, Color.rgb(24, 33, 31), Typeface.BOLD);
        month.setGravity(Gravity.CENTER);
        prev.setOnClickListener(v -> { visibleMonth.add(Calendar.MONTH, -1); renderSelectedTab(); });
        next.setOnClickListener(v -> { visibleMonth.add(Calendar.MONTH, 1); renderSelectedTab(); });
        controls.addView(prev, weightedParams(0.4f, 0, 0, dp(6), dp(8)));
        controls.addView(month, weightedParams(1.6f, 0, 0, dp(6), dp(8)));
        controls.addView(next, weightedParams(0.4f, 0, 0, 0, dp(8)));
        content.addView(controls);
    }

    private void renderCalendar() {
        Calendar cal = (Calendar) visibleMonth.clone();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        int firstDay = cal.get(Calendar.DAY_OF_WEEK) - 1;
        int maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        Map<String, Integer> counts = new HashMap<>();
        for (SalonDatabase.Booking booking : db.bookingsBetween(SalonUtils.monthStart(visibleMonth), SalonUtils.monthEnd(visibleMonth))) {
            counts.put(booking.date, counts.getOrDefault(booking.date, 0) + 1);
        }
        String[] names = {"S", "M", "T", "W", "T", "F", "S"};
        LinearLayout header = row();
        for (String name : names) {
            TextView d = text(name, 12, MUTED, Typeface.BOLD);
            d.setGravity(Gravity.CENTER);
            header.addView(d, weightedParams(1, dp(2), 0, dp(2), dp(4)));
        }
        content.addView(header);
        int day = 1;
        for (int week = 0; week < 6; week++) {
            LinearLayout line = row();
            for (int dow = 0; dow < 7; dow++) {
                if ((week == 0 && dow < firstDay) || day > maxDay) {
                    TextView blank = text("", 14, MUTED, Typeface.NORMAL);
                    line.addView(blank, weightedParams(1, dp(2), dp(2), dp(2), dp(2)));
                } else {
                    String date = dateFor(day);
                    FrameLayout dayCell = new FrameLayout(this);
                    dayCell.setMinimumHeight(dp(52));
                    dayCell.setBackgroundColor(date.equals(selectedDate) ? BRAND : Color.rgb(248, 250, 248));
                    TextView cell = text(String.valueOf(day), 14, date.equals(selectedDate) ? Color.WHITE : Color.rgb(24, 33, 31), Typeface.BOLD);
                    cell.setGravity(Gravity.CENTER);
                    dayCell.addView(cell, new FrameLayout.LayoutParams(-1, -1));
                    if (counts.containsKey(date)) {
                        TextView dot = text("•", 22, date.equals(selectedDate) ? Color.WHITE : ACCENT, Typeface.BOLD);
                        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(-2, -2, Gravity.END | Gravity.TOP);
                        dotParams.setMargins(0, dp(-2), dp(5), 0);
                        dayCell.addView(dot, dotParams);
                    }
                    final String clickedDate = date;
                    dayCell.setOnClickListener(v -> {
                        selectedDate = clickedDate;
                        if (db.bookingsForDate(clickedDate).isEmpty()) showBookingDialog(null); else renderSelectedTab();
                    });
                    line.addView(dayCell, weightedParams(1, dp(2), dp(2), dp(2), dp(2)));
                    day++;
                }
            }
            content.addView(line);
        }
    }

    private void addBookingRow(SalonDatabase.Booking booking) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info = vertical();
        info.addView(text(booking.time + "  " + booking.client, 15, Color.rgb(24, 33, 31), Typeface.BOLD));
        info.addView(text(booking.serviceName + " with " + booking.stylistName + " • " + booking.payment, 12, MUTED, Typeface.NORMAL));
        row.addView(info, weightedParams(1.9f, 0, 0, dp(8), 0));
        LinearLayout rightCol = vertical();
        rightCol.setGravity(Gravity.END);
        rightCol.addView(text(formatMoney(booking.amount), 14, Color.rgb(24, 33, 31), Typeface.BOLD));
        rightCol.addView(statusPill(booking.status));
        row.addView(rightCol, weightedParams(0.8f, 0, 0, 0, 0));
        row.setOnClickListener(v -> showBookingActions(booking));
        content.addView(row, listParams());
    }

    private void confirmDelete(String title, String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Delete", (d, w) -> onConfirm.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showBookingActions(SalonDatabase.Booking booking) {
        String[] actions = {"Edit booking", "Mark completed", "Mark pending", "Cancel booking", "Delete booking"};
        new AlertDialog.Builder(this)
                .setTitle(booking.client)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showBookingDialog(booking);
                    if (which == 1) { booking.status = "completed"; db.saveBooking(booking); rerender(); }
                    if (which == 2) { booking.status = "pending"; db.saveBooking(booking); rerender(); }
                    if (which == 3) { booking.status = "cancelled"; db.saveBooking(booking); rerender(); }
                    if (which == 4) {
                        if ("completed".equals(booking.status)) {
                            Toast.makeText(this, "Completed bookings cannot be deleted", Toast.LENGTH_SHORT).show();
                        } else {
                            confirmDelete("Delete booking", "Delete this booking for " + booking.client + "?", () -> { db.deleteBooking(booking.id); rerender(); });
                        }
                    }
                }).show();
    }

    private void showBookingDialog(SalonDatabase.Booking booking, SalonDatabase.Client preselectedClient) {
        List<SalonDatabase.Service> services = db.services();
        List<SalonDatabase.Stylist> stylists = db.stylists();
        if (stylists.isEmpty()) {
            Toast.makeText(this, "Add at least one stylist first", Toast.LENGTH_LONG).show();
            return;
        }
        if (services.isEmpty()) {
            services.add(new SalonDatabase.Service(0, "No service", 0, 0.0));
        }
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Date"));
        EditText date = input("", booking == null ? selectedDate : booking.date);
        setupDatePicker(date);
        form.addView(date);
        form.addView(label("Time"));
        EditText time = input("", booking == null ? "09:00" : booking.time);
        setupTimePicker(time);
        form.addView(time);
        List<SalonDatabase.Client> clients = db.clients();
        Spinner clientSpinner = null;
        EditText clientEdit = null;
        if (booking == null) {
            clientSpinner = spinner(clientOptions(clients));
            if (preselectedClient != null) {
                for (int i = 0; i < clients.size(); i++) {
                    if (clients.get(i).id == preselectedClient.id) {
                        clientSpinner.setSelection(i + 1);
                        break;
                    }
                }
            }
        } else {
            clientEdit = input("", booking.client);
        }
        Spinner serviceSpinner = spinner(names(services));
        Spinner stylistSpinner = spinner(stylistNames(stylists, true));
        Spinner statusSpinner = spinner(new String[]{"pending", "completed", "cancelled"});
        Spinner paymentSpinner = spinner(new String[]{"Cash", "Mobile money"});
        if (booking != null) {
            serviceSpinner.setSelection(indexOfService(services, booking.serviceId));
            stylistSpinner.setSelection(indexOfStylist(stylists, booking.stylistId));
            statusSpinner.setSelection(indexOf(new String[]{"pending", "completed", "cancelled"}, booking.status));
            paymentSpinner.setSelection(indexOf(new String[]{"Cash", "Mobile money"}, booking.payment));
        }
        if (booking == null) {
            form.addView(label("Customer"));
            form.addView(clientSpinner);
        } else {
            form.addView(label("Customer"));
            form.addView(clientEdit);
        }
        form.addView(label("Service")); form.addView(serviceSpinner);
        form.addView(label("Stylist")); form.addView(stylistSpinner);
        form.addView(label("Amount"));
        EditText amount = numberInput(booking == null ? String.valueOf((int) (services.get(0).price / 100.0)) : String.valueOf((int) (booking.amount / 100.0)));
        form.addView(amount);
        serviceSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (booking == null) amount.setText(String.valueOf((int) (services.get(position).price / 100.0)));
            }
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        form.addView(label("Status")); form.addView(statusSpinner);
        form.addView(label("Payment")); form.addView(paymentSpinner);
        EditText paidInput = null;
        EditText dueInput = null;
        if (booking == null && preselectedClient != null) {
            form.addView(label("Paid now"));
            paidInput = numberInput("0");
            form.addView(paidInput);
            form.addView(label("Due date"));
            dueInput = input("", SalonDatabase.today());
            setupDatePicker(dueInput);
            form.addView(dueInput);
        }
        final EditText fPaid = paidInput;
        final EditText fDue = dueInput;
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(booking == null ? "Add booking" : "Edit booking")
                .setView(scroll)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        Spinner finalClientSpinner = clientSpinner;
        EditText finalClientEdit = clientEdit;
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    SalonDatabase.Service service = services.get(serviceSpinner.getSelectedItemPosition());
                    SalonDatabase.Stylist stylist = stylists.get(stylistSpinner.getSelectedItemPosition());
                    String clientName;
                    Long clientId;
                    if (booking == null && finalClientSpinner != null) {
                        int pos = finalClientSpinner.getSelectedItemPosition();
                        if (pos == 0) {
                            clientName = "Walk-in";
                            clientId = null;
                        } else {
                            SalonDatabase.Client selected = clients.get(pos - 1);
                            clientName = selected.name;
                            clientId = selected.id;
                        }
                    } else {
                        clientName = valueOr(finalClientEdit.getText().toString(), "Client");
                        clientId = booking.clientId;
                    }
                    String status = statusSpinner.getSelectedItem().toString();
                    double total = parseCents(amount.getText().toString(), service.price);
                    String oldStatus = booking != null ? booking.status : null;
                    if (booking != null && !status.equals(oldStatus)) {
                        String msg = "Change status from '" + oldStatus + "' to '" + status + "'?";
                        new AlertDialog.Builder(this)
                                .setTitle("Confirm status change")
                                .setMessage(msg)
                                .setPositiveButton("Confirm", (c, w) -> {
                                    double paidAmount = booking.paidAmount;
                                    SalonDatabase.Booking row = new SalonDatabase.Booking(booking.id, valueOr(date.getText().toString(), selectedDate), valueOr(time.getText().toString(), "09:00"), clientName, service.id, service.name, stylist.id, stylist.name, total, status, paymentSpinner.getSelectedItem().toString(), clientId, paidAmount, booking.dueDate);
                                    selectedDate = row.date;
                                    db.saveBooking(row);
                                    rerender();
                                    dialog.dismiss();
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                        return;
                    }
                    double paidAmount;
                    if (booking == null && preselectedClient != null) {
                        paidAmount = Math.min(total, parseCents(fPaid.getText().toString(), 0));
                    } else {
                        paidAmount = booking == null ? total : booking.paidAmount;
                    }
                    String dueDate = booking == null ? (preselectedClient != null ? valueOr(fDue.getText().toString(), "") : "") : booking.dueDate;
                    SalonDatabase.Booking row = new SalonDatabase.Booking(booking == null ? 0 : booking.id, valueOr(date.getText().toString(), selectedDate), valueOr(time.getText().toString(), "09:00"), clientName, service.id, service.name, stylist.id, stylist.name, total, status, paymentSpinner.getSelectedItem().toString(), clientId, paidAmount, dueDate);
                    selectedDate = row.date;
                    db.saveBooking(row);
                    rerender();
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void showSaleDialog(SalonDatabase.Sale sale) {
        List<SalonDatabase.InventoryItem> inventory = db.inventory();
        String[] itemNames = inventoryNames(inventory);
        List<SalonDatabase.Client> clients = db.clients();
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Date"));
        EditText date = input("", sale == null ? SalonDatabase.today() : sale.date);
        setupDatePicker(date);
        form.addView(date);
        form.addView(label("Customer"));
        Spinner clientSpinner = spinner(clientOptions(clients));
        form.addView(clientSpinner);
        TextView itemsHeader = label("Items sold");
        form.addView(itemsHeader);
        LinearLayout itemsContainer = vertical();
        form.addView(itemsContainer);
        List<LinearLayout> itemRows = new ArrayList<>();
        List<Spinner> productSpinners = new ArrayList<>();
        List<EditText> quantityInputs = new ArrayList<>();
        TextView totalView = text("Total: " + formatMoney(0), 14, Color.rgb(24, 33, 31), Typeface.BOLD);
        totalView.setPadding(0, dp(6), 0, dp(6));
        form.addView(totalView);
        Runnable updateTotal = () -> {
            long totalCents = 0;
            for (int i = 0; i < productSpinners.size(); i++) {
                int idx = productSpinners.get(i).getSelectedItemPosition();
                if (idx > 0 && idx - 1 < inventory.size()) {
                    double sp = inventory.get(idx - 1).sellingPrice;
                    int q = (int) parseAmount(quantityInputs.get(i).getText().toString(), 0);
                    totalCents += (long) (sp * q);
                }
            }
            totalView.setText("Total: " + formatMoney(totalCents));
        };
        Runnable addItemRow = new Runnable() {
            @Override
            public void run() {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                Spinner sp = spinner(itemNames);
                EditText qty = numberInput("1");
                qty.setLayoutParams(new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));
                qty.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(Editable s) { updateTotal.run(); }
                });
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { updateTotal.run(); }
                    public void onNothingSelected(AdapterView<?> p) {}
                });
                Button removeBtn = new Button(MainActivity.this);
                removeBtn.setText("X");
                removeBtn.setTextColor(DANGER);
                removeBtn.setBackground(null);
                removeBtn.setPadding(dp(6), dp(4), dp(6), dp(4));
                int rowIdx = itemRows.size();
                removeBtn.setOnClickListener(v -> {
                    itemsContainer.removeView(row);
                    productSpinners.remove(rowIdx);
                    quantityInputs.remove(rowIdx);
                    itemRows.remove(rowIdx);
                    updateTotal.run();
                });
                LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                spParams.setMargins(0, 0, dp(6), 0);
                row.addView(sp, spParams);
                row.addView(qty);
                row.addView(removeBtn);
                itemsContainer.addView(row);
                itemRows.add(row);
                productSpinners.add(sp);
                quantityInputs.add(qty);
                updateTotal.run();
            }
        };
        Button addItemBtn = new Button(MainActivity.this);
        addItemBtn.setText("+ Add item");
        addItemBtn.setTextColor(BRAND);
        addItemBtn.setBackground(null);
        addItemBtn.setGravity(Gravity.START);
        addItemBtn.setPadding(0, dp(4), 0, dp(4));
        addItemBtn.setOnClickListener(v -> addItemRow.run());
        form.addView(addItemBtn);
        form.addView(label("Payment"));
        Spinner payment = spinner(new String[]{"Cash", "Mobile money"});
        form.addView(payment);
        if (sale != null) payment.setSelection(indexOf(new String[]{"Cash", "Mobile money"}, sale.payment));
        addItemRow.run();
        android.widget.ScrollView scroll = new android.widget.ScrollView(MainActivity.this);
        scroll.addView(form);
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this)
                .setTitle(sale == null ? "Record sale" : "Update sale")
                .setView(scroll)
                .setPositiveButton("Save", (dialog, which) -> {
                    double totalAmount = 0;
                    List<Long> itemIds = new ArrayList<>();
                    List<Integer> itemQtys = new ArrayList<>();
                    StringBuilder descParts = new StringBuilder();
                    for (int i = 0; i < productSpinners.size(); i++) {
                        int idx = productSpinners.get(i).getSelectedItemPosition();
                        if (idx > 0 && idx - 1 < inventory.size()) {
                            SalonDatabase.InventoryItem inv = inventory.get(idx - 1);
                            int q = (int) parseAmount(quantityInputs.get(i).getText().toString(), 0);
                            if (q > 0) {
                                if (q > inv.onHand) {
                                    Toast.makeText(MainActivity.this, "Only " + inv.onHand + " " + inv.name + " in stock (" + q + " requested)", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                itemIds.add(inv.id);
                                itemQtys.add(q);
                                totalAmount += inv.sellingPrice * q;
                                if (descParts.length() > 0) descParts.append(", ");
                                descParts.append(q).append("x ").append(inv.name);
                            }
                        }
                    }
                    if (itemIds.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Add at least one product with quantity > 0", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Long selClientId = clientSpinner.getSelectedItemPosition() > 0 ? clients.get(clientSpinner.getSelectedItemPosition() - 1).id : null;
                    long saleId = db.saveSale(new SalonDatabase.Sale(sale == null ? 0 : sale.id, valueOr(date.getText().toString(), SalonDatabase.today()), descParts.toString(), totalAmount, payment.getSelectedItem().toString(), null, "", selClientId, totalAmount, "", null, 0));
                    for (int i = 0; i < itemIds.size(); i++) {
                        db.deductFromBatches(itemIds.get(i), itemQtys.get(i), saleId);
                    }
                    rerender();
                })
                .setNegativeButton("Cancel", null);
        if (sale != null) builder.setNeutralButton("Delete", (dialog, which) -> { confirmDelete("Delete sale", "Delete this sale?", () -> { db.restoreBatchDeductions(sale.id); db.deleteSale(sale.id); rerender(); }); });
        builder.show();
    }

    private void showClientBehaviour(SalonDatabase.Client client) {
        List<SalonDatabase.Booking> bookings = db.bookingsForClient(client.id);
        List<SalonDatabase.Sale> sales = db.salesForClient(client.id);
        double cash = 0;
        double mobile = 0;
        double total = 0;
        double paid = 0;
        int visits = bookings.size() + sales.size();
        int overdue = 0;
        List<String> bookingHistory = new ArrayList<>();
        List<String> saleHistory = new ArrayList<>();
        for (SalonDatabase.Booking booking : bookings) {
            total += booking.amount;
            paid += booking.paidAmount;
            if ("Cash".equals(booking.payment)) cash += booking.paidAmount; else mobile += booking.paidAmount;
            if (balance(booking.amount, booking.paidAmount) > 0) overdue = Math.max(overdue, overdueDays(booking.dueDate));
            bookingHistory.add(booking.date + " • " + booking.serviceName + " • " + formatMoney(booking.amount));
        }
        for (SalonDatabase.Sale sale : sales) {
            total += sale.amount;
            paid += sale.paidAmount;
            if ("Cash".equals(sale.payment)) cash += sale.paidAmount; else mobile += sale.paidAmount;
            if (balance(sale.amount, sale.paidAmount) > 0) overdue = Math.max(overdue, overdueDays(sale.dueDate));
            saleHistory.add(sale.date + " • " + sale.description + " • " + formatMoney(sale.amount));
        }
        String message = "Visits: " + visits +
                "\nTotal value: " + formatMoney(total) +
                "\nPaid: " + formatMoney(paid) +
                "\nBalance: " + formatMoney(total - paid) +
                "\nLongest overdue: " + overdue + " days" +
                "\nCash paid: " + formatMoney(cash) +
                "\nMobile money paid: " + formatMoney(mobile) +
                "\n\nPrevious visits:\n" + (bookingHistory.isEmpty() ? "None" : joinLines(bookingHistory, 8)) +
                "\n\nPrevious purchases:\n" + (saleHistory.isEmpty() ? "None" : joinLines(saleHistory, 8));
        new AlertDialog.Builder(this).setTitle(client.name + " behaviour").setMessage(message).setPositiveButton("OK", null).show();
    }

    private void showExpenseDialog(SalonDatabase.Expense expense) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Date"));
        EditText date = input("", expense == null ? SalonDatabase.today() : expense.date);
        setupDatePicker(date);
        form.addView(date);
        form.addView(label("Category"));
        EditText category = input("", expense == null ? "Rent" : expense.category);
        form.addView(category);
        form.addView(label("Amount"));
        EditText amount = numberInput(expense == null ? "1000" : String.valueOf((int) (expense.amount / 100.0)));
        form.addView(amount);
        form.addView(label("Note"));
        EditText note = input("", expense == null ? "" : expense.note);
        form.addView(note);
        new AlertDialog.Builder(this)
                .setTitle(expense == null ? "Record expense" : "Update expense")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    db.saveExpense(new SalonDatabase.Expense(expense == null ? 0 : expense.id, valueOr(date.getText().toString(), SalonDatabase.today()), valueOr(category.getText().toString(), "Expense"), parseCents(amount.getText().toString(), 0), note.getText().toString()));
                    rerender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClientDialog(SalonDatabase.Client client) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Customer name"));
        EditText name = input("", client == null ? "" : client.name);
        form.addView(name);
        form.addView(label("Phone number"));
        EditText phone = input("", client == null ? "" : client.phone);
        form.addView(phone);
        form.addView(text("Kenyan format: 0712345678", 12, MUTED, Typeface.NORMAL));
        new AlertDialog.Builder(this)
                .setTitle(client == null ? "Add customer" : "Edit customer")
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    String phoneVal = phone.getText().toString().trim();
                    if (!phoneVal.isEmpty() && !isValidKenyanPhone(phoneVal)) {
                        Toast.makeText(this, "Enter a valid Kenyan phone number (e.g. 0712345678)", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (client == null) {
                        String newName = valueOr(name.getText().toString(), "Customer");
                        if (!phoneVal.isEmpty()) {
                            SalonDatabase.Client existing = clientByPhone(phoneVal);
                            if (existing != null) {
                                if (!existing.name.equals(newName)) {
                                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                                    builder.setTitle("Phone number exists")
                                            .setMessage("This number is saved under \"" + existing.name + "\". Update name to \"" + newName + "\"?")
                                            .setPositiveButton("Update name", (c, w2) -> {
                                                db.saveClient(new SalonDatabase.Client(existing.id, newName, phoneVal));
                                                rerender();
                                            })
                                            .setNegativeButton("Cancel", null)
                                            .show();
                                    return;
                                }
                            }
                        }
                        db.saveClient(new SalonDatabase.Client(0, newName, phoneVal));
                        rerender();
                    } else if (client != null) {
                        db.saveClient(new SalonDatabase.Client(client.id, valueOr(name.getText().toString(), "Customer"), phoneVal));
                        rerender();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClientSaleDialog(SalonDatabase.Client client, SalonDatabase.Sale sale) {
        List<SalonDatabase.InventoryItem> inventory = db.inventory();
        String[] itemNames = inventoryNames(inventory);
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Date"));
        EditText date = input("", sale == null ? SalonDatabase.today() : sale.date);
        setupDatePicker(date);
        form.addView(date);
        TextView itemsHeader = label("Items sold");
        form.addView(itemsHeader);
        LinearLayout itemsContainer = vertical();
        form.addView(itemsContainer);
        List<LinearLayout> itemRows = new ArrayList<>();
        List<Spinner> productSpinners = new ArrayList<>();
        List<EditText> quantityInputs = new ArrayList<>();
        TextView totalView = text("Total: " + formatMoney(0), 14, Color.rgb(24, 33, 31), Typeface.BOLD);
        totalView.setPadding(0, dp(6), 0, dp(6));
        form.addView(totalView);
        Runnable updateTotal = () -> {
            long totalCents = 0;
            for (int i = 0; i < productSpinners.size(); i++) {
                int idx = productSpinners.get(i).getSelectedItemPosition();
                if (idx > 0 && idx - 1 < inventory.size()) {
                    double sp = inventory.get(idx - 1).sellingPrice;
                    int q = (int) parseAmount(quantityInputs.get(i).getText().toString(), 0);
                    totalCents += (long) (sp * q);
                }
            }
            totalView.setText("Total: " + formatMoney(totalCents));
        };
        Runnable addItemRow = new Runnable() {
            @Override
            public void run() {
                LinearLayout row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                Spinner sp = spinner(itemNames);
                EditText qty = numberInput("1");
                qty.setLayoutParams(new LinearLayout.LayoutParams(dp(70), LinearLayout.LayoutParams.WRAP_CONTENT));
                qty.addTextChangedListener(new TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    public void afterTextChanged(Editable s) { updateTotal.run(); }
                });
                sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { updateTotal.run(); }
                    public void onNothingSelected(AdapterView<?> p) {}
                });
                Button removeBtn = new Button(MainActivity.this);
                removeBtn.setText("X");
                removeBtn.setTextColor(DANGER);
                removeBtn.setBackground(null);
                removeBtn.setPadding(dp(6), dp(4), dp(6), dp(4));
                int rowIdx = itemRows.size();
                removeBtn.setOnClickListener(v -> {
                    itemsContainer.removeView(row);
                    productSpinners.remove(rowIdx);
                    quantityInputs.remove(rowIdx);
                    itemRows.remove(rowIdx);
                    updateTotal.run();
                });
                LinearLayout.LayoutParams spParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
                spParams.setMargins(0, 0, dp(6), 0);
                row.addView(sp, spParams);
                row.addView(qty);
                row.addView(removeBtn);
                itemsContainer.addView(row);
                itemRows.add(row);
                productSpinners.add(sp);
                quantityInputs.add(qty);
                updateTotal.run();
            }
        };
        Button addItemBtn = new Button(MainActivity.this);
        addItemBtn.setText("+ Add item");
        addItemBtn.setTextColor(BRAND);
        addItemBtn.setBackground(null);
        addItemBtn.setGravity(Gravity.START);
        addItemBtn.setPadding(0, dp(4), 0, dp(4));
        addItemBtn.setOnClickListener(v -> addItemRow.run());
        form.addView(addItemBtn);
        form.addView(label("Payment"));
        Spinner payment = spinner(new String[]{"Cash", "Mobile money"});
        form.addView(payment);
        if (sale != null) payment.setSelection(indexOf(new String[]{"Cash", "Mobile money"}, sale.payment));
        EditText paidInput = null;
        EditText dueInput = null;
        if (sale == null) {
            form.addView(label("Paid now"));
            paidInput = numberInput("0");
            form.addView(paidInput);
            form.addView(label("Due date"));
            dueInput = input("", SalonDatabase.today());
            setupDatePicker(dueInput);
            form.addView(dueInput);
        }
        final EditText fPaid = paidInput;
        final EditText fDue = dueInput;
        addItemRow.run();
        android.widget.ScrollView scroll = new android.widget.ScrollView(MainActivity.this);
        scroll.addView(form);
        new AlertDialog.Builder(MainActivity.this)
                .setTitle((sale == null ? "Sale for " : "Update sale for ") + client.name)
                .setView(scroll)
                .setPositiveButton("Save", (dialog, which) -> {
                    double totalAmount = 0;
                    List<Long> itemIds = new ArrayList<>();
                    List<Integer> itemQtys = new ArrayList<>();
                    StringBuilder descParts = new StringBuilder();
                    for (int i = 0; i < productSpinners.size(); i++) {
                        int idx = productSpinners.get(i).getSelectedItemPosition();
                        if (idx > 0 && idx - 1 < inventory.size()) {
                            SalonDatabase.InventoryItem inv = inventory.get(idx - 1);
                            int q = (int) parseAmount(quantityInputs.get(i).getText().toString(), 0);
                            if (q > 0) {
                                if (q > inv.onHand) {
                                    Toast.makeText(MainActivity.this, "Only " + inv.onHand + " " + inv.name + " in stock (" + q + " requested)", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                itemIds.add(inv.id);
                                itemQtys.add(q);
                                totalAmount += inv.sellingPrice * q;
                                if (descParts.length() > 0) descParts.append(", ");
                                descParts.append(q).append("x ").append(inv.name);
                            }
                        }
                    }
                    if (itemIds.isEmpty()) {
                        Toast.makeText(MainActivity.this, "Add at least one product with quantity > 0", Toast.LENGTH_LONG).show();
                        return;
                    }
                    double paidAmount = sale == null ? Math.min(totalAmount, parseCents(fPaid.getText().toString(), 0)) : totalAmount;
                    String dueDate = sale == null ? valueOr(fDue.getText().toString(), "") : "";
                    long saleId = db.saveSale(new SalonDatabase.Sale(sale == null ? 0 : sale.id, valueOr(date.getText().toString(), SalonDatabase.today()), descParts.toString(), totalAmount, payment.getSelectedItem().toString(), null, "", client.id, paidAmount, dueDate, null, 0));
                    for (int i = 0; i < itemIds.size(); i++) {
                        db.deductFromBatches(itemIds.get(i), itemQtys.get(i), saleId);
                    }
                    rerender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showClientPaymentDialog(SalonDatabase.Client client) {
        List<SalonDatabase.Booking> bookings = db.bookingsForClient(client.id);
        List<SalonDatabase.Sale> sales = db.salesForClient(client.id);
        double outstanding = SalonUtils.outstandingBalance(bookings, sales);
        if (outstanding <= 0) {
            Toast.makeText(this, "No outstanding balance for " + client.name, Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Outstanding: " + formatMoney(outstanding)));
        form.addView(label("Date"));
        EditText date = input("", SalonDatabase.today());
        setupDatePicker(date);
        form.addView(date);
        form.addView(label("Amount"));
        EditText amount = numberInput(String.valueOf((int) (outstanding / 100.0)));
        form.addView(amount);
        form.addView(label("Payment method"));
        Spinner payment = spinner(new String[]{"Cash", "Mobile money"});
        form.addView(payment);
        new AlertDialog.Builder(this)
                .setTitle("Payment from " + client.name)
                .setView(form)
                .setPositiveButton("Save", (d, w) -> {
                    double paidAmount = parseCents(amount.getText().toString(), 0);
                    if (paidAmount <= 0) { Toast.makeText(this, "Enter a valid amount", Toast.LENGTH_SHORT).show(); return; }
                    double remaining = paidAmount;
                    for (SalonDatabase.Booking b : bookings) {
                        if (remaining <= 0) break;
                        double bal = balance(b.amount, b.paidAmount);
                        if (bal > 0) {
                            double pay = Math.min(remaining, bal);
                            db.saveBooking(new SalonDatabase.Booking(b.id, b.date, b.time, b.client, b.serviceId, b.serviceName, b.stylistId, b.stylistName, b.amount, b.status, b.payment, b.clientId, b.paidAmount + pay, b.dueDate, b.serviceCommission));
                            remaining -= pay;
                        }
                    }
                    for (SalonDatabase.Sale s : sales) {
                        if (remaining <= 0) break;
                        double bal = balance(s.amount, s.paidAmount);
                        if (bal > 0) {
                            double pay = Math.min(remaining, bal);
                            db.saveSale(new SalonDatabase.Sale(s.id, s.date, s.description, s.amount, s.payment, s.stylistId, s.stylistName, s.clientId, s.paidAmount + pay, s.dueDate, s.inventoryId, s.quantity));
                            remaining -= pay;
                        }
                    }
                    rerender();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderClients() {
        content.addView(ClientsComposeFactory.create(this, db), new LinearLayout.LayoutParams(-1, -2));
    }

    public void showClientOptions(SalonDatabase.Client client) {
        new AlertDialog.Builder(this)
                .setTitle(client.name)
                .setItems(new String[]{"Add booking", "Record sale", "Record payment", "View history", "Edit customer", "Delete customer"}, (dialog, which) -> {
                    if (which == 0) showBookingForClient(client);
                    else if (which == 1) showClientSaleDialog(client, null);
                    else if (which == 2) showClientPaymentDialog(client);
                    else if (which == 3) showClientBehaviour(client);
                    else if (which == 4) showClientDialog(client);
                    else if (which == 5) confirmDelete("Delete customer", "Delete " + client.name + "? This will not delete their bookings or sales.", () -> { db.deleteClient(client.id); rerender(); });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void renderReports() {
        content.addView(ReportsComposeFactory.create(this, db), new LinearLayout.LayoutParams(-1, -2));
    }

    public void showInventoryDialog(SalonDatabase.InventoryItem item) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Name"));
        EditText name = input("", item == null ? "" : item.name);
        form.addView(name);
        form.addView(label("Category"));
        EditText category = input("", item == null ? "" : item.category);
        form.addView(category);

        if (item != null) {
            form.addView(label("Current stock"));
            EditText stock = numberInput(String.valueOf(item.onHand));
            stock.setEnabled(false);
            stock.setAlpha(0.6f);
            form.addView(stock);
                            form.addView(label("New stock"));
                            EditText newStock = numberInput("0");
                            form.addView(newStock);
                            form.addView(label("Cost per unit (new stock)"));
                            EditText cost = numberInput("");
                            cost.setHint("e.g. 500");
                            form.addView(cost);
                            form.addView(label("Sell for (per unit)"));
                            EditText sellPrice = numberInput(item.sellingPrice > 0 ? String.valueOf((int) (item.sellingPrice / 100.0)) : "");
                            sellPrice.setHint("e.g. 1000");
                            form.addView(sellPrice);
                            form.addView(label("Low stock"));
                            EditText reorder = numberInput(String.valueOf(item.reorderAt));
                            form.addView(reorder);
                            AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Manage inventory").setView(form)
                                    .setPositiveButton("Save", (d, w) -> {
                                        double sPrice = parseCents(sellPrice.getText().toString(), item.sellingPrice > 0 ? item.sellingPrice : 0);
                                        db.saveInventory(new SalonDatabase.InventoryItem(item.id, valueOr(name.getText().toString(), item.name), valueOr(category.getText().toString(), item.category), item.onHand, (int) parseAmount(reorder.getText().toString(), item.reorderAt), item.cost, sPrice));
                                        int added = (int) parseAmount(newStock.getText().toString(), 0);
                                        if (added > 0) {
                                            double pPrice = parseCents(cost.getText().toString(), 0);
                                            SQLiteDatabase dba = db.getWritableDatabase();
                                            ContentValues vals = new ContentValues();
                                            vals.put("inventoryId", item.id);
                                            vals.put("quantity", added);
                                            vals.put("purchasePrice", pPrice);
                                            vals.put("dateAdded", SalonDatabase.today());
                                            vals.put("remaining", added);
                                            dba.insert("stock_batches", null, vals);
                                            dba.execSQL("UPDATE inventory SET on_hand = on_hand + ? WHERE id = ?", new Object[]{added, item.id});
                                        }
                                        rerender();
                    }).setNegativeButton("Cancel", null);
            if (item != null) builder.setNeutralButton("Delete", (d, w) -> { confirmDelete("Delete item", "Delete " + item.name + " from inventory?", () -> { db.deleteInventory(item.id); rerender(); }); });
            builder.show();
        } else {
            form.addView(label("New stock"));
            EditText stock = numberInput("0");
            form.addView(stock);
            form.addView(label("Cost per unit"));
            EditText cost = numberInput("");
            cost.setHint("e.g. 500");
            form.addView(cost);
            form.addView(label("Sell for (per unit)"));
            EditText sellPrice = numberInput("");
            sellPrice.setHint("e.g. 1000");
            form.addView(sellPrice);
            form.addView(label("Low stock"));
            EditText reorder = numberInput("5");
            form.addView(reorder);
            AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("Add inventory").setView(form)
                    .setPositiveButton("Save", (d, w) -> {
                        int initStock = (int) parseAmount(stock.getText().toString(), 0);
                        double pPrice = parseCents(cost.getText().toString(), 0);
                        double sPrice = parseCents(sellPrice.getText().toString(), 0);
                        String itemName = valueOr(name.getText().toString(), "Product");
                        long newId = db.saveInventory(new SalonDatabase.InventoryItem(0, itemName, valueOr(category.getText().toString(), "Retail"), initStock, (int) parseAmount(reorder.getText().toString(), 5), pPrice, sPrice));
                        if (initStock > 0) {
                            SQLiteDatabase dba = db.getWritableDatabase();
                            ContentValues vals = new ContentValues();
                            vals.put("inventoryId", newId);
                            vals.put("quantity", initStock);
                            vals.put("purchasePrice", pPrice);
                            vals.put("dateAdded", SalonDatabase.today());
                            vals.put("remaining", initStock);
                            dba.insert("stock_batches", null, vals);
                        }
                        rerender();
                    }).setNegativeButton("Cancel", null);
            builder.show();
        }
    }

    private void renderSettingsHub() {
        content.addView(SettingsComposeFactory.create(this, db, settings, settingsSection), new LinearLayout.LayoutParams(-1, -2));
    }

    public void showStylistDialog(SalonDatabase.Stylist stylist) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Name"));
        EditText name = input("", stylist == null ? "" : stylist.name);
        form.addView(name);
        form.addView(label("Phone number"));
        EditText phone = input("", stylist == null ? "" : stylist.phone);
        form.addView(phone);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(stylist == null ? "Add stylist" : "Edit stylist").setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null);
        if (stylist != null) dialog.setNeutralButton("Delete", (d, w) -> { confirmDelete("Delete stylist", "Delete " + stylist.name + "?", () -> { db.deleteStylist(stylist.id); rerender(); }); });
        AlertDialog alert = dialog.create();
        alert.setOnShowListener(d -> alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String phoneValue = valueOr(phone.getText().toString(), "");
            long currentId = stylist == null ? 0 : stylist.id;
            if (!isValidKenyanPhone(phoneValue)) {
                Toast.makeText(this, "Phone must start with 07 or 01 followed by 8 digits", Toast.LENGTH_LONG).show();
                return;
            }
            if (phoneExistsForStylist(phoneValue, currentId)) {
                Toast.makeText(this, "Stylist phone number already exists", Toast.LENGTH_LONG).show();
                return;
            }
            db.saveStylist(new SalonDatabase.Stylist(currentId, valueOr(name.getText().toString(), "Stylist"), stylist == null ? 0 : stylist.commission, phoneValue));
            rerender();
            alert.dismiss();
        }));
        alert.show();
    }

    public void showServiceDialog(SalonDatabase.Service service) {
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        form.addView(label("Name"));
        EditText name = input("", service == null ? "" : service.name);
        form.addView(name);
        form.addView(label("Price"));
        EditText price = numberInput(service == null ? "" : String.valueOf((int) (service.price / 100.0)));
        form.addView(price);
        form.addView(label("Commission %"));
        EditText commission = numberInput(service == null ? "30" : String.valueOf((int) service.commission));
        form.addView(commission);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(service == null ? "Add service" : "Edit service").setView(form)
                .setPositiveButton("Save", (d, w) -> { db.saveService(new SalonDatabase.Service(service == null ? 0 : service.id, valueOr(name.getText().toString(), "Service"), parseCents(price.getText().toString(), 0), parseAmount(commission.getText().toString(), 30))); rerender(); })
                .setNegativeButton("Cancel", null);
        if (service != null) builder.setNeutralButton("Delete", (d, w) -> { confirmDelete("Delete service", "Delete " + service.name + "?", () -> { db.deleteService(service.id); rerender(); }); });
        builder.show();
    }

    public void backupNow() {
        try {
            db.backupNow();
            rerender();
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        if (driveBackupManager != null && driveBackupManager.isSignedIn() && settings.hasDriveKey()) {
            File latest = db.latestBackup();
            if (latest != null) {
                driveBackupManager.uploadBackupCached(latest,
                        settings.getDriveKeyBytes(),
                        settings.getDriveKeySalt(),
                        new DriveBackupManager.DriveCallback() {
                            @Override
                            public void onResult(boolean success, String message) {
                                runOnUiThread(() -> {
                                    if (success) {
                                        settings.setLastDriveBackupTime(System.currentTimeMillis());
                                        rerender();
                                        Toast.makeText(MainActivity.this, "Backup saved locally and uploaded to Drive", Toast.LENGTH_LONG).show();
                                    } else {
                                        Toast.makeText(MainActivity.this, "Local backup saved. Drive upload failed, will retry.", Toast.LENGTH_LONG).show();
                                        DriveBackupManager.scheduleRetry(MainActivity.this, 1);
                                    }
                                });
                            }
                        });
            }
        } else {
            Toast.makeText(this, "Backup saved locally", Toast.LENGTH_LONG).show();
        }
    }

    public void restoreFromDrive(String fileId, String pin, Runnable onComplete) {
        if (driveBackupManager == null) {
            Toast.makeText(this, "Drive backup not available", Toast.LENGTH_SHORT).show();
            return;
        }
        File tempRaw = new File(getCacheDir(), "drive-restore-raw-" + System.currentTimeMillis() + ".tmp");
        driveBackupManager.downloadBackup(fileId, tempRaw, pin, new DriveBackupManager.DriveCallback() {
            @Override
            public void onResult(boolean success, String message) {
                if (!success) {
                    tempRaw.delete();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Restore failed: " + message, Toast.LENGTH_LONG).show();
                        if (onComplete != null) onComplete.run();
                    });
                    return;
                }
                try {
                    SQLiteDatabase oldDb = db.getWritableDatabase();
                    oldDb.close();
                    File dbFile = getDatabasePath("salonflow.db");
                    try (java.io.FileInputStream fin = new java.io.FileInputStream(tempRaw);
                         java.io.FileOutputStream fout = new java.io.FileOutputStream(dbFile)) {
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = fin.read(buf)) != -1) fout.write(buf, 0, len);
                    }
                    db = new SalonDatabase(MainActivity.this);
                    tempRaw.delete();
                    runOnUiThread(() -> {
                        rerender();
                        Toast.makeText(MainActivity.this, "Drive backup restored", Toast.LENGTH_LONG).show();
                        if (onComplete != null) onComplete.run();
                    });
                } catch (Exception e) {
                    tempRaw.delete();
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        if (onComplete != null) onComplete.run();
                    });
                }
            }
        });
    }

    public void restoreBackup() {
        String[] options;
        boolean hasDrive = driveBackupManager != null && driveBackupManager.isSignedIn() && settings.hasDriveKey();
        if (hasDrive) {
            options = new String[]{"Restore from Google Drive", "Restore from local backup"};
        } else {
            options = new String[]{"Restore from local backup"};
        }

        new AlertDialog.Builder(this)
                .setTitle("Restore data")
                .setItems(options, (dialog, which) -> {
                    if (hasDrive && which == 0) {
                        restoreFromDriveLatest();
                    } else {
                        restoreFromLocal();
                    }
                })
                .show();
    }

    private void restoreFromDriveLatest() {
        Toast.makeText(this, "Checking Drive for latest backup...", Toast.LENGTH_LONG).show();
        driveBackupManager.listBackups(new DriveBackupManager.FileListCallback() {
            @Override
            public void onResult(List<DriveBackupManager.DriveFileInfo> files, String error) {
                runOnUiThread(() -> {
                    if (error != null) {
                        Toast.makeText(MainActivity.this, "Drive error: " + error, Toast.LENGTH_LONG).show();
                        restoreFromLocal();
                        return;
                    }
                    if (files == null || files.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No Drive backups found. Falling back to local.", Toast.LENGTH_LONG).show();
                        restoreFromLocal();
                        return;
                    }
                    DriveBackupManager.DriveFileInfo latest = files.get(0);
                    showRestorePinDialog(latest.id);
                });
            }
        });
    }

    private void showRestorePinDialog(String fileId) {
        android.widget.EditText pinInput = new android.widget.EditText(this);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("Enter PIN");
        new AlertDialog.Builder(this)
                .setTitle("Restore from Drive")
                .setMessage("Enter your security PIN to decrypt the backup.")
                .setView(pinInput)
                .setPositiveButton("Restore", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (!settings.checkPin(pin)) {
                        Toast.makeText(MainActivity.this, "Wrong PIN", Toast.LENGTH_LONG).show();
                        return;
                    }
                    restoreFromDrive(fileId, pin, () -> rerender());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restoreFromLocal() {
        List<File> files = db.backups();
        if (files.isEmpty()) {
            Toast.makeText(this, "No local backups found", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[files.size()];
        for (int i = 0; i < files.size(); i++) names[i] = files.get(i).getName();
        new AlertDialog.Builder(this).setTitle("Restore local backup").setItems(names, (dialog, which) -> {
            try {
                db.restore(files.get(which));
                db = new SalonDatabase(this);
                rerender();
                Toast.makeText(this, "Backup restored", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Restore failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }).show();
    }

    private void scheduleDailyBackup() {
        setBackupAlarm(false);
    }

    private void setBackupAlarm(boolean allowToday) {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(this, BackupReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 2000, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 20);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (!allowToday && next.before(Calendar.getInstance()) || next.equals(Calendar.getInstance())) {
            next.add(Calendar.DAY_OF_MONTH, 1);
        }
        boolean canUseExactAlarm = Build.VERSION.SDK_INT < 31
                || checkSelfPermission(Manifest.permission.USE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED;
        if (canUseExactAlarm) {
            try {
                alarmManager.setAlarmClock(new AlarmManager.AlarmClockInfo(next.getTimeInMillis(), null), pendingIntent);
            } catch (SecurityException e) {
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
            }
        } else {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    static void rescheduleBackupAlarm(Context context) {
        Intent intent = new Intent(context, BackupReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 2000, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 20);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        next.add(Calendar.DAY_OF_MONTH, 1);
        boolean canUseExactAlarm = Build.VERSION.SDK_INT < 31
                || context.checkSelfPermission(Manifest.permission.USE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED;
        if (canUseExactAlarm) {
            try {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(next.getTimeInMillis(), null), pendingIntent);
            } catch (SecurityException e) {
                am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
            }
        } else {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }
    public void scheduleDailyReminders() {
        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        Intent intent = new Intent(this, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 2001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!settings.notifyMorningBookings() && !settings.notifyOverdueSevenDays()) {
            alarmManager.cancel(pendingIntent);
            return;
        }
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 8);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.before(Calendar.getInstance())) next.add(Calendar.DAY_OF_MONTH, 1);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            boolean canUseExactAlarm = Build.VERSION.SDK_INT < 31
                    || checkSelfPermission(android.Manifest.permission.USE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(android.Manifest.permission.SCHEDULE_EXACT_ALARM) == PackageManager.PERMISSION_GRANTED;
            if (canUseExactAlarm) {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(next.getTimeInMillis(), null);
                try {
                    alarmManager.setAlarmClock(info, pendingIntent);
                } catch (SecurityException e) {
                    alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
                }
            } else {
                alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
            }
        } else {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 33) return;
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return;
        requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 4110);
    }
    public void showSetPinDialog() {
        if (isFinishing()) return;
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        final EditText oldPin;
        if (settings.hasPin()) {
            form.addView(label("Current PIN"));
            oldPin = input("Current PIN", "");
            oldPin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            form.addView(oldPin);
        } else {
            oldPin = null;
        }
        form.addView(label(settings.hasPin() ? "New PIN" : "PIN"));
        EditText pin = input("4-digit PIN", "");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(pin);
        form.addView(label("Confirm PIN"));
        EditText confirm = input("Confirm PIN", "");
        confirm.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(confirm);
        new AlertDialog.Builder(this)
                .setTitle(settings.hasPin() ? "Change PIN" : "Create your Security PIN")
                .setView(form)
                .setPositiveButton("Save", (dialog, which) -> {
                    String first = pin.getText().toString().trim();
                    String second = confirm.getText().toString().trim();
                    if (oldPin != null && !isCorrectPin(oldPin.getText().toString().trim())) {
                        Toast.makeText(this, "Current PIN is incorrect", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!first.matches("\\d{4}")) {
                        Toast.makeText(this, "PIN must be exactly 4 digits", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (!first.equals(second)) {
                        Toast.makeText(this, "PIN entries do not match", Toast.LENGTH_LONG).show();
                        return;
                    }
                    settings.setPin(first);
                    if (!settings.isSettingsPinRequired()) settings.setSettingsPinRequired(true);
                    settings.markPinVerified();
                    appUnlocked = true;
                    rerender();
                    if (!settings.hasPin()) Toast.makeText(this, "PIN saved. Use it to access Settings.", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void handleSettingsSwipe(boolean swipedRight) {
        if ("home".equals(settingsSection)) {
            if (swipedRight) {
                selectedTab = 3;
                viewingSettings = false;
                renderTabs();
                renderSelectedTab();
            }
            return;
        }
        int current = -1;
        for (int i = 0; i < SETTINGS_SECTIONS.length; i++) {
            if (SETTINGS_SECTIONS[i].equals(settingsSection)) { current = i; break; }
        }
        if (current < 0) return;
        int target = swipedRight ? current - 1 : current + 1;
        if (target < 0 || target >= SETTINGS_SECTIONS.length) return;
        settingsSection = SETTINGS_SECTIONS[target];
        renderSelectedTab();
    }

    public void promptForSettingsPinAndThen(Runnable onSuccess) {
        if (isFinishing()) return;
        if (settings.isWithinGracePeriod()) {
            onSuccess.run();
            return;
        }
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText pin = input("Enter PIN", "");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(pin);
        new AlertDialog.Builder(this)
                .setTitle("Settings PIN")
                .setView(form)
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (!isCorrectPin(pin.getText().toString().trim())) {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_LONG).show();
                        return;
                    }
                    settings.markPinVerified();
                    onSuccess.run();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showPinUnlockDialog() {
        if (isFinishing()) return;
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText pinInput = input("Enter PIN", "");
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(pinInput);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("App Locked")
                .setMessage("Enter your PIN to unlock the app")
                .setView(form)
                .setPositiveButton("Unlock", (d, w) -> {
                    if (!isCorrectPin(pinInput.getText().toString().trim())) {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_LONG).show();
                        d.dismiss();
                        showPinUnlockDialog();
                        return;
                    }
                    settings.markPinVerified();
                    appUnlocked = true;
                    rerender();
                })
                .setNegativeButton("Exit", (d, w) -> finish())
                .setCancelable(false)
                .show();
        pinInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }

    void promptForSettingsPin() {
        if (isFinishing()) return;
        if (settings.isWithinGracePeriod()) {
            pushState();
            viewingSettings = true;
            settingsSection = "home";
            renderTabs();
            renderSelectedTab();
            return;
        }
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), 0);
        EditText pin = input("Enter PIN", "");
        pin.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        form.addView(pin);
        new AlertDialog.Builder(this)
                .setTitle("Settings PIN")
                .setView(form)
                .setPositiveButton("Continue", (dialog, which) -> {
                    if (!isCorrectPin(pin.getText().toString().trim())) {
                        Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_LONG).show();
                        return;
                    }
                    settings.markPinVerified();
                    pushState();
                    viewingSettings = true;
                    settingsSection = "home";
                    renderTabs();
                    renderSelectedTab();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void maybeStartOnboarding() {
        if (settings.onboardingDone()) return;
        if (!db.isEmpty()) return;
        showOnboardingStep(0);
    }
    private void showOnboardingStep(int step) {
        String[] titles = {"Welcome", "Settings", "Bookings", "Clients", "Reports"};
        String[] notes = {
                "Welcome to SalonFlow. Home gives quick daily business health.",
                "Use the Settings tab at the bottom for admin setup like services, stylists, security, notifications, and backups.",
                "Bookings helps you manage the calendar and appointments.",
                "Clients helps you store customer records and histories.",
                "Reports gives detailed monthly and annual performance analytics."
        };
        if (step == 1) {
            viewingSettings = true;
            settingsSection = "home";
            selectedTab = 4;
        } else {
            viewingSettings = false;
            if (step == 0) selectedTab = 0;
            if (step == 2) selectedTab = 1;
            if (step == 3) selectedTab = 2;
            if (step == 4) selectedTab = 3;
        }
        renderTabs();
        renderSelectedTab();
        new AlertDialog.Builder(this)
                .setTitle(titles[step])
                .setMessage(notes[step])
                .setCancelable(false)
                .setPositiveButton(step == titles.length - 1 ? "Finish" : "Next", (d, w) -> {
                    if (step == titles.length - 1) {
                        settings.setOnboardingDone(true);
                        viewingSettings = false;
                        selectedTab = 0;
                        renderTabs();
                        renderSelectedTab();
                    } else {
                        showOnboardingStep(step + 1);
                    }
                })
                .setNegativeButton("Skip", (d, w) -> {
                    settings.setOnboardingDone(true);
                    viewingSettings = false;
                    selectedTab = 0;
                    renderTabs();
                    renderSelectedTab();
                })
                .show();
    }
    private void pushState() {
        navHistory.push(captureState());
    }
    private String captureState() {
        return selectedTab + "|" + (viewingSettings ? "1" : "0") + "|" + settingsSection;
    }
    private void applyState(String encoded) {
        String[] parts = encoded.split("\\|", 3);
        if (parts.length < 3) return;
        selectedTab = parseInt(parts[0], 0);
        viewingSettings = "1".equals(parts[1]);
        settingsSection = parts[2];
    }

    private void addTransaction(String title, String subtitle, double amount, View.OnClickListener listener) {
        LinearLayout row = card();
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout info = vertical();
        info.addView(text(title, 15, Color.rgb(24, 33, 31), Typeface.BOLD));
        info.addView(text(subtitle, 12, MUTED, Typeface.NORMAL));
        row.addView(info, weightedParams(1.8f, 0, 0, dp(8), 0));
        row.addView(text(formatMoney(amount), 15, amount < 0 ? DANGER : BRAND, Typeface.BOLD), weightedParams(0.9f, 0, 0, 0, 0));
        if (listener != null) row.setOnClickListener(listener);
        content.addView(row, listParams());
    }



    private TextView statusPill(String status) {
        TextView pill = text(status, 12, BRAND, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        String drawableStatus = "completed".equals(status) ? "paid" : status;
        pill.setBackgroundResource(pillDrawable(drawableStatus));
        pill.setPadding(dp(8), dp(6), dp(8), dp(6));
        if ("cancelled".equals(status)) pill.setTextColor(DANGER);
        if ("pending".equals(status)) pill.setTextColor(Color.rgb(145, 97, 17));
        return pill;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        card.setBackgroundResource(R.drawable.card_bg);
        return card;
    }

    private LinearLayout vertical() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private Button button(String label, boolean primary) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(primary ? Color.WHITE : BRAND);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundResource(primary ? R.drawable.button_primary : R.drawable.button_secondary);
        return b;
    }

    private void renderFloatingActionButton() {
        if (floatingActionButton == null) return;
        if (selectedTab == 0) {
            floatingActionButton.setVisibility(View.VISIBLE);
            floatingActionButton.setText("+");
            floatingActionButton.setOnClickListener(v -> showRevenueCreatePicker());
            return;
        }
        if (selectedTab == 1) {
            floatingActionButton.setVisibility(View.VISIBLE);
            floatingActionButton.setText("+");
            floatingActionButton.setOnClickListener(v -> showBookingDialog(null));
            return;
        }
        if (selectedTab == 2) {
            floatingActionButton.setVisibility(View.VISIBLE);
            floatingActionButton.setText("+");
            floatingActionButton.setOnClickListener(v -> showClientDialog(null));
            return;
        }
        floatingActionButton.setVisibility(View.GONE);
    }
    public void pushSettingsSection(String current) {
        settingsNavStack.push(current);
    }

    public String popSettingsSection() {
        return settingsNavStack.isEmpty() ? "home" : settingsNavStack.pop();
    }

    public void updateFabForSettingsSection(String section) {
        settingsSection = section;
        if (floatingActionButton == null) return;
        floatingActionButton.setVisibility(View.VISIBLE);
        floatingActionButton.setText("+");
        switch (section) {
            case "stylists":
                floatingActionButton.setOnClickListener(v -> showStylistDialog(null));
                break;
            case "services":
                floatingActionButton.setOnClickListener(v -> showServiceDialog(null));
                break;
            case "inventory":
                floatingActionButton.setOnClickListener(v -> showInventoryDialog(null));
                break;
            default:
                floatingActionButton.setVisibility(View.GONE);
                break;
        }
    }
    public DriveBackupManager getDriveBackupManager() {
        return driveBackupManager;
    }
    public AppSettings getAppSettings() {
        return settings;
    }
    public void signInToDrive(Runnable onComplete) {
        if (driveBackupManager != null) {
            driveSignInCallback = onComplete;
            driveSignInLauncher.launch(driveBackupManager.getSignInIntent());
        } else if (onComplete != null) {
            onComplete.run();
        }
    }
    public void signOutOfDrive() {
        if (driveBackupManager != null) {
            driveBackupManager.signOut();
            Toast.makeText(this, "Signed out of Google Drive", Toast.LENGTH_SHORT).show();
        }
    }
    private void showRevenueCreatePicker() {
        String[] actions = {"Sale", "Expense"};
        new AlertDialog.Builder(this)
                .setTitle("Add transaction")
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) showSaleDialog(null);
                    if (which == 1) showExpenseDialog(null);
                })
                .show();
    }
    private TextView text(String value, int sp, int color, int style) { TextView v = new TextView(this); v.setText(value); v.setTextSize(sp); v.setTextColor(color); v.setTypeface(Typeface.DEFAULT, style); v.setIncludeFontPadding(true); return v; }
    private TextView label(String value) { TextView l = text(value, 12, MUTED, Typeface.BOLD); l.setPadding(0, dp(10), 0, dp(4)); return l; }
    private EditText input(String hint, String value) { EditText e = new EditText(this); e.setHint(""); e.setText(value); e.setSingleLine(true); return e; }
    private EditText numberInput(String value) { EditText e = input("", value); e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL); return e; }
    private View field(String labelText, String value) { LinearLayout wrapper = vertical(); wrapper.addView(label(labelText)); wrapper.addView(input("", value)); return wrapper; }
    private Spinner spinner(String[] values) { Spinner s = new Spinner(this); s.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values)); return s; }
    private LinearLayout.LayoutParams wrapParams() { return new LinearLayout.LayoutParams(-2, -2); }
    private LinearLayout.LayoutParams listParams() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(5), 0, dp(7)); return p; }
    private LinearLayout.LayoutParams bottomSpace() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(16), 0, dp(8)); return p; }
    private LinearLayout.LayoutParams weightedParams(float weight, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, weight); p.setMargins(left, top, right, bottom); return p; }
    private LinearLayout.LayoutParams fixedParams(int width, int height, int left, int top, int right, int bottom) { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height); p.setMargins(left, top, right, bottom); return p; }
    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
    public void rerender() { renderSelectedTab(); }
    private boolean isCorrectPin(String input) {
        return settings.checkPin(input);
    }
    private int pillDrawable(String status) {
        switch (status) {
            case "paid": return R.drawable.pill_paid;
            case "pending": return R.drawable.pill_pending;
            case "cancelled": return R.drawable.pill_cancelled;
            default: return R.drawable.pill_paid;
        }
    }
    private void setupDatePicker(EditText field) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setOnClickListener(v -> {
            String[] parts = field.getText().toString().split("-");
            int y = parts.length >= 3 ? Integer.parseInt(parts[0]) : Calendar.getInstance().get(Calendar.YEAR);
            int m = parts.length >= 3 ? Integer.parseInt(parts[1]) - 1 : Calendar.getInstance().get(Calendar.MONTH);
            int d = parts.length >= 3 ? Integer.parseInt(parts[2]) : Calendar.getInstance().get(Calendar.DAY_OF_MONTH);
            new android.app.DatePickerDialog(this, (view, year, month, day) ->
                field.setText(String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, day)), y, m, d).show();
        });
    }
    private void setupTimePicker(EditText field) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setOnClickListener(v -> {
            String[] parts = field.getText().toString().split(":");
            int h = parts.length >= 2 ? Integer.parseInt(parts[0]) : 9;
            int mi = parts.length >= 2 ? Integer.parseInt(parts[1]) : 0;
            new android.app.TimePickerDialog(this, (view, hour, minute) ->
                field.setText(String.format(Locale.US, "%02d:%02d", hour, minute)), h, mi, true).show();
        });
    }
    private String joinLines(List<String> rows, int max) {
        StringBuilder builder = new StringBuilder();
        int limit = Math.min(max, rows.size());
        for (int i = 0; i < limit; i++) {
            builder.append("- ").append(rows.get(i));
            if (i < limit - 1) builder.append("\n");
        }
        if (rows.size() > max) builder.append("\n- ...");
        return builder.toString();
    }
    private double balance(double amount, double paid) { return Math.max(0, amount - paid); }
    private String balanceLabel(double amount, double paid, String dueDate) {
        double remaining = balance(amount, paid);
        if (remaining <= 0) return "";
        return " • balance " + formatMoney(remaining) + " • overdue " + overdueDays(dueDate) + " days";
    }
    private int overdueDays(String dueDate) {
        if (dueDate == null || dueDate.trim().isEmpty()) return 0;
        try {
            long due = SalonUtils.DATE_FMT.get().parse(dueDate).getTime();
            long today = SalonUtils.DATE_FMT.get().parse(SalonDatabase.today()).getTime();
            return Math.max(0, (int) ((today - due) / (24L * 60L * 60L * 1000L)));
        } catch (ParseException e) {
            return 0;
        }
    }
    private SalonDatabase.Client clientByPhone(String phone) {
        if (phone == null || phone.isEmpty()) return null;
        for (SalonDatabase.Client c : db.clients()) if (phone.equals(c.phone)) return c;
        return null;
    }
    private SalonDatabase.Client clientById(Long id) {
        if (id == null) return null;
        for (SalonDatabase.Client client : db.clients()) if (client.id == id) return client;
        return null;
    }
    private double parseAmount(String value, double fallback) { try { return Double.parseDouble(value.trim()); } catch (Exception e) { return fallback; } }
    private double cents(double dollars) { return Math.round(dollars * 100.0); }
    private double parseCents(String value, double fallbackCents) { return cents(parseAmount(value, fallbackCents / 100.0)); }
    private String formatMoney(double cents) { return money.format(cents / 100.0); }
    private int parseInt(String value, int fallback) { try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; } }
    private String valueOr(String value, String fallback) { String t = value == null ? "" : value.trim(); return t.isEmpty() ? fallback : t; }
    private String dateFor(int day) { Calendar copy = (Calendar) visibleMonth.clone(); copy.set(Calendar.DAY_OF_MONTH, day); return SalonUtils.formatDate(copy); }
    private String[] names(List<SalonDatabase.Service> services) { String[] n = new String[services.size()]; for (int i = 0; i < services.size(); i++) n[i] = services.get(i).name; return n; }
    private String[] stylistNames(List<SalonDatabase.Stylist> stylists, boolean strict) { String[] n = new String[stylists.size() + (strict ? 0 : 1)]; int offset = strict ? 0 : 1; if (!strict) n[0] = "No commission"; for (int i = 0; i < stylists.size(); i++) n[i + offset] = stylists.get(i).name; return n; }
    private String[] clientOptions(List<SalonDatabase.Client> clients) {
        String[] n = new String[clients.size() + 1];
        n[0] = "Walk-in";
        for (int i = 0; i < clients.size(); i++) n[i + 1] = clients.get(i).name + " • " + clients.get(i).phone;
        return n;
    }
    private String[] clientBookingOptions(List<SalonDatabase.Client> clients) {
        String[] n = new String[clients.size() + 2];
        n[0] = "Walk-in";
        for (int i = 0; i < clients.size(); i++) n[i + 1] = clients.get(i).name + " • " + clients.get(i).phone;
        n[clients.size() + 1] = "Add new customer";
        return n;
    }
    private int indexOfService(List<SalonDatabase.Service> rows, long id) { for (int i = 0; i < rows.size(); i++) if (rows.get(i).id == id) return i; return 0; }
    private int indexOfStylist(List<SalonDatabase.Stylist> rows, long id) { for (int i = 0; i < rows.size(); i++) if (rows.get(i).id == id) return i; return 0; }
    private String[] inventoryNames(List<SalonDatabase.InventoryItem> items) { String[] n = new String[items.size() + 1]; n[0] = "No product"; for (int i = 0; i < items.size(); i++) n[i + 1] = items.get(i).name + " (" + items.get(i).onHand + " left)"; return n; }
    private boolean isValidKenyanPhone(String phone) {
        return phone != null && phone.matches("(07|01)\\d{8}");
    }
    private boolean phoneExistsForStylist(String phone, long exceptId) {
        for (SalonDatabase.Stylist stylist : db.stylists()) if (stylist.id != exceptId && phone.equals(stylist.phone)) return true;
        return false;
    }
    private boolean phoneExistsForClient(String phone, long exceptId) {
        for (SalonDatabase.Client client : db.clients()) if (client.id != exceptId && phone.equals(client.phone)) return true;
        return false;
    }
    private int indexOf(String[] rows, String value) { for (int i = 0; i < rows.length; i++) if (rows[i].equals(value)) return i; return 0; }
    private Map<String, String> mapOf(String k1, String v1, String k2, String v2) { return Map.of(k1, v1, k2, v2); }
    private Map<String, String> mapOf(String k1, String v1, String k2, String v2, String k3, String v3) { return Map.of(k1, v1, k2, v2, k3, v3); }
    private Map<String, String> mapOf(String k1, String v1, String k2, String v2, String k3, String v3, String k4, String v4) { return Map.of(k1, v1, k2, v2, k3, v3, k4, v4); }
    private Map<String, String> mapOf(String k1, String v1, String k2, String v2, String k3, String v3, String k4, String v4, String k5, String v5) { return Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5); }
    private Map<String, String> mapOf(String k1, String v1, String k2, String v2, String k3, String v3, String k4, String v4, String k5, String v5, String k6, String v6) { return Map.of(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5, k6, v6); }
}
