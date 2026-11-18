package com.qsc.accounting.domain

import com.qsc.accounting.data.model.Frequency
import java.time.LocalDate
import java.time.YearMonth

/**
 * 固定收支的日期规则，移植自插件 utils/date_utils.py 的 scheduled / next_date
 * 与 services/recurring_service.run_due()。
 *
 * 三条容易写错的边界：
 * 1. 每月 31 号在没有 31 号的月份记在月末；
 * 2. 每年 2 月 29 日在非闰年记在 2 月 28 日；
 * 3. 暂停期间不生成，但游标照常推进到当天——恢复后不会补出暂停期的账。
 */
data class RecurrenceSpec(
    val frequency: Frequency,
    val dayOfMonth: Int?,
    val monthOfYear: Int?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val effectiveFrom: LocalDate,
)

data class GenerationPlan(
    /** 本次应当生成的日期；已停用或不需要补录时为空。 */
    val occurrences: List<LocalDate>,
    /** 处理完之后游标应该推进到的日期；null 表示这次没有碰游标。 */
    val processedThrough: LocalDate?,
    /** 还有更早的补齐批次没跑完（单次最多扫 366 天）。 */
    val hasMore: Boolean,
)

object Recurrence {

    fun scheduled(spec: RecurrenceSpec, candidate: LocalDate): Boolean {
        if (candidate.isBefore(spec.startDate)) return false
        spec.endDate?.let { if (candidate.isAfter(it)) return false }
        return when (spec.frequency) {
            Frequency.DAILY -> true
            Frequency.MONTHLY ->
                candidate.dayOfMonth == minOf(spec.dayOfMonth ?: 1, candidate.lengthOfMonth())

            Frequency.YEARLY ->
                candidate.monthValue == (spec.monthOfYear ?: 1) &&
                    candidate.dayOfMonth ==
                    minOf(
                        spec.dayOfMonth ?: 1,
                        YearMonth.of(candidate.year, candidate.monthValue).lengthOfMonth(),
                    )
        }
    }

    /** 规则在 [from, to] 内的全部执行日期。 */
    fun occurrences(spec: RecurrenceSpec, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from.isAfter(to)) return emptyList()
        val candidates = mutableListOf<LocalDate>()
        when (spec.frequency) {
            Frequency.DAILY -> {
                var day = from
                while (!day.isAfter(to)) {
                    candidates += day
                    day = day.plusDays(1)
                }
            }

            Frequency.MONTHLY -> {
                var month = YearMonth.from(from)
                val last = YearMonth.from(to)
                while (month <= last) {
                    val day = minOf(spec.dayOfMonth ?: 1, month.lengthOfMonth())
                    candidates += month.atDay(day)
                    month = month.plusMonths(1)
                }
            }

            Frequency.YEARLY -> {
                for (year in from.year..to.year) {
                    val monthValue = spec.monthOfYear ?: 1
                    val day = minOf(
                        spec.dayOfMonth ?: 1,
                        YearMonth.of(year, monthValue).lengthOfMonth(),
                    )
                    candidates += LocalDate.of(year, monthValue, day)
                }
            }
        }
        return candidates
            .filter { !it.isBefore(from) && !it.isAfter(to) }
            .filter { !it.isBefore(spec.startDate) }
            .filter { spec.endDate == null || !it.isAfter(spec.endDate) }
            .distinct()
            .sorted()
    }

    /** 下一次执行日期；停用或已结束返回 null。与插件 next_date() 同口径。 */
    fun nextOccurrence(
        spec: RecurrenceSpec,
        after: LocalDate,
        enabled: Boolean = true,
    ): LocalDate? {
        if (!enabled) return null
        val start = maxOf(after.plusDays(1), spec.startDate, spec.effectiveFrom)
        val end = spec.endDate ?: LocalDate.of(2199, 12, 31)
        for (offset in 0..367L) {
            val candidate = start.plusDays(offset)
            if (candidate.isAfter(end)) break
            if (scheduled(spec, candidate)) return candidate
        }
        return null
    }

    /**
     * 计算一次补录的边界。月份/年份规则只在这一步做区间裁剪，
     * 具体日期交给 occurrences()，与插件逐日扫描等价但更快。
     */
    fun plan(
        spec: RecurrenceSpec,
        enabled: Boolean,
        catchUp: Boolean,
        processedThrough: LocalDate?,
        today: LocalDate,
    ): GenerationPlan {
        var start = maxOf(spec.startDate, spec.effectiveFrom)
        if (processedThrough != null) start = maxOf(start, processedThrough.plusDays(1))
        val end = spec.endDate?.let { minOf(today, it) } ?: today
        // 停用或关闭补录：不生成历史，但游标推进到当天，恢复后不补账。
        if (!enabled || !catchUp) start = maxOf(start, today)
        if (start.isAfter(end)) return GenerationPlan(emptyList(), null, false)

        val batchEnd = minOf(end, start.plusDays(365))
        val occurrences = if (enabled) occurrences(spec, start, batchEnd) else emptyList()
        return GenerationPlan(
            occurrences = occurrences,
            processedThrough = batchEnd,
            hasMore = batchEnd.isBefore(end),
        )
    }
}
