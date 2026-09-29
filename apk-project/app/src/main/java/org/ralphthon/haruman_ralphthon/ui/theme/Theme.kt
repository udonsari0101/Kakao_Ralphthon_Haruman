package org.ralphthon.haruman_ralphthon.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import org.ralphthon.haruman_ralphthon.ui.AppColors

private val LightColorScheme = lightColorScheme(
    primary = AppColors.Blue,
    onPrimary = AppColors.Surface,
    primaryContainer = AppColors.BlueTint,
    onPrimaryContainer = AppColors.Blue,
    secondary = AppColors.Secondary,
    onSecondary = AppColors.Surface,
    secondaryContainer = AppColors.Soft,
    onSecondaryContainer = AppColors.Text,
    tertiary = AppColors.Green,
    background = AppColors.Background,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Soft,
    onSurfaceVariant = AppColors.Secondary,
    outline = AppColors.Muted,
    outlineVariant = AppColors.Border,
    error = AppColors.Red
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun Haruman_RalphthonTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
