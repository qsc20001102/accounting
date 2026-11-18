package com.qsc.accounting

import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.domain.GenerationPlan
import com.qsc.accounting.domain.Recurrence
import com.qsc.accounting.domain.RecurrenceSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 固定收支的日期规则；行为对齐插件 utils/date_utils.py 与 recurring_service.run_due()。
 */
class RecurrenceTest {

    private fun spec(
        frequency: Frequency,
        dayOfMonth: Int? = null,
        monthOfYear: Int? = null,
        start: String = "2026-01-01",
        end: String? = null,
        effectiveFrom: String = start,
    ) = RecurrenceSpec(
        frequency = frequency,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        startDate = LocalDate.parse(start),
        endDate = end?.let { LocalDate.parse(it) },
        effectiveFrom = LocalDate.parse(effectiveFrom),
    )

    private fun dates(plan: GenerationPlan) = plan.occurrences.map { it.toString() }

    // ------------------------------------------------------------------ 日期规则

    @Test
    fun monthly_31_falls_back_to_month_end() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 31)
        assertEquals(
            listOf(
                "2026-02-28",
                "2026-03-31",
                "2026-04-30",
                "2026-05-31",
                "2026-06-30",
                "2026-07-31",
                "2026-08-31",
                "2026-09-30",
            ),
            Recurrence.occurrences(
                monthly,
                LocalDate.parse("2026-02-01"),
                LocalDate.parse("2026-09-30"),
            ).map { it.toString() },
        )
    }

    @Test
    fun yearly_feb_29_falls_back_in_common_years() {
        val yearly = spec(
            Frequency.YEARLY,
            dayOfMonth = 29,
            monthOfYear = 2,
            start = "2023-01-01",
        )
        assertEquals(
            listOf("2023-02-28", "2024-02-29"),
            Recurrence.occurrences(
                yearly,
                LocalDate.parse("2023-01-01"),
                LocalDate.parse("2024-12-31"),
            ).map { it.toString() },
        )
    }

    @Test
    fun daily_occurrences_are_inclusive_on_both_ends() {
        val daily = spec(Frequency.DAILY, start = "2026-03-01")
        val occurrences = Recurrence.occurrences(
            daily,
            LocalDate.parse("2026-03-01"),
            LocalDate.parse("2026-03-05"),
        )
        assertEquals(5, occurrences.size)
        assertEquals("2026-03-01", occurrences.first().toString())
        assertEquals("2026-03-05", occurrences.last().toString())
    }

    @Test
    fun occurrences_respect_the_rule_start_and_end_dates() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 15, start = "2026-03-01", end = "2026-06-30")
        val occurrences = Recurrence.occurrences(
            monthly,
            LocalDate.parse("2026-01-01"),
            LocalDate.parse("2026-12-31"),
        ).map { it.toString() }
        assertEquals(
            listOf("2026-03-15", "2026-04-15", "2026-05-15", "2026-06-15"),
            occurrences,
        )
    }

    // ------------------------------------------------------------------ 下次执行

    @Test
    fun next_occurrence_moves_to_the_following_period() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 18)
        assertEquals(
            "2026-11-18",
            Recurrence.nextOccurrence(monthly, LocalDate.parse("2026-11-17"))?.toString(),
        )
        assertEquals(
            "2026-12-18",
            Recurrence.nextOccurrence(monthly, LocalDate.parse("2026-11-18"))?.toString(),
        )
    }

    @Test
    fun next_occurrence_is_null_when_paused_or_finished() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 18, end = "2026-11-30")
        assertNull(Recurrence.nextOccurrence(monthly, LocalDate.parse("2026-11-01"), enabled = false))
        assertNull(Recurrence.nextOccurrence(monthly, LocalDate.parse("2026-11-30")))
    }

    // ------------------------------------------------------------------ 补录计划

    @Test
    fun plan_catches_up_from_the_cursor() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 18)
        val plan = Recurrence.plan(
            spec = monthly,
            enabled = true,
            catchUp = true,
            processedThrough = LocalDate.parse("2026-09-30"),
            today = LocalDate.parse("2026-11-18"),
        )
        assertEquals(listOf("2026-10-18", "2026-11-18"), dates(plan))
        assertEquals("2026-11-18", plan.processedThrough.toString())
        assertFalse(plan.hasMore)
    }

    @Test
    fun plan_advances_the_cursor_but_generates_nothing_when_paused() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 18)
        val plan = Recurrence.plan(
            spec = monthly,
            enabled = false,
            catchUp = true,
            processedThrough = LocalDate.parse("2026-01-01"),
            today = LocalDate.parse("2026-11-18"),
        )
        assertTrue(plan.occurrences.isEmpty())
        assertEquals("2026-11-18", plan.processedThrough.toString())
    }

    @Test
    fun plan_does_not_backfill_when_catch_up_is_off() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 18)
        val plan = Recurrence.plan(
            spec = monthly,
            enabled = true,
            catchUp = false,
            processedThrough = LocalDate.parse("2026-01-01"),
            today = LocalDate.parse("2026-11-18"),
        )
        assertEquals(listOf("2026-11-18"), dates(plan))
    }

    @Test
    fun plan_stops_at_the_rule_end_date() {
        val monthly = spec(
            Frequency.MONTHLY,
            dayOfMonth = 31,
            start = "2026-01-01",
            end = "2026-03-31",
        )
        val plan = Recurrence.plan(
            spec = monthly,
            enabled = true,
            catchUp = true,
            processedThrough = null,
            today = LocalDate.parse("2026-11-18"),
        )
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31"), dates(plan))
        assertEquals("2026-03-31", plan.processedThrough.toString())
        assertFalse(plan.hasMore)
    }

    @Test
    fun plan_batches_long_backlogs_into_366_day_windows() {
        val daily = spec(Frequency.DAILY, start = "2024-01-01")
        val plan = Recurrence.plan(
            spec = daily,
            enabled = true,
            catchUp = true,
            processedThrough = null,
            today = LocalDate.parse("2026-01-01"),
        )
        assertEquals(366, plan.occurrences.size)
        assertEquals("2024-01-01", plan.occurrences.first().toString())
        assertEquals("2024-12-31", plan.occurrences.last().toString())
        assertEquals("2024-12-31", plan.processedThrough.toString())
        assertTrue(plan.hasMore)
    }

    @Test
    fun plan_does_nothing_before_the_rule_starts() {
        val monthly = spec(Frequency.MONTHLY, dayOfMonth = 5, start = "2026-12-01")
        val plan = Recurrence.plan(
            spec = monthly,
            enabled = true,
            catchUp = true,
            processedThrough = null,
            today = LocalDate.parse("2026-11-18"),
        )
        assertTrue(plan.occurrences.isEmpty())
        assertNull(plan.processedThrough)
        assertFalse(plan.hasMore)
    }

    @Test
    fun plan_respects_a_future_effective_from() {
        // 编辑规则后从当天生效：不能拿旧金额补出「生效日之前」的记录。
        val monthly = spec(
            Frequency.MONTHLY,
            dayOfMonth = 10,
            start = "2026-01-01",
            effectiveFrom = "2026-11-01",
        )
        val plan = Recurrence.plan(
            spec = monthly,
            enabled = true,
            catchUp = true,
            processedThrough = LocalDate.parse("2026-10-31"),
            today = LocalDate.parse("2026-11-18"),
        )
        assertEquals(listOf("2026-11-10"), dates(plan))
    }
}
