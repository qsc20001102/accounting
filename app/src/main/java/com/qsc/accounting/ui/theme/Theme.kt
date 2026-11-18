package com.qsc.accounting.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0F4C46),
    onPrimary = Color(0xFFF8F6F0),
    primaryContainer = Color(0xFFD8E9E3),
    onPrimaryContainer = Color(0xFF082F2B),
    secondary = Color(0xFF6C5C41),
    onSecondary = Color(0xFFFBF7EE),
    secondaryContainer = Color(0xFFEFE6D4),
    onSecondaryContainer = Color(0xFF3A2F1D),
    background = Color(0xFFF6F4EF),
    onBackground = Color(0xFF1B1A17),
    surface = Color(0xFFFFFDF8),
    onSurface = Color(0xFF1B1A17),
    surfaceVariant = Color(0xFFECE7DB),
    onSurfaceVariant = Color(0xFF6A665C),
    outline = Color(0xFFCFC8B7),
    outlineVariant = Color(0xFFE3DDCF),
    error = Color(0xFFB4412B),
    onError = Color(0xFFFFF8F5),
    errorContainer = Color(0xFFF6DCD4),
    onErrorContainer = Color(0xFF5B1809),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7FC0B1),
    onPrimary = Color(0xFF04231F),
    primaryContainer = Color(0xFF123B36),
    onPrimaryContainer = Color(0xFFCFE9E2),
    secondary = Color(0xFFCBB794),
    onSecondary = Color(0xFF2A2116),
    secondaryContainer = Color(0xFF3A3023),
    onSecondaryContainer = Color(0xFFEFE3CC),
    background = Color(0xFF14130F),
    onBackground = Color(0xFFEDE9DF),
    surface = Color(0xFF1C1A15),
    onSurface = Color(0xFFEDE9DF),
    surfaceVariant = Color(0xFF2A2721),
    onSurfaceVariant = Color(0xFFA7A096),
    outline = Color(0xFF4A463D),
    outlineVariant = Color(0xFF322E27),
    error = Color(0xFFE79680),
    onError = Color(0xFF3A0F05),
    errorContainer = Color(0xFF4E1D10),
    onErrorContainer = Color(0xFFF7D8CF),
)

@Immutable
data class LedgerColors(
    val income: Color,
    val expense: Color,
    val accent: Color,
    val muted: Color,
    val hairline: Color,
    val paper: Color,
    val star: Color,
    val chart: List<Color>,
)

private val LightLedger = LedgerColors(
    income = Color(0xFF2F7A5B),
    expense = Color(0xFFB54B33),
    accent = Color(0xFF0F4C46),
    muted = Color(0xFF8A857A),
    hairline = Color(0xFFE2DCCD),
    paper = Color(0xFFF6F4EF),
    star = Color(0xFFBE8A2E),
    chart = listOf(
        Color(0xFF0F4C46), Color(0xFFB54B33), Color(0xFFC99A4B), Color(0xFF5B6FA8),
        Color(0xFF7E6A9B), Color(0xFF4E8C7D), Color(0xFFA8674B), Color(0xFF8A8577),
    ),
)

private val DarkLedger = LedgerColors(
    income = Color(0xFF83C4A2),
    expense = Color(0xFFE0937C),
    accent = Color(0xFF7FC0B1),
    muted = Color(0xFF9A9389),
    hairline = Color(0xFF302C25),
    paper = Color(0xFF14130F),
    star = Color(0xFFD9B36A),
    chart = listOf(
        Color(0xFF7FC0B1), Color(0xFFE0937C), Color(0xFFD8B26A), Color(0xFF8E9FD8),
        Color(0xFFB29AC9), Color(0xFF7FBCAA), Color(0xFFD29174), Color(0xFFA8A294),
    ),
)

val LocalLedgerColors = staticCompositionLocalOf { LightLedger }

@Composable
fun AccountingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLedgerColors provides if (darkTheme) DarkLedger else LightLedger) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = Typography(),
            content = content,
        )
    }
}
