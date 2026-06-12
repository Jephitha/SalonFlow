package com.salonflow.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "salonflow_reminders";

    @Override
    public void onReceive(Context context, Intent intent) {
        AppSettings settings = new AppSettings(context);
        SalonDatabase db = new SalonDatabase(context);

        if (settings.notifyMorningBookings()) {
            List<SalonDatabase.Booking> morningBookings = morningBookingsToday(db);
            if (!morningBookings.isEmpty()) {
                String msg = "You have " + morningBookings.size() + " booking(s) before 11:00 AM today.";
                postNotification(context, 3101, "Morning bookings", msg);
                NotificationRepository.getInstance(context).add("morning_bookings", "Morning bookings", msg);
            }
        }

        if (settings.notifyOverdueSevenDays()) {
            int overdueCount = overdueForExactlySevenDays(db);
            if (overdueCount > 0) {
                String msg = overdueCount + " payment(s) reached 7 days overdue.";
                postNotification(context, 3102, "Overdue payments", msg);
                NotificationRepository.getInstance(context).add("overdue", "Overdue payments", msg);
            }
        }

        scheduleNext(context);
    }

    static void scheduleNext(Context context) {
        AppSettings settings = new AppSettings(context);
        if (!settings.notifyMorningBookings() && !settings.notifyOverdueSevenDays()) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        Intent intent = new Intent(context, ReminderReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 2001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 8);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        if (next.before(Calendar.getInstance())) next.add(Calendar.DAY_OF_MONTH, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(next.getTimeInMillis(), null);
            alarmManager.setAlarmClock(info, pendingIntent);
        } else {
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), AlarmManager.INTERVAL_DAY, pendingIntent);
        }
    }

    private List<SalonDatabase.Booking> morningBookingsToday(SalonDatabase db) {
        List<SalonDatabase.Booking> rows = db.bookingsForDate(SalonDatabase.today());
        List<SalonDatabase.Booking> result = new ArrayList<>();
        for (SalonDatabase.Booking booking : rows) {
            if ("cancelled".equals(booking.status)) continue;
            if (isBeforeEleven(booking.time)) result.add(booking);
        }
        return result;
    }

    private int overdueForExactlySevenDays(SalonDatabase db) {
        int total = 0;
        String today = SalonDatabase.today();
        List<SalonDatabase.Booking> bookings = db.bookingsBetween("2000-01-01", today);
        for (SalonDatabase.Booking booking : bookings) {
            if ("cancelled".equals(booking.status)) continue;
            if (booking.amount - booking.paidAmount <= 0) continue;
            if (daysBetween(booking.dueDate, today) == 7) total++;
        }
        List<SalonDatabase.Sale> sales = db.salesBetween("2000-01-01", today);
        for (SalonDatabase.Sale sale : sales) {
            if (sale.amount - sale.paidAmount <= 0) continue;
            if (daysBetween(sale.dueDate, today) == 7) total++;
        }
        return total;
    }

    private int daysBetween(String dueDate, String today) {
        if (dueDate == null || dueDate.trim().isEmpty()) return -1;
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date due = f.parse(dueDate);
            Date now = f.parse(today);
            if (due == null || now == null) return -1;
            long diff = now.getTime() - due.getTime();
            return (int) (diff / (24L * 60L * 60L * 1000L));
        } catch (ParseException e) {
            return -1;
        }
    }

    private boolean isBeforeEleven(String time) {
        if (time == null) return false;
        try {
            SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
            Date parsed = f.parse(time.trim());
            if (parsed == null) return false;
            Date cutoff = f.parse("11:00");
            return cutoff != null && parsed.before(cutoff);
        } catch (ParseException e) {
            return false;
        }
    }

    static void postNotification(Context context, int id, String title, String message) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SalonFlow reminders", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Booking and payment reminders");
            channel.enableLights(true);
            channel.setLightColor(Color.rgb(212, 91, 67));
            manager.createNotificationChannel(channel);
        }
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                id,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(contentIntent)
                .setAutoCancel(true);
        manager.notify(id, builder.build());
    }
}
