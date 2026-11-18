package com.qsc.accounting.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.qsc.accounting.AccountingApp
import com.qsc.accounting.MainActivity
import com.qsc.accounting.R
import com.qsc.accounting.core.Dates
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.domain.Period
import com.qsc.accounting.domain.StatsQuery

/** 把「今日/本月 收入、支出、结余」做成一条系统通知。 */
object SummaryNotifications {

    /**
     * 提醒用独立渠道：IMPORTANCE_HIGH 才会弹出来。
     * 旧的 summary 渠道是 DEFAULT，用户升级后如果继续用同一个渠道，无法把重要级别提上去，
     * 所以换 ID 并删掉旧渠道。
     */
    const val CHANNEL_ID = "reminders"
    private const val LEGACY_CHANNEL_ID = "summary"

    /** 发通知的结果，用来给用户一句准确的话，而不是「已发送」这种空话。 */
    enum class Result {
        POSTED,
        NOT_ENABLED,
        NO_PERMISSION,
        CHANNEL_BLOCKED,
        FAILED,
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "收支提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "每日与每月的收支小结"
            setShowBadge(false)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    suspend fun show(
        context: Context,
        kind: String,
        prefs: Prefs,
        notificationId: Int? = null,
    ): Result {
        if (!prefs.notificationsEnabled) return Result.NOT_ENABLED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.NO_PERMISSION
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return Result.NOT_ENABLED
        }

        val container = (context.applicationContext as? AccountingApp)?.container
            ?: return Result.FAILED
        val today = Dates.today()
        val monthly = kind == NotificationScheduler.KIND_MONTHLY
        val query = if (monthly) {
            StatsQuery(period = Period.MONTH, date = Dates.yearMonth(today))
        } else {
            StatsQuery(period = Period.DAY, date = today.toString())
        }
        val summary = container.repository.summary(
            query = query,
            mode = prefs.statisticsMode,
            includePending = prefs.includePending,
            today = today,
        )

        ensureChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return Result.FAILED
        val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return Result.CHANNEL_BLOCKED
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return Result.CHANNEL_BLOCKED
        }

        val title = if (monthly) "本月收支" else "今日收支"
        val body = "支出 ¥${summary.expense}　收入 ¥${summary.income}　结余 ¥${summary.net}"
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .build()

        return try {
            val id = notificationId ?: if (monthly) NOTIFY_MONTHLY else NOTIFY_DAILY
            NotificationManagerCompat.from(context).notify(id, notification)
            Result.POSTED
        } catch (_: SecurityException) {
            Result.NO_PERMISSION
        } catch (_: Exception) {
            Result.FAILED
        }
    }

    private const val NOTIFY_DAILY = 2001
    private const val NOTIFY_MONTHLY = 2002
}
