package com.qsc.accounting.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qsc.accounting.core.Money
import com.qsc.accounting.data.RecurringInput
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.db.RecurringRuleEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RULE_DATE = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)

@Composable
fun RecurringEditorDialog(
    categories: List<CategoryEntity>,
    initial: RecurringRuleEntity?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (RecurringInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: EntryType.EXPENSE) }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: 0L) }
    var amount by remember { mutableStateOf(initial?.let { Money.formatPlain(it.amountCents) } ?: "") }
    var frequency by remember { mutableStateOf(initial?.frequency ?: Frequency.MONTHLY) }
    var dayText by remember { mutableStateOf((initial?.dayOfMonth ?: 1).toString()) }
    var monthText by remember { mutableStateOf((initial?.monthOfYear ?: 1).toString()) }
    var startDate by remember { mutableStateOf(initial?.startDate ?: today.toString()) }
    var endDate by remember { mutableStateOf(initial?.endDate) }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    val ledger = LocalLedgerColors.current
    val context = LocalContext.current
    val options = remember(categories, type, initial?.categoryId) {
        categories.filter { it.type == type && (it.enabled || it.id == initial?.categoryId) }
    }

    LaunchedEffect(options) {
        if (options.none { it.id == categoryId }) {
            categoryId = options.firstOrNull()?.id ?: 0L
        }
    }

    val day = dayText.toIntOrNull()
    val month = monthText.toIntOrNull()
    val cents = runCatching { Money.parseToCents(amount) }.getOrNull()
    val problem = when {
        name.isBlank() -> "给这条规则起个名字"
        categoryId <= 0L -> "选一个分类"
        cents == null -> "金额需大于 0，最多两位小数"
        frequency != Frequency.DAILY && (day == null || day !in 1..31) -> "执行日需在 1～31 之间"
        frequency == Frequency.YEARLY && (month == null || month !in 1..12) -> "执行月需在 1～12 之间"
        else -> null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Column(
                Modifier
                    .heightIn(max = 660.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (initial == null) "新建固定收支" else "编辑固定收支",
                    style = MaterialTheme.typography.titleMedium,
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(80) },
                    label = { Text("名称") },
                    placeholder = { Text("房租 / 工资 / 会员费") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Segmented(
                    options = EntryType.entries,
                    selected = type,
                    label = { if (it == EntryType.INCOME) "收入" else "支出" },
                    onSelect = {
                        type = it
                        categoryId = 0L
                    },
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("分类", style = MaterialTheme.typography.labelLarge, color = ledger.muted)
                    options.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { category ->
                                Surface(
                                    onClick = { categoryId = category.id },
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (category.id == categoryId) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ) {
                                    Row(
                                        Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    parseHexColor(category.color, ledger.accent),
                                                ),
                                        )
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            text = category.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { raw ->
                        val filtered = raw.filter { it.isDigit() || it == '.' }
                        if (filtered.length <= 12 &&
                            filtered.count { it == '.' } <= 1 &&
                            filtered.substringAfter('.', "").length <= 2
                        ) {
                            amount = filtered
                        }
                    },
                    label = { Text("金额") },
                    prefix = { Text("¥") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("周期", style = MaterialTheme.typography.labelLarge, color = ledger.muted)
                    Segmented(
                        options = listOf(Frequency.DAILY, Frequency.MONTHLY, Frequency.YEARLY),
                        selected = frequency,
                        label = {
                            when (it) {
                                Frequency.DAILY -> "每天"
                                Frequency.MONTHLY -> "每月"
                                Frequency.YEARLY -> "每年"
                            }
                        },
                        onSelect = { frequency = it },
                    )
                    when (frequency) {
                        Frequency.DAILY -> Text(
                            text = "生效日期范围内每天都记一笔。",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledger.muted,
                        )

                        Frequency.MONTHLY -> OutlinedTextField(
                            value = dayText,
                            onValueChange = { dayText = it.filter { ch -> ch.isDigit() }.take(2) },
                            label = { Text("每月几号") },
                            supportingText = { Text("没有 31 号的月份会记在月末") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Frequency.YEARLY -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = monthText,
                                onValueChange = { monthText = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("月份") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = dayText,
                                onValueChange = { dayText = it.filter { ch -> ch.isDigit() }.take(2) },
                                label = { Text("日期") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("生效范围", style = MaterialTheme.typography.labelLarge, color = ledger.muted)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        Surface(
                            onClick = {
                                val current = runCatching { LocalDate.parse(startDate) }
                                    .getOrDefault(today)
                                DatePickerDialog(
                                    context,
                                    { _, year, monthValue, dayOfMonth ->
                                        startDate = LocalDate.of(year, monthValue + 1, dayOfMonth)
                                            .toString()
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth,
                                ).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = "开始 " + runCatching { LocalDate.parse(startDate).format(RULE_DATE) }
                                    .getOrDefault(startDate),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        Surface(
                            onClick = {
                                val base = endDate ?: startDate
                                val current = runCatching { LocalDate.parse(base) }
                                    .getOrDefault(today)
                                DatePickerDialog(
                                    context,
                                    { _, year, monthValue, dayOfMonth ->
                                        endDate = LocalDate.of(year, monthValue + 1, dayOfMonth)
                                            .toString()
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth,
                                ).show()
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = endDate?.let { date ->
                                    "结束 " + runCatching { LocalDate.parse(date).format(RULE_DATE) }
                                        .getOrDefault(date)
                                } ?: "结束 · 长期",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        if (endDate != null) {
                            TextButton(onClick = { endDate = null }) { Text("长期") }
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("备注（会写进每一笔生成的记录）") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "暂停后不再生成，恢复从当天开始，不补暂停期的账",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledger.muted,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (problem != null) {
                    Text(
                        text = problem,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    androidx.compose.material3.Button(
                        onClick = {
                            onSave(
                                RecurringInput(
                                    id = initial?.id,
                                    name = name,
                                    type = type,
                                    categoryId = categoryId,
                                    amount = amount,
                                    frequency = frequency,
                                    dayOfMonth = day ?: 1,
                                    monthOfYear = month ?: 1,
                                    startDate = startDate,
                                    endDate = endDate,
                                    enabled = enabled,
                                    note = note,
                                ),
                            )
                        },
                        enabled = problem == null,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
