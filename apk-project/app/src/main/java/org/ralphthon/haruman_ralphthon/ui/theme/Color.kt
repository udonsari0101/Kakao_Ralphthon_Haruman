package org.ralphthon.haruman_ralphthon.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** App tokens are local to the composition so changing theme preserves screen state. */
@Immutable
data class AppPalette(
    val background: Color,
    val surface: Color,
    val soft: Color,
    val border: Color,
    val brandText: Color,
    val brandSoft: Color,
    val text: Color,
    val secondary: Color,
    val muted: Color,
    val green: Color,
    val greenTint: Color,
    val error: Color,
    val dark: Boolean
)

val LightAppPalette = AppPalette(
    background = Color(0xFFFAF9F9), surface = Color.White,
    soft = Color(0xFFF2EFEF), border = Color(0xFFE7E2E2),
    brandText = Color(0xFFB80010), brandSoft = Color(0xFFFFF4F3),
    text = Color(0xFF202124), secondary = Color(0xFF5F6368), muted = Color(0xFF686B70),
    green = Color(0xFF167447), greenTint = Color(0xFFEAF7EF), error = Color(0xFFB80010),
    dark = false
)

val DarkAppPalette = AppPalette(
    background = Color(0xFF121214), surface = Color(0xFF1B1B1F),
    soft = Color(0xFF27272D), border = Color(0xFF39393F),
    brandText = Color(0xFFFF6B73), brandSoft = Color(0xFF341B20),
    text = Color(0xFFF5F5F7), secondary = Color(0xFFC6C6CC), muted = Color(0xFFA8A8B2),
    green = Color(0xFF83D5A7), greenTint = Color(0xFF173526), error = Color(0xFFFF8A90),
    dark = true
)

val LocalAppPalette = staticCompositionLocalOf { LightAppPalette }

object AppColors {
    val Primary = Color(0xFFE60012)
    val OnPrimary = Color.White
    val Pressed = Color(0xFFB80010)
    val Accent = Color(0xFFFF303B)
    val Background: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.surface
    val Soft: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.soft
    val Border: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.border
    /** Retained names for existing screens; these now use the accessible brand red. */
    val Blue: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.brandText
    val BlueTint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.brandSoft
    val Text: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.text
    val Secondary: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.secondary
    val Muted: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.muted
    val Green: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.green
    val GreenTint: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.greenTint
    val Red: Color @Composable @ReadOnlyComposable get() = LocalAppPalette.current.error
}
