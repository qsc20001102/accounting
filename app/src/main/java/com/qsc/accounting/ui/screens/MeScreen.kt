package com.qsc.accounting.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.qsc.accounting.data.model.StatisticsMode
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.components.KeyValueRow
import com.qsc.accounting.ui.components.SectionCard
import com.qsc.accounting.ui.components.Segmented
import com.qsc.accounting.ui.theme.LocalLedgerColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun MeScreen(
    state: AppViewModel.State,
    viewModel: AppViewModel,
    onOpenCategories: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenRecords: () -> Unit,
) {
    val ledger = LocalLedgerColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now().toString()

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setNotificationsEnabled(true)
        } else {
            viewModel.notify("没有通知权限，提醒不会显示")
        }
    }
    val enableNotifications: () -> Unit = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setNotificationsEnabled(true)
        }
    }

    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { viewModel.backupJson() }.getOrElse {
                viewModel.notify("导出失败：${it.message}")
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }.isSuccess
            }
            viewModel.notify(if (ok) "已导出 JSON 备份" else "写入文件失败")
        }
    }

    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = runCatching { viewModel.csvText() }.getOrElse {
                viewModel.notify("导出失败：${it.message}")
                return@launch
            }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入文件")
                }.isSuccess
            }
            viewModel.notify(if (ok) "已导出 CSV" else "写入文件失败")
        }
    }

    val importJson = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text == null) viewModel.notify("读取备份文件失败") else pendingImport = text
        }
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
            text = "我的",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        SectionCard(title = "账本") {
            ActionRow(
                title = "分类管理",
                subtitle = "共 ${state.categories.size} 个分类，可新增、改色、停用",
                onClick = onOpenCategories,
            )
            ActionRow(
                title = "固定收支",
                subtitle = "${state.rules.size} 条规则 · 到点自动记账",
                onClick = onOpenRecurring,
            )
            ActionRow(
                title = "本月明细",
                subtitle = "${state.month.year} 年 ${state.month.monthValue} 月 · ${state.recordTotal} 笔",
                onClick = onOpenRecords,
            )
        }

        SectionCard(title = "数据", subtitle = "所有数据只在本机，建议定期导出一份") {
            ActionRow(
                title = "导出 JSON 备份",
                subtitle = "完整账本：分类、记录、固定规则与均摊快照",
                onClick = { exportJson.launch("accounting-$today.json") },
            )
            ActionRow(
                title = "导出 CSV 明细",
                subtitle = "当前月份的记录，可用表格软件打开",
                onClick = { exportCsv.launch("accounting-$today.csv") },
            )
            ActionRow(
                title = "导入 JSON 备份",
                subtitle = "选择之前导出的备份文件恢复，会替换现有账本",
                onClick = {
                    importJson.launch(arrayOf("application/json", "application/octet-stream", "text/*"))
                },
            )
        }

        SectionCard(title = "统计方式", subtitle = "决定每日收支怎么算") {
            Segmented(
                options = listOf(StatisticsMode.CASH, StatisticsMode.AMORTIZED),
                selected = state.mode,
                label = { it.label },
                onSelect = { viewModel.setMode(it) },
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("均摊时包含未到账计划", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "打开后，发薪日前也能看到每月工资的每日份额",
                        style = MaterialTheme.typography.bodySmall,
                        color = ledger.muted,
                    )
                }
                Switch(
                    checked = state.includePending,
                    onCheckedChange = { viewModel.setIncludePending(it) },
                )
            }
        }

        NotificationSettingsCard(
            state = state,
            viewModel = viewModel,
            onEnableRequested = enableNotifications,
        )

        SectionCard(title = "关于") {
            KeyValueRow("版本", "0.4.3")
            KeyValueRow("币种", "人民币 CNY")
            KeyValueRow("存储", "本机 SQLite，不上传")
            Spacer(Modifier.height(8.dp))
            Text(
                text = "卸载应用会同时删除账本，换机或重置前请先导出备份。统计方式与分类收藏只保存在本机。",
                style = MaterialTheme.typography.bodySmall,
                color = ledger.muted,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    pendingImport?.let { raw ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("导入备份") },
            text = {
                Text(
                    "导入会替换现有的分类、记录和固定规则。建议先导出一份当前账本。确定继续吗？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    viewModel.importBackup(raw)
                }) { Text("确定导入") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    val ledger = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ledger.muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
