package com.qsc.accounting.domain

import com.qsc.accounting.core.Dates
import com.qsc.accounting.core.fail
import com.qsc.accounting.data.model.EntryType
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 前端传来的统计查询参数；字段与插件的一致。 */
data class StatsQuery(
    val period: Period = Period.MONTH,
    /** day: yyyy-MM-dd；month: yyyy-MM；year: yyyy；custom 时忽略。 */
    val date: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val type: EntryType? = null,
    val categoryId: Long? = null,
    val search: String? = null,
)

/**
 * 统计窗口，移植自 utils/date_utils.date_range() 与 statistics_service 的 trend 裁剪逻辑。
 * 汇总范围与趋势范围是两件事：趋势永远截止到今天，汇总范围可以选历史或未来。
 */
data class StatsWindow(
    val period: Period,
    val start: LocalDate,
    val end: LocalDate,
    val trendStart: LocalDate,
    val trendEnd: LocalDate,
    val labels: List<String>,
    val loadStart: LocalDate,
) {
    companion object {
        fun of(query: StatsQuery, current: LocalDate): StatsWindow {
            val (start, end) = resolveRange(query, current)
            val trendStart =
                if (query.period == Period.DAY) maxOf(Dates.MIN, start.minusDays(6)) else start
            val trendEnd = minOf(end, current)
            val labels = if (trendStart.isAfter(trendEnd)) {
                emptyList()
            } else {
                buildLabels(query.period, start, trendStart, trendEnd)
            }
            val loadStart = if (labels.isEmpty()) start else minOf(start, trendStart)
            return StatsWindow(query.period, start, end, trendStart, trendEnd, labels, loadStart)
        }

        private fun resolveRange(query: StatsQuery, current: LocalDate): Pair<LocalDate, LocalDate> {
            val value = query.date?.takeIf { it.isNotBlank() } ?: current.toString()
            return when (query.period) {
                Period.DAY -> {
                    val day = Dates.parse(value)
                    day to day
                }

                Period.MONTH -> {
                    val normalized = if (value.length == 7) "$value-01" else value
                    val chosen = Dates.parse(normalized)
                    chosen.withDayOfMonth(1) to chosen.withDayOfMonth(chosen.lengthOfMonth())
                }

                Period.YEAR -> {
                    val normalized = if (value.length == 4) "$value-01-01" else value
                    val chosen = Dates.parse(normalized)
                    LocalDate.of(chosen.year, 1, 1) to LocalDate.of(chosen.year, 12, 31)
                }

                Period.CUSTOM -> {
                    val start = Dates.parse(query.startDate, "开始日期")
                    val end = Dates.parse(query.endDate, "结束日期")
                    start to end
                }
            }.also { (start, end) ->
                if (start.isAfter(end)) fail("开始日期不能晚于结束日期")
                if (ChronoUnit.DAYS.between(start, end) > 3660) fail("日期范围不能超过 10 年")
            }
        }

        private fun buildLabels(
            period: Period,
            start: LocalDate,
            trendStart: LocalDate,
            trendEnd: LocalDate,
        ): List<String> = if (period == Period.YEAR) {
            (1..trendEnd.monthValue).map { month -> "%04d-%02d".format(start.year, month) }
        } else {
            val days = ChronoUnit.DAYS.between(trendStart, trendEnd).toInt()
            (0..days).map { trendStart.plusDays(it.toLong()).toString() }
        }
    }
}
