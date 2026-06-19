package com.salonflow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class SweeperChargingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_POWER_CONNECTED == intent.action) {
            Log.i(TAG, "Power connected, checking first-charge sweep")
            triggerFirstChargeSweep(context)
        }
    }

    companion object {
        private const val TAG = "SweeperCharge"
        private const val KEY_CHARGING_VERSION = "charging_sweep_version"

        fun triggerFirstChargeSweep(context: Context) {
            val prefs = context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
            val lastVersion = prefs.getInt(KEY_CHARGING_VERSION, -1)
            val currentVersion = BuildConfig.VERSION_CODE
            if (lastVersion == currentVersion) return

            CoroutineScope(Dispatchers.IO + Job()).launch {
                try {
                    Log.i(TAG, "Running first-charge unlimited sweepAll")
                    SweeperSync.sweepAll(context, SweeperSync.PRIORITY_FULL_CHARGING)
                    FirestoreManager.getInstance(context).reportSweep("fullCharging")
                    prefs.edit()
                        .putInt(KEY_CHARGING_VERSION, currentVersion)
                        .apply()
                    Log.i(TAG, "First-charge sweepAll complete, flag set")
                } catch (e: Exception) {
                    Log.e(TAG, "First-charge sweep failed", e)
                }
            }
        }

        fun isCharging(context: Context): Boolean {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }
    }
}
