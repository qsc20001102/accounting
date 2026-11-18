package com.qsc.accounting.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qsc.accounting.data.CategoryInput
import com.qsc.accounting.data.db.CategoryEntity
import com.qsc.accounting.data.model.EntryType
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.components.parseHexColor
import com.qsc.accounting.ui.theme.LocalLedgerColors

private val PALETTE = listOf(
    "#169b7b", "#5264df", "#e99651", "#8c6dd8",
    "#d87580", "#7890a0", "#b58b3a", "#4f8a5b",
)

@Composable
fun CategoriesScreen(state: AppViewModel.State, viewModel: AppViewModel, onBack: () -> Unit) {
    val ledger = LocalLedgerColors.current
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Text(
                    text = "分类管理",
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
            ) {
                item(key = "hint") {
                    Text(
                        text = "点右侧星标收藏分类：记一笔页面会先显示收藏的，其余收在「更多分类」里。",
                        style = MaterialTheme.typography.bodySmall,
                        color = ledger.muted,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                listOf(EntryType.EXPENSE, EntryType.INCOME).forEach { type ->
                    val rows = state.categories.filter { it.type == type }
                    item(key = "title-$type") {
                        Text(
                            text = if (type == EntryType.INCOME) "收入分类" else "支出分类",
                            style = MaterialTheme.typography.labelLarge,
                            color = ledger.muted,
                            modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
                        )
                    }
                    items(rows, key = { it.id }) { category ->
                        CategoryRow(
                            category = category,
                            favorited = category.id in state.favorites,
                            onClick = { editing = category },
                            onToggleFavorite = { viewModel.toggleCategoryFavorite(category.id) },
                        )
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        CategoryDialog(
            initial = editing,
            onDismiss = {
                creating = false
                editing = null
            },
            onSave = { input ->
                viewModel.saveCategory(input)
                creating = false
                editing = null
            },
            onDelete = editing?.let { category ->
                {
                    viewModel.deleteCategory(category.id)
                    creating = false
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun CategoryRow(
    category: CategoryEntity,
    favorited: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val ledger = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledger.hairline),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(parseHexColor(category.color, ledger.accent)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!category.enabled) {
                Text(
                    text = "已停用",
                    style = MaterialTheme.typography.labelSmall,
                    color = ledger.muted,
                )
                Spacer(Modifier.width(6.dp))
            }
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleFavorite),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (favorited) "★" else "☆",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (favorited) ledger.star else ledger.muted.copy(alpha = 0.55f),
                )
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    initial: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (CategoryInput) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var type by remember { mutableStateOf(initial?.type ?: EntryType.EXPENSE) }
    var color by remember { mutableStateOf(initial?.color ?: PALETTE.first()) }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }
    val ledger = LocalLedgerColors.current

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
                Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (initial == null) "新建分类" else "编辑分类",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                Segmented(
                    options = EntryType.entries,
                    selected = type,
                    label = { if (it == EntryType.INCOME) "收入" else "支出" },
                    onSelect = { type = it },
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("分类名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("颜色", style = MaterialTheme.typography.labelLarge, color = ledger.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PALETTE.forEach { hex ->
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(hex, ledger.accent))
                                    .clickable { color = hex },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (color == hex) {
                                    Box(
                                        Modifier
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface),
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("启用", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "停用后不再出现在记账页，历史记录不受影响",
                            style = MaterialTheme.typography.bodySmall,
                            color = ledger.muted,
                        )
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        onClick = {
                            onSave(
                                CategoryInput(
                                    id = initial?.id,
                                    name = name,
                                    type = type,
                                    color = color,
                                    sortOrder = initial?.sortOrder ?: 0,
                                    enabled = enabled,
                                ),
                            )
                        },
                        enabled = name.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("保存")
                    }
                }
            }
        }
    }
}
