package com.qsc.accounting.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qsc.accounting.core.Money
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.StatisticsMode
import com.qsc.accounting.domain.CategoryTotal
import com.qsc.accounting.domain.Period
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.charts.CategoryBar
import com.qsc.accounting.ui.charts.ChartSlice
import com.qsc.accounting.ui.charts.DonutChart
import com.qsc.accounting.ui.charts.TrendLineChart
import com.qsc.accounting.ui.charts.TrendSeries
import com.qsc.accounting.ui.components.EmptyState
import com.qsc.accounting.ui.components.MoneyText
import com.qsc.accounting.ui.components.SectionCard
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun StatsScreen(state: AppViewModel.State, viewModel: AppViewModel) {
    val ledger = LocalLedgerColors.current
    var categoryType by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    var showNet by rememberSaveable { mutableStateOf(true) }
    var showIncome by rememberSaveable { mutableStateOf(false) }
    var showExpense by rememberSaveable { mutableStateOf(false) }
    val stats = state.stats

    val visibleSeries = buildSet {
        if (showNet) add(TrendSeries.NET)
        if (showIncome) add(TrendSeries.INCOME)
        if (showExpense) add(TrendSeries.EXPENSE)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(18.dp))
        Text(
            text = "统计",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.shiftStats(-1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "上一周期",
                    tint = ledger.muted,
                )
            }
            Text(
                text = periodLabel(state.statsPeriod, state.statsDate),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { viewModel.shiftStats(1) }) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "下一周期",
                    tint = ledger.muted,
                )
            }
            IconButton(onClick = { viewModel.resetStatsDate() }) {
                Icon(Icons.Filled.DateRange, contentDescription = "回到今天", tint = ledger.muted)
            }
        }

        Segmented(
            options = listOf(Period.DAY, Period.MONTH, Period.YEAR),
            selected = state.statsPeriod,
            label = {
                when (it) {
                    Period.DAY -> "日"
                    Period.MONTH -> "月"
                    Period.YEAR -> "年"
                    Period.CUSTOM -> "自定义"
                }
            },
            onSelect = { viewModel.setStatsPeriod(it) },
        )

        SectionCard(
            title = "收支总览",
            subtitle = modeLabel(state.mode, state.includePending),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TotalBlock("收入", stats.incomeCents, ledger.income, Modifier.weight(1f))
                TotalBlock("支出", stats.expenseCents, ledger.expense, Modifier.weight(1f))
                TotalBlock(
                    "结余",
                    stats.netCents,
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f),
                )
            }
            if (stats.mode == StatisticsMode.AMORTIZED && stats.includePending &&
                (stats.pendingIncomeCents > 0 || stats.pendingExpenseCents > 0)
            ) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "含未到账固定计划：收入 ${stats.pendingIncome} / 支出 ${stats.pendingExpense}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "共 ${stats.recordCount} 笔 · ${stats.startDate} 至 ${stats.endDate}",
                style = MaterialTheme.typography.bodySmall,
                color = ledger.muted,
            )
        }

        SectionCard(
            title = "趋势",
            subtitle = if (stats.trend.isEmpty()) {
                "所选周期还没有可以统计的日期"
            } else {
                "点曲线标签切换显示，默认只看结余"
            },
        ) {
            if (stats.trend.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = "暂无趋势",
                    hint = "趋势只统计到今天为止的日期。",
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SeriesChip(
                        label = TrendSeries.NET.label,
                        color = ledger.accent,
                        active = showNet,
                        onClick = { showNet = !showNet },
                    )
                    SeriesChip(
                        label = TrendSeries.INCOME.label,
                        color = ledger.income,
                        active = showIncome,
                        onClick = { showIncome = !showIncome },
                    )
                    SeriesChip(
                        label = TrendSeries.EXPENSE.label,
                        color = ledger.expense,
                        active = showExpense,
                        onClick = { showExpense = !showExpense },
                    )
                }
                Spacer(Modifier.height(14.dp))
                if (visibleSeries.isEmpty()) {
                    Text(
                        text = "至少选一条曲线",
                        style = MaterialTheme.typography.bodySmall,
                        color = ledger.muted,
                        modifier = Modifier.padding(vertical = 40.dp),
                    )
                } else {
                    TrendLineChart(
                        points = stats.trend,
                        visible = visibleSeries,
                        seriesColor = { series ->
                            when (series) {
                                TrendSeries.NET -> ledger.accent
                                TrendSeries.INCOME -> ledger.income
                                TrendSeries.EXPENSE -> ledger.expense
                            }
                        },
                        baselineColor = ledger.hairline,
                        labelColor = ledger.muted,
                        labelFormatter = { label -> shortLabel(state.statsPeriod, label) },
                        modifier = Modifier.fillMaxWidth().height(170.dp),
                    )
                }
            }
        }

        SectionCard(title = "分类占比") {
            Segmented(
                options = listOf(EntryType.EXPENSE, EntryType.INCOME),
                selected = categoryType,
                label = { if (it == EntryType.INCOME) "收入" else "支出" },
                onSelect = { categoryType = it },
            )
            Spacer(Modifier.height(16.dp))

            val items = stats.categories[categoryType].orEmpty()
            val total = items.sumOf { it.amountCents }

            if (items.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.DateRange,
                    title = "这一类还没有数据",
                    hint = "换个周期，或者先记几笔。",
                )
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    DonutChart(
                        slices = items.map { item ->
                            ChartSlice(
                                label = item.name,
                                value = item.amountCents,
                                color = parseHexColor(item.color, ledger.chart.first()),
                            )
                        },
                        modifier = Modifier.size(170.dp),
                        stroke = 18.dp,
                        trackColor = ledger.hairline,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (categoryType == EntryType.INCOME) "收入" else "支出",
                                style = MaterialTheme.typography.labelMedium,
                                color = ledger.muted,
                            )
                            Spacer(Modifier.height(2.dp))
                            MoneyText(cents = total, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                items.forEach { item ->
                    CategoryLine(item = item, total = total)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SeriesChip(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit,
) {
    val ledger = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) color.copy(alpha = 0.14f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (active) color.copy(alpha = 0.55f) else ledger.hairline,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (active) color else ledger.muted.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (active) MaterialTheme.colorScheme.onSurface else ledger.muted,
            )
        }
    }
}

