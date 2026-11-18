package com.qsc.accounting

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.qsc.accounting.data.LedgerRepository
import com.qsc.accounting.data.Prefs
import com.qsc.accounting.data.RecurringRepository
import com.qsc.accounting.data.db.AppDatabase
import com.qsc.accounting.notify.NotificationScheduler
import com.qsc.accounting.notify.SummaryNotifications
import com.qsc.accounting.widget.WidgetUpdater
import com.qsc.accounting.work.RecurringWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** 极简手写依赖容器：只有一条依赖链，不值得引入 Hilt。 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val database = AppDatabase.build(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = Prefs(appContext)
    val repository = LedgerRepository(database, prefs)
    val recurring = RecurringRepository(database)
    val notifications = NotificationScheduler(appContext)

    fun refreshWidget() = WidgetUpdater.requestUpdate(appContext)

    fun rescheduleNotifications() = notifications.reschedule(prefs)

    fun isNotificationPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun sendTestNotification(onResult: (String) -> Unit) {
        scope.launch {
            val result = runCatching {
                SummaryNotifications.show(
                    appContext,
                    NotificationScheduler.KIND_DAILY,
                    prefs,
                    // 测试通知用独立 ID，避免和真正的日报互相覆盖。
                    notificationId = 2099,
                )
            }.getOrNull() ?: SummaryNotifications.Result.FAILED

            onResult(
                when (result) {
                    SummaryNotifications.Result.POSTED -> "已发送测试通知，下拉通知栏看看"
                    SummaryNotifications.Result.NOT_ENABLED -> "系统里本应用的通知是关闭的，去设置里打开"
                    SummaryNotifications.Result.NO_PERMISSION -> "没有通知权限，请先允许通知"
                    SummaryNotifications.Result.CHANNEL_BLOCKED -> "「收支提醒」通知渠道被关闭了，去设置里打开"
                    SummaryNotifications.Result.FAILED -> "发送失败，请检查系统通知设置"
                },
            )
        }
    }
}

class AccountingApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        SummaryNotifications.ensureChannel(this)
        container.rescheduleNotifications()
        WidgetUpdater.requestUpdate(this)

        scheduleRecurringWork()
    }

    /**
     * 固定收支的后台补录：每 6 小时巡检一次，另外每次启动立刻补一次。
     * 真正的幂等在数据库（recurring_executions），重复执行不会多记账。
     */
    private fun scheduleRecurringWork() {
        // 某些定制 ROM 或测试环境里 WorkManager 可能没初始化好；补录失败不该影响记账主流程。
        runCatching {
            val manager = WorkManager.getInstance(this)
            manager.enqueueUniquePeriodicWork(
                RecurringWorker.PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RecurringWorker>(6, TimeUnit.HOURS).build(),
            )
            manager.enqueueUniqueWork(
                RecurringWorker.IMMEDIATE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RecurringWorker>()
                    .setInitialDelay(3, TimeUnit.SECONDS)
                    .build(),
            )
        }
    }
}
