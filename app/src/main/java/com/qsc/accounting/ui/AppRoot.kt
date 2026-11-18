package com.qsc.accounting.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import com.qsc.accounting.ui.screens.CategoriesScreen
import com.qsc.accounting.ui.screens.MeScreen
import com.qsc.accounting.ui.screens.RecordScreen
import com.qsc.accounting.ui.screens.RecordsScreen
import com.qsc.accounting.ui.screens.RecurringScreen
import com.qsc.accounting.ui.screens.StatsScreen
import com.qsc.accounting.ui.theme.LocalLedgerColors

private enum class Tab(val label: String, val icon: ImageVector) {
    RECORD("记一笔", Icons.Filled.Add),
    RECORDS("明细", Icons.AutoMirrored.Filled.List),
    STATS("统计", Icons.Filled.DateRange),
    ME("我的", Icons.Filled.Person),
}

private enum class Route { NONE, CATEGORIES, RECURRING }

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val state by viewModel.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.RECORD) }
    var route by remember { mutableStateOf(Route.NONE) }
    val snackbar = remember { SnackbarHostState() }

    var permissionPrompt by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.markNotificationPromptShown()
        if (granted) viewModel.setNotificationsEnabled(true)
    }

    LaunchedEffect(Unit) {
        // 首次打开问一次；授权过或点过「暂不」就不再打扰。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            viewModel.shouldPromptNotificationPermission()
        ) {
            permissionPrompt = true
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(enabled = route != Route.NONE) { route = Route.NONE }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                val ledger = LocalLedgerColors.current
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = ledger.muted,
                            unselectedTextColor = ledger.muted,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                Tab.RECORD -> RecordScreen(state, viewModel)
                Tab.RECORDS -> RecordsScreen(state, viewModel)
                Tab.STATS -> StatsScreen(state, viewModel)
                Tab.ME -> MeScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenCategories = { route = Route.CATEGORIES },
                    onOpenRecurring = { route = Route.RECURRING },
                    onOpenRecords = { tab = Tab.RECORDS },
                )
            }
        }
    }

    if (route == Route.CATEGORIES) {
        CategoriesScreen(state = state, viewModel = viewModel, onBack = { route = Route.NONE })
    }

    if (route == Route.RECURRING) {
        RecurringScreen(state = state, viewModel = viewModel, onBack = { route = Route.NONE })
    }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("知道了") }
            },
            title = { Text("操作失败") },
            text = { Text(message) },
        )
    }

    if (permissionPrompt) {
        AlertDialog(
            onDismissRequest = {
                permissionPrompt = false
                viewModel.markNotificationPromptShown()
            },
            title = { Text("开启收支提醒？") },
            text = {
                Text(
                    "可以选择每天或每月在固定时间，把当时的收入、支出、结余推到通知栏。"
                        + "需要通知权限；之后也能在「我的 → 通知」里随时开关和改时间。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionPrompt = false
                    viewModel.markNotificationPromptShown()
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text("允许通知") }
            },
            dismissButton = {
                TextButton(onClick = {
                    permissionPrompt = false
                    viewModel.markNotificationPromptShown()
                }) { Text("暂不") }
            },
        )
    }
}
