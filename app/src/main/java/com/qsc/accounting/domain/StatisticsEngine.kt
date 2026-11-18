package com.qsc.accounting.domain

import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.StatisticsMode
import java.time.LocalDate

/**
 * 统计引擎：纯函数，只吃已装载的数据，不碰数据库。对应插件 services/statistics_service.py。
 *
 * cash（按到账日）：汇总与趋势都只数实际记录。
 * amortized（按周期均摊）：固定收支按周期分摊到每天，可选补上「尚未到账的计划份额」。
 */
object StatisticsEngine {

    private data class CategoryKey(
        val type: EntryType,
        val id: Long,
        val name: String,
        val color: String,
    )

    fun summary(
        window: StatsWindow,
        mode: StatisticsMode,
        includePending: Boolean,
        entries: List<StatsEntry>,
        rules: List<PlannedRule>,
        executed: Set<Pair<Long, String>>,
    ): Summary = when (mode) {
        StatisticsMode.CASH -> cash(window, entries)
        StatisticsMode.AMORTIZED -> amortized(window, includePending, entries, rules, executed)
    }

    private fun cash(window: StatsWindow, entries: List<StatsEntry>): Summary {
        val inRange = entries.filter { it.date in window.start..window.end }
        var income = 0L
        var expense = 0L
        val grouped = LinkedHashMap<CategoryKey, Long>()

        for (entry in inRange) {
            if (entry.type == EntryType.INCOME) income += entry.amountCents else expense += entry.amountCents
            val key = CategoryKey(entry.type, entry.categoryId, entry.categoryName, entry.categoryColor)
            grouped[key] = (grouped[key] ?: 0L) + entry.amountCents
        }

        val buckets = window.labels.associateWith { LongArray(2) }
        if (window.labels.isNotEmpty()) {
            for (entry in entries) {
                if (entry.date.isBefore(window.trendStart) || entry.date.isAfter(window.trendEnd)) continue
                val label = if (window.period == Period.YEAR) {
                    "%04d-%02d".format(entry.date.year, entry.date.monthValue)
                } else {
                    entry.date.toString()
                }
                val bucket = buckets[label] ?: continue
                bucket[if (entry.type == EntryType.INCOME) 0 else 1] += entry.amountCents
            }
        }

        return Summary(
            period = window.period,
            mode = StatisticsMode.CASH,
            includePending = false,
            startDate = window.start.toString(),
            endDate = window.end.toString(),
            incomeCents = income,
            expenseCents = expense,
            pendingIncomeCents = 0,
            pendingExpenseCents = 0,
            categories = groupCategories(grouped),
            trend = window.labels.map { label ->
                val bucket = buckets.getValue(label)
                TrendPoint(label, bucket[0], bucket[1])
            },
            recordCount = inRange.size,
        )
    }

    private fun amortized(
        window: StatsWindow,
        includePending: Boolean,
        entries: List<StatsEntry>,
        rules: List<PlannedRule>,
        executed: Set<Pair<Long, String>>,
    ): Summary {
        val totals = LongArray(2)
        val pending = LongArray(2)
        val grouped = LinkedHashMap<CategoryKey, Long>()
        val buckets = LinkedHashMap<String, LongArray>().apply {
            window.labels.forEach { put(it, LongArray(2)) }
        }

        fun add(
            type: EntryType,
            categoryId: Long,
            categoryName: String,
            categoryColor: String,
            amountCents: Long,
            periodStart: LocalDate,
            periodEnd: LocalDate,
            planned: Boolean,
        ) {
            val slot = if (type == EntryType.INCOME) 0 else 1
            val allocated =
                Amortization.allocatedCents(amountCents, periodStart, periodEnd, window.start, window.end)
            if (allocated != 0L) {
                totals[slot] += allocated
                if (planned) pending[slot] += allocated
                val key = CategoryKey(type, categoryId, categoryName, categoryColor)
                grouped[key] = (grouped[key] ?: 0L) + allocated
            }

            val first = maxOf(window.trendStart, periodStart)
            val last = minOf(window.trendEnd, periodEnd)
            if (first.isAfter(last)) return

            if (window.period == Period.YEAR) {
                for (month in first.monthValue..last.monthValue) {
                    val monthStart = LocalDate.of(window.start.year, month, 1)
                    val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())
                    val label = "%04d-%02d".format(window.start.year, month)
                    val bucket = buckets[label] ?: continue
                    bucket[slot] += Amortization.allocatedCents(
                        amountCents,
                        periodStart,
                        periodEnd,
                        maxOf(first, monthStart),
                        minOf(last, monthEnd),
                    )
                }
            } else {
                var day = first
                while (!day.isAfter(last)) {
                    val label = day.toString()
                    val bucket = buckets[label]
                    if (bucket != null) {
                        bucket[slot] += Amortization.allocatedCents(
                            amountCents,
                            periodStart,
                            periodEnd,
                            day,
                            day,
                        )
                    }
                    day = day.plusDays(1)
                }
            }
        }

        for (entry in entries) {
            add(
                type = entry.type,
                categoryId = entry.categoryId,
                categoryName = entry.categoryName,
                categoryColor = entry.categoryColor,
                amountCents = entry.amountCents,
                periodStart = entry.periodStart ?: entry.date,
                periodEnd = entry.periodEnd ?: entry.date,
                planned = false,
            )
        }

        if (includePending) {
            for (rule in rules) {
                if (!rule.enabled) continue
                for (occurrence in Amortization.plannedOccurrences(rule, window.loadStart, window.end)) {
                    if ((rule.ruleId to occurrence.toString()) in executed) continue
                    val (periodStart, periodEnd) = Amortization.allocationPeriod(rule.frequency, occurrence)
                    add(
                        type = rule.type,
                        categoryId = rule.categoryId,
                        categoryName = rule.categoryName,
                        categoryColor = rule.categoryColor,
                        amountCents = rule.amountCents,
                        periodStart = periodStart,
                        periodEnd = periodEnd,
                        planned = true,
                    )
                }
            }
        }

        return Summary(
            period = window.period,
            mode = StatisticsMode.AMORTIZED,
            includePending = includePending,
            startDate = window.start.toString(),
            endDate = window.end.toString(),
            incomeCents = totals[0],
            expenseCents = totals[1],
            pendingIncomeCents = pending[0],
            pendingExpenseCents = pending[1],
            categories = groupCategories(grouped),
            trend = window.labels.map { label ->
                val bucket = buckets.getValue(label)
                TrendPoint(label, bucket[0], bucket[1])
            },
            recordCount = entries.count { it.date in window.start..window.end },
        )
    }

    private fun groupCategories(grouped: Map<CategoryKey, Long>): Map<EntryType, List<CategoryTotal>> =
        EntryType.entries.associateWith { type ->
            grouped.entries
                .filter { it.key.type == type }
                .sortedByDescending { it.value }
                .map { (key, amount) ->
                    CategoryTotal(key.id, key.name, key.color, amount)
                }
        }
}
