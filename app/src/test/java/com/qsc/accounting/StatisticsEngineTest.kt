package com.qsc.accounting

import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.data.model.StatisticsMode
import com.qsc.accounting.domain.Period
import com.qsc.accounting.domain.PlannedRule
import com.qsc.accounting.domain.StatsEntry
import com.qsc.accounting.domain.StatsQuery
import com.qsc.accounting.domain.StatsWindow
import com.qsc.accounting.domain.StatisticsEngine
import com.qsc.accounting.domain.Summary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 移植自插件的 tests/test_statistics_service.py。
 * 时钟固定为 2026-10-26，与插件测试里的 patch 一致，方便逐条对拍。
 */
class StatisticsEngineTest {

    private val today = LocalDate.of(2026, 10, 26)

    private fun entry(
        date: String,
        cents: Long,
        type: EntryType = EntryType.INCOME,
        categoryId: Long = if (type == EntryType.INCOME) 1 else 2,
        name: String = if (type == EntryType.INCOME) "工资" else "吃饭",
        periodStart: String? = null,
        periodEnd: String? = null,
    ) = StatsEntry(
        type = type,
        categoryId = categoryId,
        categoryName = name,
        categoryColor = "#5264df",
        amountCents = cents,
        date = LocalDate.parse(date),
        periodStart = periodStart?.let { LocalDate.parse(it) },
        periodEnd = periodEnd?.let { LocalDate.parse(it) },
    )

    private fun summary(
        query: StatsQuery,
        entries: List<StatsEntry> = emptyList(),
        rules: List<PlannedRule> = emptyList(),
        executed: Set<Pair<Long, String>> = emptySet(),
        mode: StatisticsMode = StatisticsMode.CASH,
        includePending: Boolean = true,
    ): Summary = StatisticsEngine.summary(
        StatsWindow.of(query, today),
        mode,
        includePending,
        entries,
        rules,
        executed,
    )

    private fun plan(
        executed: Boolean = false,
    ): Pair<PlannedRule, StatsEntry?> {
        val rule = PlannedRule(
            ruleId = 7,
            type = EntryType.INCOME,
            categoryId = 1,
            categoryName = "工资",
            categoryColor = "#169b7b",
            amountCents = 310_001,
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.of(2026, 10, 1),
            endDate = LocalDate.of(2026, 10, 31),
            dayOfMonth = 31,
            monthOfYear = 1,
            validFrom = LocalDate.of(2026, 10, 1),
            validUntil = null,
            enabled = true,
        )
        val executedEntry = if (executed) {
            entry(
                date = "2026-10-31",
                cents = 310_001,
                periodStart = "2026-10-01",
                periodEnd = "2026-10-31",
            )
        } else {
            null
        }
        return rule to executedEntry
    }

    // ------------------------------------------------------------------ 按到账日

    @Test
    fun current_period_clips_only_the_trend() {
        val entries = listOf(
            entry("2026-10-25", 10_000),
            entry("2026-10-26", 2_500, EntryType.EXPENSE),
            entry("2026-10-27", 90_000),
        )
        val month = summary(StatsQuery(period = Period.MONTH, date = "2026-10"), entries)

        assertEquals(100_000L, month.incomeCents)
        assertEquals(2_500L, month.expenseCents)
        assertEquals(3, month.recordCount)
        assertEquals(26, month.trend.size)
        assertEquals("2026-10-26", month.trend.last().label)
        assertEquals(10_000L, month.trend.sumOf { it.incomeCents })
        assertEquals(7_500L, month.trend.sumOf { it.netCents })

        val year = summary(StatsQuery(period = Period.YEAR, date = "2026"), entries)
        assertEquals(10, year.trend.size)
        assertEquals("2026-10", year.trend.last().label)

        val custom = summary(
            StatsQuery(period = Period.CUSTOM, startDate = "2026-10-25", endDate = "2026-11-02"),
            entries,
        )
        assertEquals(2, custom.trend.size)
        assertEquals("2026-10-26", custom.trend.last().label)
    }

    @Test
    fun daily_trend_crosses_the_year_without_changing_daily_totals() {
        val entries = listOf(
            entry("2025-12-26", 80_000),
            entry("2025-12-27", 10_000),
            entry("2026-01-01", 2_000, EntryType.EXPENSE),
            entry("2026-01-02", 3_000),
        )
        val result = summary(StatsQuery(period = Period.DAY, date = "2026-01-02"), entries)

        assertEquals("2026-01-02", result.startDate)
        assertEquals("2026-01-02", result.endDate)
        assertEquals(3_000L, result.incomeCents)
        assertEquals(0L, result.expenseCents)
        assertEquals(1, result.recordCount)
        assertEquals(7, result.trend.size)
        assertEquals("2025-12-27", result.trend.first().label)
        assertEquals(10_000L, result.trend.first().incomeCents)
        assertEquals(2_000L, result.trend[5].expenseCents)
    }

