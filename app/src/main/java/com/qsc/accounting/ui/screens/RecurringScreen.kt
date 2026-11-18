package com.qsc.accounting.ui.screens

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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qsc.accounting.core.Money
import com.qsc.accounting.data.RuleView
import com.qsc.accounting.data.db.RecurringRuleEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.data.model.Frequency
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.components.EmptyState
import com.qsc.accounting.ui.components.SectionCard
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors

@Composable
fun RecurringScreen(state: AppViewModel.State, viewModel: AppViewModel, onBack: () -> Unit) {
    val ledger = LocalLedgerColors.current
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<RecurringRuleEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<RecurringRuleEntity?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "固定收支",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { creating = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新建")
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    SectionCard(
                        title = "到点自动记一笔",
                        subtitle = "工资、房租、订阅费这类每周每月都来的收支",
                    ) {
                        Text(
                            text = "打开 App、以及每 6 小时后台检查一次；错过的时间会按原计划日期补记。" +
                                "暂停期间不记账，恢复后也不补暂停期。",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledger.muted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.runRecurringNow() }) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("立即检查")
                            }
                        }
                    }
                }

                if (state.rules.isEmpty()) {
                    item(key = "empty") {
                        EmptyState(
                            icon = Icons.Filled.Refresh,
                            title = "还没有固定收支",
                            hint = "点右上角「新建」，把每个月的房租工资交给它。",
                        )
                        HorizontalDivider(color = ledger.hairline)
                    }
                }

                items(state.rules, key = { it.rule.id }) { view ->
                    RuleCard(
                        view = view,
                        onEdit = { editing = view.rule },
                        onToggle = { enabled -> viewModel.setRecurringEnabled(view.rule.id, enabled) },
                    )
                }
            }
        }
    }

    if (creating || editing != null) {
        RecurringEditorDialog(
            categories = state.categories,
            initial = editing,
            today = state.today,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { input ->
                viewModel.saveRecurring(input) {
                    creating = false
                    editing = null
                }
            },
            onDelete = editing?.let { rule ->
                {
                    pendingDelete = rule
                    creating = false
                    editing = null
                }
            },
        )
    }

    pendingDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${rule.name}」") },
            text = { Text("删除后不再生成新记录，已经记下的历史保留。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRecurring(rule.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun RuleCard(
    view: RuleView,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val ledger = LocalLedgerColors.current
    val rule = view.rule
    val nextLabel = when {
        rule.deleted -> "已删除"
        !rule.enabled -> "已暂停"
        !view.categoryEnabled -> "分类已停用"
        view.nextExecution != null -> "下次 ${view.nextExecution}"
        else -> "已结束"
    }

    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledger.hairline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = scheduleLabel(rule),
                        style = MaterialTheme.typography.labelSmall,
                        color = ledger.muted,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(parseHexColor(view.categoryColor, ledger.accent)),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = view.categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = ledger.muted,
                    )
                    Text(
                        text = " · $nextLabel",
                        style = MaterialTheme.typography.bodySmall,
                        color = ledger.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = (if (rule.type == EntryType.INCOME) "+" else "-") +
                        Money.format(rule.amountCents),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (rule.type == EntryType.INCOME) ledger.income else ledger.expense,
                )
                Switch(checked = rule.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

private fun scheduleLabel(rule: RecurringRuleEntity): String = when (rule.frequency) {
    Frequency.DAILY -> "每天"
    Frequency.MONTHLY -> "每月 ${rule.dayOfMonth ?: 1} 号"
    Frequency.YEARLY -> "每年 ${rule.monthOfYear ?: 1} 月 ${rule.dayOfMonth ?: 1} 日"
}
