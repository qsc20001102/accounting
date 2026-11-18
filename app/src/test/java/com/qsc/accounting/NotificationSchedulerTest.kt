package com.qsc.accounting

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.notify.NotificationScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 开关状态到闹钟数量的映射：这是「开了才会响」的最直接证据。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationSchedulerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun scheduledCount(): Int {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        return shadowOf(alarmManager).scheduledAlarms.size
    }

    @Test
    fun schedules_one_alarm_per_enabled_reminder() {
        val prefs = Prefs(context)
        prefs.notificationsEnabled = true
        prefs.dailyEnabled = true
        prefs.monthlyEnabled = true

        NotificationScheduler(context).reschedule(prefs)

        assertEquals(2, scheduledCount())
    }

    @Test
    fun master_switch_off_cancels_everything() {
        val prefs = Prefs(context)
        prefs.notificationsEnabled = true
        prefs.dailyEnabled = true
        NotificationScheduler(context).reschedule(prefs)
        assertEquals(1, scheduledCount())

        prefs.notificationsEnabled = false
        NotificationScheduler(context).reschedule(prefs)
        assertEquals(0, scheduledCount())
    }

    @Test
    fun rescheduling_does_not_pile_up_alarms() {
        val prefs = Prefs(context)
        prefs.notificationsEnabled = true
        prefs.dailyEnabled = true

        NotificationScheduler(context).reschedule(prefs)
        NotificationScheduler(context).reschedule(prefs)
        NotificationScheduler(context).reschedule(prefs)

        assertEquals(1, scheduledCount())
    }
}
