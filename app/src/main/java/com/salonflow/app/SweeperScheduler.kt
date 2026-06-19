package com.salonflow.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Calendar

object SweeperScheduler {
    private const val TAG = "SweeperSched"

    fun scheduleAlarms(context: Context) {
        val alarmMgr = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)

        when (dayOfWeek) {
            Calendar.SUNDAY -> {
                Log.i(TAG, "No sweeper alarms on Sunday; only BackupReceiver runs at 8 PM")
            }
            Calendar.SATURDAY -> {
                scheduleSaturday(context, alarmMgr, cal)
            }
            else -> {
                scheduleNormal(context, alarmMgr, cal)
            }
        }
    }

    private fun scheduleNormal(context: Context, alarmMgr: AlarmManager, now: Calendar) {
        scheduleOne(context, alarmMgr, now, 7, 30, SweeperConfig.ALARM_REQUEST_CODE_730, SweeperConfig.ACTION_SWEEP_NORMAL, "7:30 AM")
        scheduleOne(context, alarmMgr, now, 14, 30, SweeperConfig.ALARM_REQUEST_CODE_1430, SweeperConfig.ACTION_SWEEP_NORMAL, "2:30 PM")
    }

    private fun scheduleOne(context: Context, alarmMgr: AlarmManager, now: Calendar,
                            hour: Int, minute: Int, requestCode: Int, action: String, label: String, intervalDays: Int = 1) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, intervalDays)
        }

        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SweeperAlarmReceiver::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(pi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
        }
        Log.i(TAG, "Scheduled $label sweep (next: ${cal.time})")
    }

    private fun scheduleSaturday(context: Context, alarmMgr: AlarmManager, now: Calendar) {
        cancelIfExists(context, alarmMgr, SweeperConfig.ALARM_REQUEST_CODE_730)
        Log.i(TAG, "Cancelled 7:30 AM sweep for Saturday")
        cancelIfExists(context, alarmMgr, SweeperConfig.ALARM_REQUEST_CODE_2000)
        Log.i(TAG, "Cancelled 8:00 PM sweep for Saturday")

        scheduleOne(context, alarmMgr, now, 2, 0, SweeperConfig.ALARM_REQUEST_CODE_SATURDAY, SweeperConfig.ACTION_SWEEP_SATURDAY, "Saturday 2:00 AM", 7)
        scheduleOne(context, alarmMgr, now, 14, 30, SweeperConfig.ALARM_REQUEST_CODE_1430, SweeperConfig.ACTION_SWEEP_NORMAL, "Saturday 2:30 PM")
    }

    private fun cancelIfExists(context: Context, alarmMgr: AlarmManager, requestCode: Int) {
        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SweeperAlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        pi?.let { alarmMgr.cancel(it); it.cancel() }
    }

    fun registerConnectivityObserver(context: Context) {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "Network available, checking pending sweep")
                val prefs = context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
                if (prefs.getBoolean(SweeperConfig.KEY_SWEEP_PENDING, false)) {
                    Log.i(TAG, "Running pending sweep after connectivity restore")
                    GlobalScope.launch(Dispatchers.IO) {
                        try {
                            SweeperSync.sweep(context, SweeperSync.PRIORITY_NETWORK_RECOVERY)
                            FirestoreManager.getInstance(context).reportSweep("networkRecovery")
                            SweeperConfig.clearPendingSweep(context)
                        } catch (e: Exception) {
                            Log.e(TAG, "Recovery sweep failed", e)
                        }
                    }
                }
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        Log.i(TAG, "Connectivity observer registered")
    }

    fun init(context: Context) {
        scheduleAlarms(context)
        registerConnectivityObserver(context)
    }
}
