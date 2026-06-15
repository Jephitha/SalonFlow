package com.salonflow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SweeperAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Received: $action")

        when (action) {
            SweeperConfig.ACTION_SWEEP_NORMAL -> {
                val pendingResult = goAsync()
                val wakeLock = acquireWakeLock(context)
                CoroutineScope(Dispatchers.IO + Job()).launch {
                    try {
                        if (!isOnline(context)) {
                            setPendingSweep(context)
                            return@launch
                        }
                        clearPendingSweep(context)
                        SweeperSync.sweep(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Normal sweep failed", e)
                        setPendingSweep(context)
                    } finally {
                        releaseWakeLock(wakeLock)
                        pendingResult.finish()
                    }
                }
            }
            SweeperConfig.ACTION_SWEEP_SATURDAY -> {
                val pendingResult = goAsync()
                val wakeLock = acquireWakeLock(context)
                CoroutineScope(Dispatchers.IO + Job()).launch {
                    try {
                        if (!isOnline(context)) {
                            setPendingSweep(context)
                            return@launch
                        }
                        clearPendingSweep(context)
                        val prefs = context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
                        val firstSaturdayDone = prefs.getBoolean(SweeperConfig.KEY_FIRST_SATURDAY_DONE, false)
                        if (!firstSaturdayDone) {
                            SweeperSync.sweepAll(context)
                            prefs.edit().putBoolean(SweeperConfig.KEY_FIRST_SATURDAY_DONE, true).apply()
                        } else {
                            SweeperSync.sweep(context)
                        }
                        SweeperSync.sweepSaturday(context)
                    } catch (e: Exception) {
                        Log.e(TAG, "Saturday sweep failed", e)
                        setPendingSweep(context)
                    } finally {
                        releaseWakeLock(wakeLock)
                        pendingResult.finish()
                    }
                }
            }
            Intent.ACTION_BOOT_COMPLETED -> {
                SweeperScheduler.scheduleAlarms(context)
                runPendingSweep(context)
            }
        }
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun acquireWakeLock(context: Context): PowerManager.WakeLock? {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SweeperAlarm:Wakelock")
            wl.acquire(300_000L)
            wl
        } catch (e: Exception) {
            null
        }
    }

    private fun releaseWakeLock(wl: PowerManager.WakeLock?) {
        try { wl?.release() } catch (_: Exception) {}
    }

    private fun setPendingSweep(context: Context) {
        context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
            .edit().putBoolean(SweeperConfig.KEY_SWEEP_PENDING, true).apply()
    }

    private fun clearPendingSweep(context: Context) {
        context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
            .edit().putBoolean(SweeperConfig.KEY_SWEEP_PENDING, false).apply()
    }

    private fun runPendingSweep(context: Context) {
        val prefs = context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SweeperConfig.KEY_SWEEP_PENDING, false)) {
            Log.i(TAG, "Running pending sweep")
            CoroutineScope(Dispatchers.IO + Job()).launch {
                try {
                    SweeperSync.sweep(context)
                    clearPendingSweep(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Pending sweep failed", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SweeperAlarm"
    }
}