    @Test
    fun an_empty_day_still_has_weekly_history() {
        val entries = listOf(entry("2026-10-25", 50_000))
        val result = summary(StatsQuery(period = Period.DAY, date = "2026-10-26"), entries)

        assertEquals(0, result.recordCount)
        assertEquals(0L, result.incomeCents)
        assertEquals(7, result.trend.size)
        assertEquals(50_000L, result.trend[5].netCents)
    }

    @Test
    fun historical_periods_keep_complete_labels() {
        assertEquals(29, summary(StatsQuery(period = Period.MONTH, date = "2024-02")).trend.size)
        assertEquals(12, summary(StatsQuery(period = Period.YEAR, date = "2025")).trend.size)
        assertEquals(
            4,
            summary(
                StatsQuery(period = Period.CUSTOM, startDate = "2025-12-30", endDate = "2026-01-02"),
            ).trend.size,
        )
    }

    @Test
    fun future_periods_have_no_trend() {
        listOf(
            StatsQuery(period = Period.DAY, date = "2026-11-10"),
            StatsQuery(period = Period.MONTH, date = "2026-11"),
            StatsQuery(period = Period.YEAR, date = "2027"),
            StatsQuery(period = Period.CUSTOM, startDate = "2026-10-27", endDate = "2026-11-01"),
        ).forEach { query ->
            assertTrue(summary(query).trend.isEmpty())
        }
    }

    @Test
    fun future_daily_window_excludes_future_dates() {
        val result = summary(StatsQuery(period = Period.DAY, date = "2026-10-28"))
        assertEquals(5, result.trend.size)
        assertEquals("2026-10-26", result.trend.last().label)
    }

    // ------------------------------------------------------------------ 按周期均摊

    @Test
    fun pending_plan_is_amortized_and_clipped_in_yearly_bucket() {
        val (rule, _) = plan()

        val month = summary(
            StatsQuery(period = Period.MONTH, date = "2026-10"),
            rules = listOf(rule),
            mode = StatisticsMode.AMORTIZED,
        )
        assertEquals(310_001L, month.incomeCents)
        assertEquals(310_001L, month.pendingIncomeCents)
        assertEquals(260_001L, month.trend.sumOf { it.incomeCents })

        val year = summary(
            StatsQuery(period = Period.YEAR, date = "2026"),
            rules = listOf(rule),
            mode = StatisticsMode.AMORTIZED,
        )
        assertEquals(310_001L, year.incomeCents)
        assertEquals(260_001L, year.trend.sumOf { it.incomeCents })

        val day = summary(
            StatsQuery(period = Period.DAY, date = "2026-10-02"),
            rules = listOf(rule),
            mode = StatisticsMode.AMORTIZED,
        )
        assertEquals(10_000L, day.incomeCents)
        assertEquals(10_000L, day.pendingIncomeCents)
        assertEquals(10_001L, day.trend[5].incomeCents)

        val excluded = summary(
            StatsQuery(period = Period.MONTH, date = "2026-10"),
            rules = listOf(rule),
            mode = StatisticsMode.AMORTIZED,
            includePending = false,
        )
        assertTrue(excluded.trend.all { it.incomeCents == 0L })
    }

    @Test
    fun executed_record_keeps_totals_without_duplicating_the_plan() {
        val (rule, executedEntry) = plan(executed = true)
        val result = summary(
            StatsQuery(period = Period.YEAR, date = "2026"),
            entries = listOf(executedEntry!!),
            rules = listOf(rule),
            executed = setOf(rule.ruleId to "2026-10-31"),
            mode = StatisticsMode.AMORTIZED,
        )

        assertEquals(310_001L, result.incomeCents)
        assertEquals(0L, result.pendingIncomeCents)
        assertEquals(260_001L, result.trend.last().incomeCents)
    }

    @Test
    fun categories_are_grouped_and_sorted() {
        val entries = listOf(
            entry("2026-10-10", 5_000, EntryType.EXPENSE, 2, "吃饭"),
            entry("2026-10-11", 9_000, EntryType.EXPENSE, 3, "交通"),
            entry("2026-10-12", 1_000, EntryType.EXPENSE, 2, "吃饭"),
        )
        val result = summary(StatsQuery(period = Period.MONTH, date = "2026-10"), entries)

        val expense = result.categories.getValue(EntryType.EXPENSE)
        assertEquals(listOf("交通", "吃饭"), expense.map { it.name })
        assertEquals(9_000L, expense[0].amountCents)
        assertEquals(6_000L, expense[1].amountCents)
        assertTrue(result.categories.getValue(EntryType.INCOME).isEmpty())
    }

    @Test
    fun day_labels_never_go_before_the_supported_minimum() {
        val result = summary(StatsQuery(period = Period.DAY, date = "1900-01-01"))
        assertEquals(listOf("1900-01-01"), result.trend.map { it.label })
    }
}
