package com.qsc.accounting.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.qsc.accounting.data.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 提醒闹钟的落点，同时负责开机/升级后把闹钟重新排上。 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REMIND -> {
                val kind = intent.getStringExtra(EXTRA_KIND) ?: NotificationScheduler.KIND_DAILY
                val prefs = Prefs(context)
                // 先把下一次排上：通知构建即使失败，也不影响后续提醒。
                NotificationScheduler(context).reschedule(prefs)

                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        SummaryNotifications.show(context, kind, prefs)
                    } finally {
                        pending.finish()
                    }
                }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> NotificationScheduler(context).reschedule(Prefs(context))
        }
    }

    companion object {
        const val ACTION_REMIND = "com.qsc.accounting.action.REMIND"
        const val EXTRA_KIND = "kind"
    }
}
