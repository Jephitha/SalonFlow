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
import kotlinx.coroutines.CoroutineScope
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

        val cal730 = baseCal.clone() as Calendar
        cal730.set(Calendar.HOUR_OF_DAY, 7)
        cal730.set(Calendar.MINUTE, 30)
        cal730.set(Calendar.SECOND, 0)
        cal730.set(Calendar.MILLISECOND, 0)
        if (cal730.before(now)) cal730.add(Calendar.DAY_OF_YEAR, 1)

        val cal1430 = baseCal.clone() as Calendar
        cal1430.set(Calendar.HOUR_OF_DAY, 14)
        cal1430.set(Calendar.MINUTE, 30)
        cal1430.set(Calendar.SECOND, 0)
        cal1430.set(Calendar.MILLISECOND, 0)
        if (cal1430.before(now)) cal1430.add(Calendar.DAY_OF_YEAR, 1)

        val intent = Intent(context, SweeperAlarmReceiver::class.java).apply {
            action = SweeperConfig.ACTION_SWEEP_NORMAL
        }

        val pi = PendingIntent.getBroadcast(
            context, SweeperConfig.ALARM_REQUEST_CODE_NORMAL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmMgr.cancel(pi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmMgr.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal730.timeInMillis,
                pi
            )
        } else {
            alarmMgr.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal730.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pi
            )
        }

        Log.i(TAG, "Scheduled 7:30 AM sweep (next: ${cal730.time})")
    }

    private fun scheduleSaturday(context: Context, alarmMgr: AlarmManager, now: Calendar) {
        val cal200 = now.clone() as Calendar
        cal200.set(Calendar.HOUR_OF_DAY, 2)
        cal200.set(Calendar.MINUTE, 0)
        cal200.set(Calendar.SECOND, 0)
        cal200.set(Calendar.MILLISECOND, 0)

        if (cal200.before(now)) cal200.add(Calendar.DAY_OF_YEAR, 7)

        val intent = Intent(context, SweeperAlarmReceiver::class.java).apply {
            action = SweeperConfig.ACTION_SWEEP_SATURDAY
        }

        val pi = PendingIntent.getBroadcast(
            context, SweeperConfig.ALARM_REQUEST_CODE_SATURDAY,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmMgr.cancel(pi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmMgr.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                cal200.timeInMillis,
                pi
            )
        } else {
            alarmMgr.setRepeating(
                AlarmManager.RTC_WAKEUP,
                cal200.timeInMillis,
                7 * AlarmManager.INTERVAL_DAY,
                pi
            )
        }

        Log.i(TAG, "Scheduled Saturday 2:00 AM sweep (next: ${cal200.time})")

        val intent730 = Intent(context, SweeperAlarmReceiver::class.java).apply {
            action = SweeperConfig.ACTION_SWEEP_NORMAL
        }
        val pi730 = PendingIntent.getBroadcast(
            context, SweeperConfig.ALARM_REQUEST_CODE_NORMAL,
            intent730,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmMgr.cancel(pi730)
        Log.i(TAG, "Cancelled 7:30 AM sweep for Saturday")
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
                            prefs.edit().putBoolean(SweeperConfig.KEY_SWEEP_PENDING, false).apply()
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
