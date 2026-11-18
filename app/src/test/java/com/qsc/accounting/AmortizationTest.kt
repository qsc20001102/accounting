package com.qsc.accounting

import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.domain.Amortization
import com.qsc.accounting.domain.PlannedRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AmortizationTest {

    private fun plan(
        frequency: Frequency,
        dayOfMonth: Int,
        monthOfYear: Int = 1,
        start: LocalDate = LocalDate.of(2026, 1, 1),
        end: LocalDate? = null,
    ) = PlannedRule(
        ruleId = 1,
        type = EntryType.INCOME,
        categoryId = 1,
        categoryName = "工资",
        categoryColor = "#169b7b",
        amountCents = 310_001,
        frequency = frequency,
        startDate = start,
        endDate = end,
        dayOfMonth = dayOfMonth,
        monthOfYear = monthOfYear,
        validFrom = start,
        validUntil = null,
        enabled = true,
    )

    @Test
    fun remainder_cents_go_to_the_first_days() {
        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 31)
        // 3100.01 元分到 31 天：前 1 天各多 1 分。
        assertEquals(10_001L, Amortization.allocatedCents(310_001, start, end, start, start))
        assertEquals(
            10_000L,
            Amortization.allocatedCents(310_001, start, end, start.plusDays(1), start.plusDays(1)),
        )
        assertEquals(310_001L, Amortization.allocatedCents(310_001, start, end, start, end))
    }

    @Test
    fun partial_ranges_stay_exact() {
        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 31)
        val firstHalf = Amortization.allocatedCents(310_001, start, end, start, LocalDate.of(2026, 10, 10))
        val secondHalf = Amortization.allocatedCents(
            310_001,
            start,
            end,
            LocalDate.of(2026, 10, 11),
            end,
        )
        assertEquals(310_001L, firstHalf + secondHalf)
        assertEquals(
            100_001L,
            Amortization.allocatedCents(310_001, start, end, start, LocalDate.of(2026, 10, 10)),
        )
    }

    @Test
    fun range_outside_period_is_zero() {
        val start = LocalDate.of(2026, 10, 1)
        val end = LocalDate.of(2026, 10, 31)
        assertEquals(0L, Amortization.allocatedCents(310_001, start, end, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 2)))
    }

    @Test
    fun monthly_day_31_falls_back_to_month_end() {
        val rule = plan(Frequency.MONTHLY, dayOfMonth = 31)
        val occurrences = Amortization.plannedOccurrences(
            rule,
            LocalDate.of(2026, 2, 1),
            LocalDate.of(2026, 4, 30),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)),
            occurrences,
        )
    }

    @Test
    fun yearly_feb_29_falls_back_in_common_years() {
        val rule = plan(
            Frequency.YEARLY,
            dayOfMonth = 29,
            monthOfYear = 2,
            start = LocalDate.of(2023, 1, 1),
        )
        val occurrences = Amortization.plannedOccurrences(
            rule,
            LocalDate.of(2023, 1, 1),
            LocalDate.of(2024, 12, 31),
        )
        assertEquals(listOf(LocalDate.of(2023, 2, 28), LocalDate.of(2024, 2, 29)), occurrences)
    }

    @Test
    fun allocation_period_matches_frequency() {
        val monthly = Amortization.allocationPeriod(Frequency.MONTHLY, LocalDate.of(2026, 2, 15))
        assertEquals(LocalDate.of(2026, 2, 1), monthly.first)
        assertEquals(LocalDate.of(2026, 2, 28), monthly.second)

        val yearly = Amortization.allocationPeriod(Frequency.YEARLY, LocalDate.of(2026, 7, 4))
        assertEquals(LocalDate.of(2026, 1, 1), yearly.first)
        assertEquals(LocalDate.of(2026, 12, 31), yearly.second)

        val daily = Amortization.allocationPeriod(Frequency.DAILY, LocalDate.of(2026, 7, 4))
        assertEquals(LocalDate.of(2026, 7, 4), daily.first)
        assertEquals(LocalDate.of(2026, 7, 4), daily.second)
    }
}
