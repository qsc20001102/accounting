package com.qsc.accounting.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.qsc.accounting.data.Prefs
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * 用 AlarmManager 排每日/每月提醒。
 *
 * 为什么不交给 WorkManager：日期的准点性靠延时任务很容易被 Doze 推走几小时；
 * AlarmManager 的 setAndAllowWhileIdle 在 Doze 下仍能触发（最多延迟几分钟），够用。
 * 这里不需要精确闹钟权限：能精确就精确，不能就退化成允许唤醒的普通闹钟。
 */
class NotificationScheduler(private val context: Context) {

    private val alarms: AlarmManager? = context.getSystemService(AlarmManager::class.java)

    fun reschedule(prefs: Prefs, now: LocalDateTime = LocalDateTime.now()) {
        cancelAll()
        if (!prefs.notificationsEnabled) return
        if (prefs.dailyEnabled) {
            schedule(
                requestCode = REQUEST_DAILY,
                kind = KIND_DAILY,
                atMillis = nextDaily(now, prefs.dailyHour, prefs.dailyMinute),
            )
        }
        if (prefs.monthlyEnabled) {
            schedule(
                requestCode = REQUEST_MONTHLY,
                kind = KIND_MONTHLY,
                atMillis = nextMonthly(now, prefs.monthlyDay, prefs.monthlyHour, prefs.monthlyMinute),
            )
        }
    }

    private fun schedule(requestCode: Int, kind: String, atMillis: Long) {
        val manager = alarms ?: return
        val pending = pendingIntent(requestCode, kind)
        runCatching {
            val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        }
    }

    private fun cancelAll() {
        val manager = alarms ?: return
        listOf(REQUEST_DAILY to KIND_DAILY, REQUEST_MONTHLY to KIND_MONTHLY).forEach { (code, kind) ->
            runCatching { manager.cancel(pendingIntent(code, kind)) }
        }
    }

    private fun pendingIntent(requestCode: Int, kind: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMIND
            putExtra(ReminderReceiver.EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val KIND_DAILY = "daily"
        const val KIND_MONTHLY = "monthly"
        private const val REQUEST_DAILY = 1001
        private const val REQUEST_MONTHLY = 1002

        /** 下一次「每天 hh:mm」；已经过了今天的时间就顺延到明天。 */
        fun nextDaily(now: LocalDateTime, hour: Int, minute: Int): Long {
            var target = now.toLocalDate().atTime(hour, minute)
            if (!target.isAfter(now)) target = target.plusDays(1)
            return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }

        /** 下一次「每月 day 号 hh:mm」；没有该日期按月末，已经过了就顺延到下一月。 */
        fun nextMonthly(now: LocalDateTime, day: Int, hour: Int, minute: Int): Long {
            fun atMonth(month: YearMonth): LocalDateTime {
                val safeDay = minOf(day.coerceAtLeast(1), month.lengthOfMonth())
                return month.atDay(safeDay).atTime(hour, minute)
            }

            var target = atMonth(YearMonth.from(now))
            if (!target.isAfter(now)) target = atMonth(YearMonth.from(now).plusMonths(1))
            return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
    }
}
