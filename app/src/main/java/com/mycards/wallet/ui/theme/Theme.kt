package com.mycards.wallet.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Палитра в духе OriginOS: светло-серый «воздушный» фон, белые скруглённые блоки,
 * фирменный синий акцент vivo, крупные жирные заголовки.
 * Шрифт берётся системный — на телефоне vivo это шрифт самой OriginOS.
 */
@Immutable
data class OriginPalette(
    val background: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val text: Color,
    val textSecondary: Color,
    val divider: Color,
    val accent: Color,
    val accentSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val danger: Color,
    val isDark: Boolean,
)

val LightOrigin = OriginPalette(
    background = Color(0xFFF2F3F7),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF6F7FA),
    text = Color(0xFF15161A),
    textSecondary = Color(0xFF8B8E98),
    divider = Color(0xFFE8E9EE),
    accent = Color(0xFF3E6BFF),
    accentSoft = Color(0x1A3E6BFF),
    success = Color(0xFF13A863),
    successSoft = Color(0x1A13A863),
    warning = Color(0xFFE08A00),
    warningSoft = Color(0x1AE08A00),
    danger = Color(0xFFF2453D),
    isDark = false,
)

val DarkOrigin = OriginPalette(
    background = Color(0xFF0B0C10),
    surface = Color(0xFF1B1C22),
    surfaceHigh = Color(0xFF25262D),
    text = Color(0xFFF2F3F7),
    textSecondary = Color(0xFF8F929C),
    divider = Color(0xFF2C2D34),
    accent = Color(0xFF5B82FF),
    accentSoft = Color(0x2E5B82FF),
    success = Color(0xFF2FCB7E),
    successSoft = Color(0x262FCB7E),
    warning = Color(0xFFFFB13B),
    warningSoft = Color(0x26FFB13B),
    danger = Color(0xFFFF5A52),
    isDark = true,
)

val LocalOrigin = staticCompositionLocalOf { LightOrigin }

@Composable
fun OriginTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkOrigin else LightOrigin
    val scheme = if (dark) {
        darkColorScheme(
            primary = p.accent, onPrimary = Color.White,
            background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text,
            surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textSecondary,
            surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surfaceHigh,
            outline = p.divider, outlineVariant = p.divider, error = p.danger,
        )
    } else {
        lightColorScheme(
            primary = p.accent, onPrimary = Color.White,
            background = p.background, onBackground = p.text,
            surface = p.surface, onSurface = p.text,
            surfaceVariant = p.surfaceHigh, onSurfaceVariant = p.textSecondary,
            surfaceContainerLowest = p.surface, surfaceContainerLow = p.surface,
            surfaceContainer = p.surface, surfaceContainerHigh = p.surface,
            surfaceContainerHighest = p.surfaceHigh,
            outline = p.divider, outlineVariant = p.divider, error = p.danger,
        )
    }
    CompositionLocalProvider(LocalOrigin provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
