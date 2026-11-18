package com.qsc.accounting.data

import androidx.room.withTransaction
import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.Money
import com.qsc.accounting.core.fail
import com.qsc.accounting.data.db.AppDatabase
import com.qsc.accounting.data.db.RecurringExecutionEntity
import com.qsc.accounting.data.db.RecurringRuleEntity
import com.qsc.accounting.data.db.RecurringVersionEntity
import com.qsc.accounting.data.db.TransactionAllocationEntity
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.domain.Amortization
import com.qsc.accounting.domain.Recurrence
import com.qsc.accounting.domain.RecurrenceSpec
import kotlinx.coroutines.yield
import java.time.LocalDate
import java.time.YearMonth

/** 规则在界面上的展示形态：带上分类名与「下次执行」。 */
data class RuleView(
    val rule: RecurringRuleEntity,
    val categoryName: String,
    val categoryColor: String,
    val nextExecution: LocalDate?,
    val categoryEnabled: Boolean,
)

data class RecurringInput(
    val id: Long? = null,
    val name: String = "",
    val type: EntryType = EntryType.EXPENSE,
    val categoryId: Long = 0,
    val amount: String = "",
    val frequency: Frequency = Frequency.MONTHLY,
    val dayOfMonth: Int = 1,
    val monthOfYear: Int = 1,
    val startDate: String,
    val endDate: String? = null,
    val enabled: Boolean = true,
    val note: String = "",
)

data class RunResult(val generated: Int, val date: String)

/**
 * 固定收支。生成逻辑对应插件 services/recurring_service.py：
 * recurring_executions 是幂等凭据（用户删掉已生成的记录后不会被重新生成），
 * processed_through 是补录游标（单次最多扫 366 天，分批推进）。
 */
class RecurringRepository(private val db: AppDatabase) {

    private val rules = db.recurring()
    private val transactions = db.transactions()

    suspend fun ruleViews(today: LocalDate = Dates.today()): List<RuleView> {
        val categories = db.categories().all().associateBy { it.id }
        return rules.rules()
            .filter { !it.deleted }
            .map { rule ->
                val category = categories[rule.categoryId]
                val spec = rule.toSpec()
                RuleView(
                    rule = rule,
                    categoryName = category?.name ?: "未知分类",
                    categoryColor = category?.color ?: "#7890a0",
                    nextExecution = Recurrence.nextOccurrence(
                        spec,
                        after = today.minusDays(1),
                        enabled = rule.enabled && category?.enabled == true,
                    ),
                    categoryEnabled = category?.enabled == true,
                )
            }
    }

    suspend fun rule(id: Long): RecurringRuleEntity? = rules.ruleById(id)

