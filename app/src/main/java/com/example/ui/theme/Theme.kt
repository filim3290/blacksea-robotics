package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class BlackseaExtendedColors(
    val isDark: Boolean,
    val background: Color,
    val surfaceElevated: Color,
    val cardBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentBlue: Color,
    val accentBlueSolid: Color,
    val accentBlueContainer: Color,
    val adminRedText: Color,
    val adminRedSolid: Color,
    val emeraldText: Color,
    val emeraldSolid: Color,
    val amberText: Color,
    val dangerText: Color,
    val dangerSolid: Color,
    val dangerContainer: Color,
    val authGradientColors: List<Color>,
    val bannerGradientColors: List<Color>,
    val bannerTitleColor: Color,
    val bannerSubtitleColor: Color,
    val bannerMottoColor: Color,
    val codeBackground: Color,
    val codeHeaderBackground: Color,
    val codeTextColor: Color,
    val codeLineNumberColor: Color
)

private val LightExtendedColors = BlackseaExtendedColors(
    isDark = false,
    background = LightBackground,
    surfaceElevated = Color(0xFFF8FAFD),
    cardBorder = LightBorder,
    textPrimary = TextPrimary,
    textSecondary = TextSecondary,
    textMuted = TextMuted,
    accentBlue = LightAccentBlue,
    accentBlueSolid = ElectricBlue,
    accentBlueContainer = LightAccentBlueContainer,
    adminRedText = LightAdminRed,
    adminRedSolid = LightAdminRed,
    emeraldText = LightEmeraldText,
    emeraldSolid = Color(0xFF059669),
    amberText = LightAmberText,
    dangerText = LightDangerText,
    dangerSolid = Color(0xFFDC2626),
    dangerContainer = LightDangerContainer,
    authGradientColors = listOf(Color(0xFFDCEBFF), Color(0xFFF1F5F9), Color(0xFFE2ECF9)),
    bannerGradientColors = listOf(Color(0xFF0F2B5C), Color(0xFF1E3A8A), Color(0xFF0369A1)),
    bannerTitleColor = Color.White,
    bannerSubtitleColor = Color(0xFFE2E8F0),
    bannerMottoColor = Color(0xFFFDE68A),
    codeBackground = Color(0xFFF8FAFC),
    codeHeaderBackground = Color(0xFFE2E8F0),
    codeTextColor = Color(0xFF0F172A),
    codeLineNumberColor = Color(0xFF64748B)
)

private val DarkExtendedColors = BlackseaExtendedColors(
    isDark = true,
    background = DarkNavyBackground,
    surfaceElevated = Color(0xFF16223A),
    cardBorder = DarkBorder,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textMuted = DarkTextMuted,
    accentBlue = DarkSecondary,
    accentBlueSolid = ElectricBlue,
    accentBlueContainer = DarkAccentBlueContainer,
    adminRedText = DarkAdminRed,
    adminRedSolid = Color(0xFFE11D48),
    emeraldText = DarkEmeraldText,
    emeraldSolid = Color(0xFF10B981),
    amberText = DarkAmberText,
    dangerText = DarkDangerText,
    dangerSolid = Color(0xFFEF4444),
    dangerContainer = DarkDangerContainer,
    authGradientColors = listOf(Color(0xFF0B1222), Color(0xFF131F36), Color(0xFF070C17)),
    bannerGradientColors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF1E3A8A)),
    bannerTitleColor = Color.White,
    bannerSubtitleColor = Color(0xFFCBD5E1),
    bannerMottoColor = PhoenixAmber,
    codeBackground = Color(0xFF0B1120),
    codeHeaderBackground = Color(0xFF1E293B),
    codeTextColor = Color(0xFF7DD3FC),
    codeLineNumberColor = Color(0xFF64748B)
)

val LocalBlackseaColors = staticCompositionLocalOf { LightExtendedColors }

val MaterialTheme.blackseaColors: BlackseaExtendedColors
    @Composable
    get() = LocalBlackseaColors.current

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF002F6C),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = DarkSecondary,
    onSecondary = Color(0xFF003549),
    secondaryContainer = DarkAccentBlueContainer,
    onSecondaryContainer = Color(0xFFE0F2FE),
    tertiary = PhoenixAmber,
    onTertiary = Color(0xFF452B00),
    background = DarkNavyBackground,
    onBackground = DarkTextPrimary,
    surface = DarkNavySurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkNavySurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = ElectricBlue,
    onSecondary = Color.White,
    secondaryContainer = LightAccentBlueContainer,
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = PhoenixAmber,
    onTertiary = Color.Black,
    background = LightBackground,
    onBackground = TextPrimary,
    surface = LightSurface,
    onSurface = TextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = LightBorder,
    error = LightDangerText,
    onError = Color.White
)

@Composable
fun BlackseaRoboticsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    CompositionLocalProvider(LocalBlackseaColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
