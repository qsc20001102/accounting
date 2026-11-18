package com.qsc.accounting.ui.screens

import android.app.DatePickerDialog
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
import com.qsc.accounting.data.EntryInput
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val EDITOR_DATE = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

@Composable
fun RecordEditorDialog(
    categories: List<CategoryEntity>,
    initial: TransactionEntity?,
    today: LocalDate,
    onDismiss: () -> Unit,
    onSave: (EntryInput) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var type by remember { mutableStateOf(initial?.type ?: EntryType.EXPENSE) }
    var amount by remember {
        mutableStateOf(initial?.let { Money.formatPlain(it.amountCents) } ?: "")
    }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var categoryId by remember { mutableStateOf(initial?.categoryId ?: 0L) }
    var date by remember { mutableStateOf(initial?.transactionDate ?: today.toString()) }

    val ledger = LocalLedgerColors.current
    val context = LocalContext.current
    val options = remember(categories, type) {
        categories.filter {
            it.type == type && (it.enabled || it.id == initial?.categoryId)
        }
    }

    LaunchedEffect(options) {
        if (options.none { it.id == categoryId }) {
            categoryId = options.firstOrNull()?.id ?: 0L
        }
    }

    val cents = runCatching { Money.parseToCents(amount) }.getOrNull()
    val canSave = cents != null && categoryId > 0L

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
                    .heightIn(max = 620.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (initial == null) "新增记录" else "编辑记录",
                    style = MaterialTheme.typography.titleMedium,
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
                    isError = amount.isNotEmpty() && cents == null,
                    supportingText = if (amount.isNotEmpty() && cents == null) {
                        { Text("金额需大于 0，最多两位小数") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
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
                                                .background(parseHexColor(category.color, ledger.accent)),
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        onClick = {
                            val current = runCatching { LocalDate.parse(date) }.getOrDefault(today)
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
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = runCatching { LocalDate.parse(date).format(EDITOR_DATE) }
                                .getOrDefault(date),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Surface(
                        onClick = { date = today.toString() },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, ledger.hairline),
                    ) {
                        Text(
                            text = "今天",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = ledger.muted,
                        )
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(500) },
                    label = { Text("备注") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                                EntryInput(
                                    id = initial?.id,
                                    type = type,
                                    categoryId = categoryId,
                                    amount = amount,
                                    date = date,
                                    note = note,
                                ),
                            )
                        },
                        enabled = canSave,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