    suspend fun save(input: RecurringInput, today: LocalDate = Dates.today()): RecurringRuleEntity {
        val name = input.name.trim()
        if (name.isEmpty()) fail("规则名称不能为空")
        if (name.length > 80) fail("规则名称不能超过 80 个字符")
        val cents = Money.parseToCents(input.amount)
        val start = Dates.parse(input.startDate, "开始日期")
        val end = input.endDate?.takeIf { it.isNotBlank() }?.let { Dates.parse(it, "结束日期") }
        if (end != null && end.isBefore(start)) fail("结束日期不能早于开始日期")
        val note = input.note.trim()
        if (note.length > 500) fail("备注不能超过 500 个字符")

        val dayOfMonth = if (input.frequency == Frequency.DAILY) null else input.dayOfMonth
        if (dayOfMonth != null && dayOfMonth !in 1..31) fail("执行日必须在 1～31 之间")
        val monthOfYear = if (input.frequency == Frequency.YEARLY) input.monthOfYear else null
        if (monthOfYear != null && monthOfYear !in 1..12) fail("执行月必须在 1～12 之间")
        // 二月最多允许 29 日，其他月份拒绝本来就不存在的日期；月度允许 31 日回退。
        if (input.frequency == Frequency.YEARLY && dayOfMonth != null) {
            val limit = YearMonth.of(2000, monthOfYear ?: 1).lengthOfMonth()
            if (dayOfMonth > limit) fail("年度执行月份与日期不匹配")
        }

        val category = db.categories().byId(input.categoryId) ?: fail("分类不存在或已删除")
        if (category.type != input.type) fail("分类与收支类型不一致")
        if (!category.enabled && input.enabled) fail("该分类已停用，请选择启用的分类")

        return db.withTransaction {
            val now = Dates.timestamp()
            if (input.id == null) {
                val entity = RecurringRuleEntity(
                    name = name,
                    type = input.type,
                    categoryId = category.id,
                    amountCents = cents,
                    frequency = input.frequency,
                    startDate = start.toString(),
                    endDate = end?.toString(),
                    dayOfMonth = dayOfMonth,
                    monthOfYear = monthOfYear,
                    enabled = input.enabled,
                    note = note,
                    effectiveFrom = start.toString(),
                    createdAt = now,
                    updatedAt = now,
                )
                val id = rules.insertRule(entity)
                val saved = rules.ruleById(id) ?: fail("规则保存失败")
                saveVersion(saved, start.toString())
                saved
            } else {
                val old = rules.ruleById(input.id) ?: fail("该固定规则不存在")
                if (old.deleted) fail("该固定规则已删除")
                // 新版本规则不追溯改写过去；重新开始补录也基于本次生效日。
                val effective = maxOf(today, start)
                val updated = old.copy(
                    name = name,
                    type = input.type,
                    categoryId = category.id,
                    amountCents = cents,
                    frequency = input.frequency,
                    startDate = start.toString(),
                    endDate = end?.toString(),
                    dayOfMonth = dayOfMonth,
                    monthOfYear = monthOfYear,
                    enabled = input.enabled,
                    note = note,
                    effectiveFrom = effective.toString(),
                    processedThrough = effective.minusDays(1).toString(),
                    updatedAt = now,
                )
                rules.updateRule(updated)
                saveVersion(updated, today.toString())
                updated
            }
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean, today: LocalDate = Dates.today()) {
        db.withTransaction {
            val old = rules.ruleById(id) ?: fail("该固定规则不存在")
            if (old.deleted) fail("该固定规则已删除")
            val effective = maxOf(today, Dates.parse(old.startDate))
            val updated = old.copy(
                enabled = enabled,
                effectiveFrom = effective.toString(),
                processedThrough = effective.minusDays(1).toString(),
                updatedAt = Dates.timestamp(),
            )
            rules.updateRule(updated)
            saveVersion(updated, today.toString())
        }
    }

    suspend fun delete(id: Long, today: LocalDate = Dates.today()) {
        db.withTransaction {
            val rule = rules.ruleById(id) ?: fail("该固定规则不存在")
            if (rule.deleted) fail("规则已删除")
            // 保留历史外键与执行凭据，仅从规则管理列表归档。
            val archived = rule.copy(
                deleted = true,
                enabled = false,
                updatedAt = Dates.timestamp(),
            )
            rules.updateRule(archived)
            saveVersion(archived, today.toString())
        }
    }

    /**
     * 生成所有到期的固定收支，返回本次生成的笔数。
     * 单批最多扫 366 天，循环推进直到没有更多补齐任务。
     */
    suspend fun runDue(today: LocalDate = Dates.today(), catchUp: Boolean = true): RunResult {
        var generated = 0
        var rounds = 0
        while (rounds < 64) {
            val batch = db.withTransaction { runBatch(today, catchUp) }
            generated += batch.generated
            if (!batch.hasMore) break
            rounds++
            yield()
        }
        return RunResult(generated, today.toString())
    }

    private data class Batch(val generated: Int, val hasMore: Boolean)

    private suspend fun runBatch(today: LocalDate, catchUp: Boolean): Batch {
        val categories = db.categories().all().associateBy { it.id }
        val now = Dates.timestamp()
        var generated = 0
        var hasMore = false

        for (rule in rules.rules()) {
            if (rule.deleted) continue
            val category = categories[rule.categoryId] ?: continue
            val enabled = rule.enabled && category.enabled
            val plan = Recurrence.plan(
                spec = rule.toSpec(),
                enabled = enabled,
                catchUp = catchUp,
                processedThrough = rule.processedThrough?.let { Dates.parse(it) },
                today = today,
            )

            for (occurrence in plan.occurrences) {
                // 先抢执行凭据：抢不到说明这笔已经生成过（或用户删过），跳过。
                val claim = rules.insertExecutionIgnore(
                    RecurringExecutionEntity(
                        ruleId = rule.id,
                        occurrenceDate = occurrence.toString(),
                        createdAt = now,
                    ),
                )
                if (claim == -1L) continue

                val transactionId = transactions.insert(
                    TransactionEntity(
                        type = rule.type,
                        categoryId = rule.categoryId,
                        categoryName = category.name,
                        categoryColor = category.color,
                        amountCents = rule.amountCents,
                        transactionDate = occurrence.toString(),
                        note = rule.note,
                        source = EntrySource.RECURRING,
                        recurringRuleId = rule.id,
                        recurringOccurrenceDate = occurrence.toString(),
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                val (periodStart, periodEnd) = Amortization.allocationPeriod(rule.frequency, occurrence)
                rules.insertAllocation(
                    TransactionAllocationEntity(
                        transactionId = transactionId,
                        frequency = rule.frequency,
                        periodStart = periodStart.toString(),
                        periodEnd = periodEnd.toString(),
                    ),
                )
                rules.touchLastExecution(rule.id, occurrence.toString())
                generated++
            }

            plan.processedThrough?.let {
                rules.setProcessedThrough(rule.id, it.toString())
            }
            hasMore = hasMore || plan.hasMore
        }

        return Batch(generated, hasMore)
    }

    private suspend fun saveVersion(rule: RecurringRuleEntity, validFrom: String) {
        // 编辑时撤销尚未生效的旧计划；已入账记录的均摊周期另有不可变快照。
        rules.deleteVersionsFrom(rule.id, validFrom)
        rules.closeVersionsBefore(rule.id, validFrom)
        rules.insertVersion(
            RecurringVersionEntity(
                ruleId = rule.id,
                validFrom = validFrom,
                validUntil = null,
                type = rule.type,
                categoryId = rule.categoryId,
                amountCents = rule.amountCents,
                frequency = rule.frequency,
                startDate = rule.startDate,
                endDate = rule.endDate,
                dayOfMonth = rule.dayOfMonth,
                monthOfYear = rule.monthOfYear,
                enabled = rule.enabled && !rule.deleted,
                note = rule.note,
            ),
        )
    }

    private fun RecurringRuleEntity.toSpec(): RecurrenceSpec = RecurrenceSpec(
        frequency = frequency,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        startDate = Dates.parse(startDate),
        endDate = endDate?.let { Dates.parse(it) },
        effectiveFrom = Dates.parse(effectiveFrom),
    )
}
