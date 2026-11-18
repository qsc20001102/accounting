package com.qsc.accounting.domain

import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.core.Money

data class CategoryTotal(
    val categoryId: Long,
    val name: String,
    val color: String,
    val amountCents: Long,
) {
    val amount: String get() = Money.format(amountCents)
}

data class TrendPoint(
    val label: String,
    val incomeCents: Long,
    val expenseCents: Long,
) {
    val netCents: Long get() = incomeCents - expenseCents
}

/** 与插件 statistics_service.summary() 返回结构一致，UI 只消费这个对象。 */
data class Summary(
    val period: Period,
    val mode: com.qsc.accounting.data.model.StatisticsMode,
    val includePending: Boolean,
    val startDate: String,
    val endDate: String,
    val incomeCents: Long,
    val expenseCents: Long,
    val pendingIncomeCents: Long,
    val pendingExpenseCents: Long,
    val categories: Map<EntryType, List<CategoryTotal>>,
    val trend: List<TrendPoint>,
    val recordCount: Int,
) {
    val netCents: Long get() = incomeCents - expenseCents
    val income: String get() = Money.format(incomeCents)
    val expense: String get() = Money.format(expenseCents)
    val net: String get() = Money.format(netCents)
    val pendingIncome: String get() = Money.format(pendingIncomeCents)
    val pendingExpense: String get() = Money.format(pendingExpenseCents)
    val isEmpty: Boolean get() = incomeCents == 0L && expenseCents == 0L

    companion object {
        fun empty(period: Period = Period.MONTH): Summary = Summary(
            period = period,
            mode = com.qsc.accounting.data.model.StatisticsMode.CASH,
            includePending = false,
            startDate = "",
            endDate = "",
            incomeCents = 0,
            expenseCents = 0,
            pendingIncomeCents = 0,
            pendingExpenseCents = 0,
            categories = emptyMap(),
            trend = emptyList(),
            recordCount = 0,
        )
    }
}

/** 统计周期；与插件 data-range 的 day/month/year/custom 对应。 */
enum class Period(val wire: String) {
    DAY("day"),
    MONTH("month"),
    YEAR("year"),
    CUSTOM("custom");

    companion object {
        fun fromWire(value: String?): Period = entries.firstOrNull { it.wire == value } ?: MONTH
    }
}

/** 一笔参与统计的收支；periodStart/periodEnd 只有固定收支的均摊记录才有。 */
data class StatsEntry(
    val type: EntryType,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val amountCents: Long,
    val date: java.time.LocalDate,
    val periodStart: java.time.LocalDate? = null,
    val periodEnd: java.time.LocalDate? = null,
)

/** 固定收支规则的有效版本；用于「含未到账固定计划」的均摊补足。 */
data class PlannedRule(
    val ruleId: Long,
    val type: EntryType,
    val categoryId: Long,
    val categoryName: String,
    val categoryColor: String,
    val amountCents: Long,
    val frequency: com.qsc.accounting.data.model.Frequency,
    val startDate: java.time.LocalDate,
    val endDate: java.time.LocalDate?,
    val dayOfMonth: Int,
    val monthOfYear: Int,
    val validFrom: java.time.LocalDate,
    val validUntil: java.time.LocalDate?,
    val enabled: Boolean,
)
