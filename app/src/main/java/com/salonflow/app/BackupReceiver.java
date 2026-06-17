package com.salonflow.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        // Reschedule for tomorrow (setAlarmClock is one-shot)
        MainActivity.rescheduleBackupAlarm(context);

        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date());
        try {
            SalonDatabase database = new SalonDatabase(context);
            File newBackup = database.backupNow();
            database.deleteOldBackups(newBackup);
            String msg = "Daily backup completed at " + timestamp;
            NotificationRepository.getInstance(context).add("backup_success", "Backup Successful", msg);
            ReminderReceiver.postNotification(context, 3201, "Backup Successful", msg);
        } catch (Exception e) {
            String msg = "Error: " + e.getMessage();
            NotificationRepository.getInstance(context).add("backup_failure", "Backup Failed", msg);
            ReminderReceiver.postNotification(context, 3202, "Backup Failed", msg);
            return;
        }

        SweeperSync.sweep(context);

        AppSettings settings = new AppSettings(context);
        if (!settings.hasDriveKey()) return;

        DriveBackupManager driveManager = new DriveBackupManager(context);
        File latest = new SalonDatabase(context).latestBackup();
        if (latest == null) return;

        driveManager.uploadBackupCached(latest,
                settings.getDriveKeyBytes(),
                settings.getDriveKeySalt(),
                new DriveBackupManager.DriveCallback() {
                    @Override
                    public void onResult(boolean success, String message) {
                        if (!success) {
                            DriveBackupManager.scheduleRetry(context, 1);
                        }
                    }
                });
    }
}
