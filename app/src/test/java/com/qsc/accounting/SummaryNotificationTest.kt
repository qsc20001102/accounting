package com.qsc.accounting

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.notify.NotificationScheduler
import com.qsc.accounting.notify.SummaryNotifications
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 通知内容这一环：真的发一条，检查标题与摘要。
 * 闹钟能不能按时响是系统调度问题，这里验证的是「响了之后一定看得见」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SummaryNotificationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun grantNotificationPermission() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun postedNotifications(): List<Notification> {
        val manager = context.getSystemService(NotificationManager::class.java)
        return shadowOf(manager).allNotifications
    }

    @Test
    fun daily_summary_notification_contains_totals() = runBlocking {
        grantNotificationPermission()
        val prefs = Prefs(context)
        prefs.notificationsEnabled = true

        val result = SummaryNotifications.show(context, NotificationScheduler.KIND_DAILY, prefs)

        assertEquals(SummaryNotifications.Result.POSTED, result)
        val posted = postedNotifications()
        assertEquals(1, posted.size)
        val extras = posted.first().extras
        assertTrue(extras.getString(Notification.EXTRA_TITLE)!!.contains("今日"))
        assertTrue(extras.getString(Notification.EXTRA_TEXT)!!.contains("支出"))
    }

    @Test
    fun monthly_summary_uses_month_title() = runBlocking {
        grantNotificationPermission()
        val prefs = Prefs(context)
        prefs.notificationsEnabled = true

        SummaryNotifications.show(context, NotificationScheduler.KIND_MONTHLY, prefs)

        val posted = postedNotifications()
        assertEquals(1, posted.size)
        assertTrue(
            posted.first().extras.getString(Notification.EXTRA_TITLE)!!.contains("本月"),
        )
    }

    @Test
    fun nothing_is_posted_when_notifications_are_off() = runBlocking {
        grantNotificationPermission()
        val prefs = Prefs(context)
        prefs.notificationsEnabled = false

        val result = SummaryNotifications.show(context, NotificationScheduler.KIND_DAILY, prefs)

        assertEquals(SummaryNotifications.Result.NOT_ENABLED, result)
        assertTrue(postedNotifications().isEmpty())
    }
}
