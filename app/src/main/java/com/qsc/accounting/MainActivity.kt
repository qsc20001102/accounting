package com.qsc.accounting

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qsc.accounting.ui.AppRoot
import com.qsc.accounting.ui.AppViewModel
import com.qsc.accounting.ui.theme.AccountingTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as AccountingApp).container

        setContent {
            AccountingTheme {
                val viewModel: AppViewModel = viewModel(
                    factory = AppViewModel.factory(
                        repository = container.repository,
                        recurring = container.recurring,
                        prefs = container.prefs,
                        onLedgerChanged = container::refreshWidget,
                        onNotificationSettingsChanged = container::rescheduleNotifications,
                        isNotificationPermissionGranted = container::isNotificationPermissionGranted,
                        onSendTestNotification = container::sendTestNotification,
                    ),
                )
                AppRoot(viewModel)
            }
        }
    }
}
