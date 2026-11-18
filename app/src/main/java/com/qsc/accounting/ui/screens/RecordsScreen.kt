package com.qsc.accounting.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qsc.accounting.core.Money
import com.qsc.accounting.data.db.TransactionEntity
import com.qsc.accounting.data.model.EntrySource
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.components.EmptyState
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_LABEL = DateTimeFormatter.ofPattern("M月d日 EEE", Locale.CHINA)

private sealed interface FilterChoice {
    data object All : FilterChoice
    data object Income : FilterChoice
    data object Expense : FilterChoice
}

@Composable
fun RecordsScreen(state: AppViewModel.State, viewModel: AppViewModel) {
    val ledger = LocalLedgerColors.current
    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var searchDraft by remember { mutableStateOf(state.search) }

    val groups = remember(state.records) {
        state.records
            .groupBy { it.transactionDate }
            .toList()
            .sortedByDescending { it.first }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "明细",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.shiftMonth(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月", tint = ledger.muted)
                }
                Text(
                    text = "%d年%d月".format(state.month.year, state.month.monthValue),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { viewModel.resetMonth() },
                )
                IconButton(onClick = { viewModel.shiftMonth(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月", tint = ledger.muted)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${state.recordTotal} 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
            Spacer(Modifier.height(12.dp))
            Segmented(
                options = listOf(FilterChoice.All, FilterChoice.Expense, FilterChoice.Income),
                selected = when (state.filterType) {
                    null -> FilterChoice.All
                    EntryType.EXPENSE -> FilterChoice.Expense
                    EntryType.INCOME -> FilterChoice.Income
                },
                label = {
                    when (it) {
                        FilterChoice.All -> "全部"
                        FilterChoice.Income -> "收入"
                        FilterChoice.Expense -> "支出"
                    }
                },
                onSelect = { choice ->
                    viewModel.setFilterType(
                        when (choice) {
                            FilterChoice.All -> null
                            FilterChoice.Income -> EntryType.INCOME
                            FilterChoice.Expense -> EntryType.EXPENSE
                        },
                    )
                },
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = searchDraft,
                onValueChange = { searchDraft = it.take(60) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜索备注") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.applySearch(searchDraft) }),
                shape = RoundedCornerShape(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            val income = state.records.filter { it.type == EntryType.INCOME }.sumOf { it.amountCents }
            val expense = state.records.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountCents }
            Text(
                text = "本月收入 ${Money.format(income)} · 支出 ${Money.format(expense)}",
                style = MaterialTheme.typography.bodySmall,
                color = ledger.muted,
            )
        }

        if (groups.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = "这个月还没有记录",
                hint = "切到「记一笔」记下第一笔，或者换个月份看看。",
                modifier = Modifier.padding(top = 40.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            ) {
                groups.forEach { (date, entries) ->
                    item(key = "header-$date") {
                        DayHeader(date = date, entries = entries)
                    }
                    items(entries, key = { it.id }) { entry ->
                        RecordRow(entry = entry, onClick = { editing = entry })
                    }
                }
            }
        }
    }

    editing?.let { entry ->
        RecordEditorDialog(
            categories = state.categories,
            initial = entry,
            today = state.today,
            onDismiss = { editing = null },
            onSave = { input ->
                viewModel.saveEntry(input)
                editing = null
            },
            onDelete = {
                viewModel.deleteEntry(entry.id)
                editing = null
            },
        )
    }
}

@Composable
private fun DayHeader(date: String, entries: List<TransactionEntity>) {
    val ledger = LocalLedgerColors.current
    val parsed = runCatching { LocalDate.parse(date) }.getOrNull()
    val expense = entries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amountCents }
    val income = entries.filter { it.type == EntryType.INCOME }.sumOf { it.amountCents }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = parsed?.format(DAY_LABEL) ?: date,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = buildString {
                if (expense > 0) append("支 ${Money.format(expense)}")
                if (expense > 0 && income > 0) append(" · ")
                if (income > 0) append("收 ${Money.format(income)}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = ledger.muted,
        )
    }
}

@Composable
private fun RecordRow(entry: TransactionEntity, onClick: () -> Unit) {
    val ledger = LocalLedgerColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(parseHexColor(entry.categoryColor, ledger.accent)),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.categoryName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                if (entry.source == EntrySource.RECURRING) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "固定",
                        style = MaterialTheme.typography.labelSmall,
                        color = ledger.muted,
                    )
                }
            }
            if (entry.note.isNotBlank()) {
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = (if (entry.type == EntryType.INCOME) "+" else "-") + Money.format(entry.amountCents),
            style = MaterialTheme.typography.titleSmall,
            color = if (entry.type == EntryType.INCOME) ledger.income else ledger.expense,
        )
    }
}
