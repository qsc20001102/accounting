package com.qsc.accounting.domain

import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 周期均摊算法，逐行移植自插件 utils/amortization.py。
 *
 * 关键性质：余下的分依次分给周期前几天，任意连续日期区间求和都能精确回到周期总额。
 * 这段代码不要重新推导，直接照抄，否则跨月/跨年对不上账。
 */
object Amortization {

    /** 一笔固定收支归属的均摊周期（月度=自然月，年度=自然年，每天=当天）。 */
    fun allocationPeriod(frequency: Frequency, occurrence: LocalDate): Pair<LocalDate, LocalDate> =
        when (frequency) {
            Frequency.MONTHLY -> occurrence.withDayOfMonth(1) to
                occurrence.withDayOfMonth(occurrence.lengthOfMonth())

            Frequency.YEARLY -> LocalDate.of(occurrence.year, 1, 1) to
                LocalDate.of(occurrence.year, 12, 31)

            Frequency.DAILY -> occurrence to occurrence
        }

    /** 金额 [amount] 均摊在 [periodStart, periodEnd] 上，其中 [start, end] 这段区间的份额。 */
    fun allocatedCents(
        amount: Long,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        start: LocalDate,
        end: LocalDate,
    ): Long {
        val first = maxOf(start, periodStart)
        val last = minOf(end, periodEnd)
        if (first > last) return 0L

        val count = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1
        val base = amount / count
        val remainder = amount % count
        val left = ChronoUnit.DAYS.between(periodStart, first)
        val right = ChronoUnit.DAYS.between(periodStart, last) + 1
        return base * (right - left) + minOf(right, remainder) - minOf(left, remainder)
    }

    /**
     * 规则在 [start, end] 内所有应执行日期；对应 statistics_service.planned_occurrences()。
     * 月末回退（31 号在只有 30 天的月份记在月末）与 2 月 29 日非闰年回退都在这里。
     */
    fun plannedOccurrences(rule: PlannedRule, start: LocalDate, end: LocalDate): List<LocalDate> {
        if (start > end) return emptyList()
        val candidates: List<LocalDate> = when (rule.frequency) {
            Frequency.DAILY -> {
                val days = ChronoUnit.DAYS.between(start, end).toInt()
                (0..days).map { start.plusDays(it.toLong()) }
            }

            Frequency.MONTHLY -> {
                val firstMonth = start.year * 12 + start.monthValue - 1
                val lastMonth = end.year * 12 + end.monthValue - 1
                (firstMonth..lastMonth).map { index ->
                    val year = index / 12
                    val month = index % 12 + 1
                    val day = minOf(rule.dayOfMonth, LocalDate.of(year, month, 1).lengthOfMonth())
                    LocalDate.of(year, month, day)
                }
            }

            Frequency.YEARLY -> (start.year..end.year).map { year ->
                val day = minOf(
                    rule.dayOfMonth,
                    LocalDate.of(year, rule.monthOfYear, 1).lengthOfMonth(),
                )
                LocalDate.of(year, rule.monthOfYear, day)
            }
        }
        return candidates.filter { value ->
            !value.isBefore(rule.startDate) &&
                !value.isBefore(rule.validFrom) &&
                (rule.endDate == null || !value.isAfter(rule.endDate)) &&
                (rule.validUntil == null || value.isBefore(rule.validUntil))
        }
    }

    /** 当前周期内某笔记录的收支方向。 */
    fun signedCents(type: EntryType, cents: Long): Long =
        if (type == EntryType.INCOME) cents else -cents
}
