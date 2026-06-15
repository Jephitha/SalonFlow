package com.salonflow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class SweeperBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.i(TAG, "Device booted, re-scheduling alarms")
            SweeperScheduler.scheduleAlarms(context)
        }
    }

    companion object {
        private const val TAG = "SweeperBoot"
    }
}
