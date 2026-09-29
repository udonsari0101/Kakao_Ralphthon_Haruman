package org.ralphthon.haruman_ralphthon.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import org.ralphthon.haruman_ralphthon.ui.AppColors
import org.ralphthon.haruman_ralphthon.ui.DarkAppPalette
import org.ralphthon.haruman_ralphthon.ui.LightAppPalette
import org.ralphthon.haruman_ralphthon.ui.LocalAppPalette

private val LightColorScheme = LightAppPalette.let { palette ->
    lightColorScheme(
        primary = AppColors.Primary, onPrimary = Color.White,
        primaryContainer = palette.brandSoft, onPrimaryContainer = palette.brandText,
        secondary = palette.secondary, onSecondary = Color.White,
        secondaryContainer = palette.soft, onSecondaryContainer = palette.text,
        tertiary = palette.green, onTertiary = Color.White,
        tertiaryContainer = palette.greenTint, onTertiaryContainer = palette.green,
        background = palette.background, onBackground = palette.text,
        surface = palette.surface, onSurface = palette.text,
        surfaceVariant = palette.soft, onSurfaceVariant = palette.secondary,
        surfaceTint = Color.Transparent,
        surfaceContainer = palette.surface, surfaceContainerLow = palette.background,
        surfaceContainerHigh = palette.soft, surfaceContainerHighest = palette.soft,
        surfaceContainerLowest = palette.surface,
        outline = palette.muted, outlineVariant = palette.border,
        error = palette.error, onError = Color.White,
        errorContainer = palette.brandSoft, onErrorContainer = palette.error,
        inverseSurface = DarkAppPalette.surface, inverseOnSurface = DarkAppPalette.text,
        inversePrimary = DarkAppPalette.brandText
    )
}

private val DarkColorScheme = DarkAppPalette.let { palette ->
    darkColorScheme(
        primary = palette.brandText, onPrimary = Color(0xFF4B0006),
        primaryContainer = palette.brandSoft, onPrimaryContainer = palette.brandText,
        secondary = palette.secondary, onSecondary = palette.background,
        secondaryContainer = palette.soft, onSecondaryContainer = palette.text,
        tertiary = palette.green, onTertiary = palette.background,
        tertiaryContainer = palette.greenTint, onTertiaryContainer = palette.green,
        background = palette.background, onBackground = palette.text,
        surface = palette.surface, onSurface = palette.text,
        surfaceVariant = palette.soft, onSurfaceVariant = palette.secondary,
        surfaceTint = Color.Transparent,
        surfaceContainer = palette.surface, surfaceContainerLow = palette.background,
        surfaceContainerHigh = palette.soft, surfaceContainerHighest = palette.soft,
        surfaceContainerLowest = palette.background,
        outline = palette.muted, outlineVariant = palette.border,
        error = palette.error, onError = Color(0xFF4B0006),
        errorContainer = palette.brandSoft, onErrorContainer = palette.error,
        inverseSurface = LightAppPalette.surface, inverseOnSurface = LightAppPalette.text,
        inversePrimary = LightAppPalette.brandText
    )
}

@Suppress("UNUSED_PARAMETER")
@Composable
fun Haruman_RalphthonTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppPalette provides if (darkTheme) DarkAppPalette else LightAppPalette) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content
        )
    }
}
