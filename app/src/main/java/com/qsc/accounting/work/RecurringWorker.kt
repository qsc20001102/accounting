package com.qsc.accounting.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.qsc.accounting.AccountingApp

/** 后台补齐固定收支；幂等凭据在数据库层，重复执行不会多记一笔。 */
class RecurringWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? AccountingApp ?: return Result.success()
        return try {
            app.container.recurring.runDue()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK = "accounting-recurring-periodic"
        const val IMMEDIATE_WORK = "accounting-recurring-immediate"
    }
}
