package com.qsc.accounting.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qsc.accounting.core.Money
import com.qsc.accounting.data.EntryInput
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.components.MoneyLarge
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_LABEL = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

@Composable
fun RecordScreen(state: AppViewModel.State, viewModel: AppViewModel) {
    var type by rememberSaveable { mutableStateOf(EntryType.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf(0L) }
    var date by rememberSaveable { mutableStateOf(state.today.toString()) }
    var showMore by rememberSaveable { mutableStateOf(false) }

    val ledger = LocalLedgerColors.current
    val categories = remember(state.categories, type) {
        state.categories.filter { it.type == type && it.enabled }
    }

    LaunchedEffect(categories, state.favorites, showMore) {
        // 默认选中收藏里的第一个；收起时如果当前选中的是被收起的分类，也把选中项挪回收藏。
        val favored = categories.filter { it.id in state.favorites }
        val visibleOptions = if (favored.isEmpty() || showMore) categories else favored
        if (visibleOptions.none { it.id == categoryId }) {
            categoryId = visibleOptions.firstOrNull()?.id ?: 0L
        }
    }

    val cents = remember(amount) { runCatching { Money.parseToCents(amount) }.getOrNull() }
    val canSave = cents != null && categoryId > 0L
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "记一笔",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = state.today.format(DATE_LABEL) + " · 本月结余 " + Money.format(state.monthSummary.netCents),
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MiniStat("今日支出", state.daySummary.expenseCents, ledger.expense, Modifier.weight(1f))
            MiniStat("今日收入", state.daySummary.incomeCents, ledger.income, Modifier.weight(1f))
            MiniStat(
                label = "今日结余",
                cents = state.daySummary.netCents,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        Segmented(
            options = EntryType.entries,
            selected = type,
            label = { if (it == EntryType.INCOME) "收入" else "支出" },
            onSelect = {
                type = it
                categoryId = 0L
                showMore = false
            },
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ledger.hairline),
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "¥",
                        style = MoneyLarge.copy(fontSize = 24.sp),
                        color = ledger.muted,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        BasicTextField(
                            value = amount,
                            onValueChange = { raw ->
                                val filtered = raw.filter { ch -> ch.isDigit() || ch == '.' }
                                val decimals = filtered.substringAfter('.', "")
                                if (filtered.length <= 12 &&
                                    filtered.count { it == '.' } <= 1 &&
                                    decimals.length <= 2
                                ) {
                                    amount = filtered
                                }
                            },
                            singleLine = true,
                            textStyle = MoneyLarge.copy(
                                fontSize = 32.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "0.00",
                                        style = MoneyLarge.copy(fontSize = 32.sp),
                                        color = ledger.muted.copy(alpha = 0.5f),
                                    )
                                }
                                inner()
                            },
                        )
                    }
                    if (amount.isNotEmpty()) {
                        IconButton(onClick = { amount = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "清空金额",
                                tint = ledger.muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                if (amount.isNotEmpty() && cents == null) {
                    Text(
                        text = "金额需大于 0，最多两位小数",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "分类",
                style = MaterialTheme.typography.labelLarge,
                color = ledger.muted,
            )
            if (categories.isEmpty()) {
                Text(
                    text = "这个方向还没有启用中的分类，先去「我的 → 分类管理」建一个。",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            } else {
                val favored = categories.filter { it.id in state.favorites }
                val rest = categories.filter { it.id !in state.favorites }
                if (favored.isEmpty()) {
                    // 还没收藏过：全部平铺，避免用户以为分类丢了。
                    CategoryGrid(categories, categoryId) { categoryId = it }
                } else {
                    CategoryGrid(favored, categoryId) { categoryId = it }
                    if (rest.isNotEmpty()) {
                        TextButton(
                            onClick = { showMore = !showMore },
                            contentPadding = PaddingValues(0.dp),
                        ) {
                            Text(
                                text = if (showMore) {
                                    "收起"
                                } else {
                                    "更多分类（${rest.size}）"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (showMore) {
                            CategoryGrid(rest, categoryId) { categoryId = it }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("日期", style = MaterialTheme.typography.labelLarge, color = ledger.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "今天" to state.today,
                    "昨天" to state.today.minusDays(1),
                    "前天" to state.today.minusDays(2),
                ).forEach { (label, value) ->
                    DatePill(
                        label = label,
                        selected = date == value.toString(),
                        onClick = { date = value.toString() },
                    )
                }
                DatePill(
                    label = runCatching { LocalDate.parse(date).format(DATE_LABEL) }
                        .getOrDefault("选择日期"),
                    selected = date != state.today.toString() &&
                        date != state.today.minusDays(1).toString() &&
                        date != state.today.minusDays(2).toString(),
                    onClick = {
                        val current = runCatching { LocalDate.parse(date) }.getOrDefault(state.today)
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                date = LocalDate.of(year, month + 1, day).toString()
                            },
                            current.year,
                            current.monthValue - 1,
                            current.dayOfMonth,
                        ).show()
                    },
                )
            }
        }

        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("备注（可选）") },
            shape = RoundedCornerShape(14.dp),
        )

        Button(
            onClick = {
                viewModel.saveEntry(
                    EntryInput(
                        type = type,
                        categoryId = categoryId,
                        amount = amount,
                        date = date,
                        note = note,
                    ),
                ) {
                    amount = ""
                    note = ""
                }
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("记下这笔", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MiniStat(
    label: String,
    cents: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalLedgerColors.current.hairline),
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = LocalLedgerColors.current.muted,
                maxLines = 1,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = Money.format(cents),
                style = MaterialTheme.typography.titleSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    items: List<CategoryEntity>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = category.id == selectedId,
                        onClick = { onSelect(category.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryChip(
    category: CategoryEntity,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ledger = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else ledger.hairline,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(category.color, ledger.accent)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DatePill(label: String, selected: Boolean, onClick: () -> Unit) {
    val ledger = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else ledger.hairline,
        ),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else ledger.muted,
            maxLines = 1,
        )
    }
}
