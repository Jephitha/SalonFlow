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

        if (dayOfWeek == Calendar.SATURDAY) {
            scheduleSaturday(context, alarmMgr, cal)
        } else {
            scheduleNormal(context, alarmMgr, cal)
        }
    }

    private fun scheduleNormal(context: Context, alarmMgr: AlarmManager, now: Calendar) {
        val baseCal = Calendar.getInstance()
        val dayOfWeek = baseCal.get(Calendar.DAY_OF_WEEK)
        if (dayOfWeek == Calendar.SATURDAY) return

        scheduleOne(context, alarmMgr, now, 7, 30, SweeperConfig.ALARM_REQUEST_CODE_730, "7:30 AM")
        scheduleOne(context, alarmMgr, now, 14, 30, SweeperConfig.ALARM_REQUEST_CODE_1430, "2:30 PM")
        scheduleOne(context, alarmMgr, now, 20, 0, SweeperConfig.ALARM_REQUEST_CODE_2000, "8:00 PM")
    }

    private fun scheduleOne(context: Context, alarmMgr: AlarmManager, now: Calendar,
                            hour: Int, minute: Int, requestCode: Int, label: String) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_YEAR, 1)
        }

        val pi = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, SweeperAlarmReceiver::class.java).apply {
                action = SweeperConfig.ACTION_SWEEP_NORMAL
            },
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
        val cal200 = now.clone() as Calendar
        cal200.set(Calendar.HOUR_OF_DAY, 2)
        cal200.set(Calendar.MINUTE, 0)
        cal200.set(Calendar.SECOND, 0)
        cal200.set(Calendar.MILLISECOND, 0)
        if (cal200.before(now)) cal200.add(Calendar.DAY_OF_YEAR, 7)

        val pi = PendingIntent.getBroadcast(
            context, SweeperConfig.ALARM_REQUEST_CODE_SATURDAY,
            Intent(context, SweeperAlarmReceiver::class.java).apply {
                action = SweeperConfig.ACTION_SWEEP_SATURDAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(pi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal200.timeInMillis, pi)
        } else {
            alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP, cal200.timeInMillis, 7 * AlarmManager.INTERVAL_DAY, pi)
        }
        Log.i(TAG, "Scheduled Saturday 2:00 AM sweep (next: ${cal200.time})")

        cancelIfExists(context, alarmMgr, SweeperConfig.ALARM_REQUEST_CODE_730)
        Log.i(TAG, "Cancelled 7:30 AM sweep for Saturday")
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
                            SweeperSync.sweep(context)
                            context.getSharedPreferences(SweeperConfig.PREFS_SWEEPER, Context.MODE_PRIVATE)
                                .edit().putBoolean(SweeperConfig.KEY_SWEEP_PENDING, false).apply()
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
