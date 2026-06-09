package com.salonflow.app

import android.app.AlarmManager
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class DriveBackupManagerTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun scheduleRetry_schedulesAlarm() {
        DriveBackupManager.scheduleRetry(context, 1)

        val shadow = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        val next = shadow.nextScheduledAlarm

        assertNotNull("alarm was scheduled", next)
        assertEquals("RTC_WAKEUP type", AlarmManager.RTC_WAKEUP, next!!.type)
    }

    @Test
    fun getAccessToken_returnsNull_whenNotSignedIn() {
        val manager = DriveBackupManager(context)
        assertNull("no account signed in", manager.getSignedInAccount())
        assertFalse("isSignedIn returns false", manager.isSignedIn())
        assertNull("email is null", manager.getSignedInEmail())
    }
}
