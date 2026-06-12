package com.salonflow.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.io.File;

public class DriveRetryReceiver extends BroadcastReceiver {
    private static final String TAG = "DriveRetry";
    private static final int MAX_RETRIES = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        int attempt = intent.getIntExtra("retry_attempt", 1);

        AppSettings settings = new AppSettings(context);
        if (!settings.hasDriveKey()) return;

        SalonDatabase db = new SalonDatabase(context);
        File latest = db.latestBackup();
        if (latest == null) {
            String msg = "No local backup found to upload. Tap Backup data manually.";
            NotificationRepository.getInstance(context)
                    .add("backup_failure", "Drive Backup Failed", msg);
            ReminderReceiver.postNotification(context, 3301, "Drive Backup Failed", msg);
            return;
        }

        DriveBackupManager driveManager = new DriveBackupManager(context);
        driveManager.uploadBackupCached(latest,
                settings.getDriveKeyBytes(),
                settings.getDriveKeySalt(),
                new DriveBackupManager.DriveCallback() {
                    @Override
                    public void onResult(boolean success, String message) {
                        if (success) {
                            android.util.Log.i(TAG, "Drive upload succeeded on retry " + attempt);
                            return;
                        }
                        android.util.Log.w(TAG, "Drive upload retry " + attempt + " failed: " + message);
                        if (attempt < MAX_RETRIES) {
                            DriveBackupManager.scheduleRetry(context, attempt + 1);
                        } else {
                            String msg = "Unable to upload after " + MAX_RETRIES + " attempts. Please tap Backup data and try again.";
                            NotificationRepository.getInstance(context)
                                    .add("backup_failure", "Drive Backup Failed", msg);
                            ReminderReceiver.postNotification(context, 3302, "Drive Backup Failed", msg);
                        }
                    }
                });
    }
}
