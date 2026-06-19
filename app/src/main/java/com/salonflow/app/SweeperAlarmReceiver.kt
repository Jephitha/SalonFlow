package com.salonflow.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
                        if (!SweeperConfig.isOnline(context)) {
                            SweeperConfig.setPendingSweep(context)
                            return@launch
                        }
                        SweeperConfig.clearPendingSweep(context)
                        SweeperSync.sweep(context, SweeperSync.PRIORITY_REGULAR)
                        FirestoreManager.getInstance(context).reportSweep("regular")
                    } catch (e: Exception) {
                        Log.e(TAG, "Normal sweep failed", e)
                        SweeperConfig.setPendingSweep(context)
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
                        if (!SweeperConfig.isOnline(context)) {
                            SweeperConfig.setPendingSweep(context)
                            return@launch
                        }
                        SweeperConfig.clearPendingSweep(context)
                        SweeperSync.deepSweep(context)
                        FirestoreManager.getInstance(context).reportSweep("deepSweep")
                    } catch (e: Exception) {
                        Log.e(TAG, "Saturday sweep failed", e)
                        SweeperConfig.setPendingSweep(context)
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

    private fun runPendingSweep(context: Context) {
        val prefs = context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SweeperConfig.KEY_SWEEP_PENDING, false)) {
            Log.i(TAG, "Running pending sweep")
            CoroutineScope(Dispatchers.IO + Job()).launch {
                    try {
                        SweeperSync.sweep(context, SweeperSync.PRIORITY_BOOT_RECOVERY)
                        FirestoreManager.getInstance(context).reportSweep("bootRecovery")
                        SweeperConfig.clearPendingSweep(context)
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