@Composable
private fun TotalBlock(
    label: String,
    cents: Long,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = LocalLedgerColors.current.muted,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = Money.format(cents),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CategoryLine(item: CategoryTotal, total: Long) {
    val ledger = LocalLedgerColors.current
    val fraction = if (total > 0) item.amountCents.toFloat() / total else 0f
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(item.color, ledger.accent)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = ledger.muted,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = Money.format(item.amountCents),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        CategoryBar(
            fraction = fraction,
            color = parseHexColor(item.color, ledger.accent),
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
        )
    }
}

private fun modeLabel(mode: StatisticsMode, includePending: Boolean): String = buildString {
    append("统计方式：")
    append(mode.label)
    if (mode == StatisticsMode.AMORTIZED && includePending) {
        append(" · 含未到账计划")
    }
}

private fun periodLabel(period: Period, date: String): String {
    val fallback = date
    return when (period) {
        Period.DAY -> runCatching { LocalDate.parse(date) }
            .map { "%d年%d月%d日".format(it.year, it.monthValue, it.dayOfMonth) }
            .getOrDefault(fallback)

        Period.MONTH -> runCatching { YearMonth.parse(date) }
            .map { "%d年%d月".format(it.year, it.monthValue) }
            .getOrDefault(fallback)

        Period.YEAR -> runCatching { date.toInt() }
            .map { "%d年".format(it) }
            .getOrDefault(fallback)

        Period.CUSTOM -> date
    }
}

private fun shortLabel(period: Period, label: String): String = when (period) {
    Period.YEAR -> label.takeLast(2)
    else -> label.takeLast(2)
}
