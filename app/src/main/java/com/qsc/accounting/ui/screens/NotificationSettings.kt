package com.qsc.accounting.ui.screens

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qsc.accounting.notify.NotificationScheduler
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.components.SectionCard
import com.qsc.accounting.ui.theme.LocalLedgerColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun NotificationSettingsCard(
    state: AppViewModel.State,
    viewModel: AppViewModel,
    onEnableRequested: () -> Unit,
) {
    val ledger = LocalLedgerColors.current
    val context = LocalContext.current

    // 从系统「精确闹钟」设置页返回时要重新读一次。
    var exactAllowed by remember { mutableStateOf(hasExactAlarmAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                exactAllowed = hasExactAlarmAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SectionCard(title = "通知", subtitle = "每天或每月把收支小结推到通知栏") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("开启通知", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "关闭后所有提醒都不发",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
            Switch(
                checked = state.notifyEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) onEnableRequested() else viewModel.setNotificationsEnabled(false)
                },
            )
        }

        if (!state.notifyEnabled) return@SectionCard

        if (!exactAllowed) {
            Spacer(Modifier.height(12.dp))
            ExactAlarmNotice(
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${context.packageName}"),
                    )
                    runCatching { context.startActivity(intent) }
                },
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = ledger.hairline)
        Spacer(Modifier.height(14.dp))

        SettingRow(
            title = "每日提醒",
            subtitle = "每天 ${formatTime(state.dailyHour, state.dailyMinute)}",
            checked = state.dailyEnabled,
            onCheckedChange = viewModel::setDailyEnabled,
        )
        if (state.dailyEnabled) {
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TimePill(state.dailyHour, state.dailyMinute) { hour, minute ->
                    viewModel.setDailyTime(hour, minute)
                }
                Text(
                    text = "下次 " + nextDailyLabel(state.dailyHour, state.dailyMinute),
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        SettingRow(
            title = "每月提醒",
            subtitle = "每月 ${state.monthlyDay} 号 ${formatTime(state.monthlyHour, state.monthlyMinute)}",
            checked = state.monthlyEnabled,
            onCheckedChange = viewModel::setMonthlyEnabled,
        )
        if (state.monthlyEnabled) {
            Spacer(Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                DayStepper(state.monthlyDay, viewModel::setMonthlyDay)
                TimePill(state.monthlyHour, state.monthlyMinute) { hour, minute ->
                    viewModel.setMonthlyTime(hour, minute)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "下次 " + nextMonthlyLabel(
                    state.monthlyDay,
                    state.monthlyHour,
                    state.monthlyMinute,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = ledger.muted,
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = ledger.hairline)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.sendTestNotification() }) {
                Text("发一条测试通知")
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                runCatching { context.startActivity(intent) }
            }) {
                Text("通知设置")
            }
        }

        Text(
            text = "提醒在设定时间前后几分钟内送达；系统省电策略可能进一步延后。",
            style = MaterialTheme.typography.bodySmall,
            color = ledger.muted,
        )
    }
}

@Composable
private fun ExactAlarmNotice(onOpenSettings: () -> Unit) {
    val ledger = LocalLedgerColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "提醒可能不准时",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "系统还没允许「闹钟和提醒」，到点后可能晚几分钟甚至更久。",
                    style = MaterialTheme.typography.bodySmall,
                    color = ledger.muted,
                )
            }
            TextButton(onClick = onOpenSettings) { Text("去允许") }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val ledger = LocalLedgerColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ledger.muted,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun TimePill(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
    val ledger = LocalLedgerColors.current
    val context = LocalContext.current
    Surface(
        onClick = {
            TimePickerDialog(
                context,
                { _, pickedHour, pickedMinute -> onPicked(pickedHour, pickedMinute) },
                hour,
                minute,
                true,
            ).show()
        },
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledger.hairline),
    ) {
        Text(
            text = formatTime(hour, minute),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DayStepper(day: Int, onChange: (Int) -> Unit) {
    val ledger = LocalLedgerColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "每月",
            style = MaterialTheme.typography.bodySmall,
            color = ledger.muted,
        )
        Spacer(Modifier.width(8.dp))
        StepButton(label = "−", enabled = day > 1) { onChange(day - 1) }
        Text(
            text = "$day",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.width(30.dp),
            textAlign = TextAlign.Center,
        )
        StepButton(label = "+", enabled = day < 31) { onChange(day + 1) }
        Spacer(Modifier.width(4.dp))
        Text(
            text = "号",
            style = MaterialTheme.typography.bodySmall,
            color = ledger.muted,
        )
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val ledger = LocalLedgerColors.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, ledger.hairline),
        modifier = Modifier.size(32.dp),
    ) {
        Column(
            modifier = Modifier.size(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else ledger.muted.copy(alpha = 0.5f),
            )
        }
    }
}

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun hasExactAlarmAccess(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    val manager = context.getSystemService(AlarmManager::class.java) ?: return false
    return manager.canScheduleExactAlarms()
}

private fun nextDailyLabel(hour: Int, minute: Int): String =
    relativeLabel(millisToLocal(NotificationScheduler.nextDaily(LocalDateTime.now(), hour, minute)))

private fun nextMonthlyLabel(day: Int, hour: Int, minute: Int): String =
    relativeLabel(
        millisToLocal(NotificationScheduler.nextMonthly(LocalDateTime.now(), day, hour, minute)),
    )

private fun millisToLocal(millis: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

private fun relativeLabel(target: LocalDateTime): String {
    val today = LocalDate.now()
    val day = when (target.toLocalDate()) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        else -> "%d月%d日".format(target.monthValue, target.dayOfMonth)
    }
    return "%s %02d:%02d".format(day, target.hour, target.minute)
}
