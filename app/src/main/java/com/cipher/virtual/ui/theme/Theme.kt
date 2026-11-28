package com.cipher.virtual.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Immutable
data class NeuPalette(
    val base: Color,
    val raisedSurface: Color,
    val pressedSurface: Color,
    val highlightShadow: Color,
    val darkShadow: Color,
    val bevelHighlight: Color,
    val bevelShadow: Color,
    val isDark: Boolean
)

val DarkNeuPalette = NeuPalette(
    base = NeuDarkBase,
    raisedSurface = NeuDarkSurface,
    pressedSurface = NeuDarkWell,
    highlightShadow = NeuDarkHighlight,
    darkShadow = NeuDarkShadow,
    bevelHighlight = NeuDarkBevelLight,
    bevelShadow = NeuDarkBevelDark,
    isDark = true
)

val LightNeuPalette = NeuPalette(
    base = NeuLightBase,
    raisedSurface = NeuLightSurface,
    pressedSurface = NeuLightWell,
    highlightShadow = NeuLightHighlight,
    darkShadow = NeuLightShadow,
    bevelHighlight = NeuLightBevelLight,
    bevelShadow = NeuLightBevelDark,
    isDark = false
)

val LocalNeuPalette = staticCompositionLocalOf { DarkNeuPalette }

private val OctoDarkColorScheme = darkColorScheme(
    primary = CyanPrimary,
    onPrimary = OnCyanPrimary,
    primaryContainer = CyanContainer,
    onPrimaryContainer = OnCyanContainer,
    secondary = CoralSecondary,
    onSecondary = OnCoralSecondary,
    secondaryContainer = CoralContainer,
    onSecondaryContainer = OnCoralContainer,
    tertiary = MintTertiary,
    onTertiary = OnMintTertiary,
    tertiaryContainer = MintContainer,
    onTertiaryContainer = OnMintContainer,
    background = NeuDarkBase,
    onBackground = DarkOnSurface,
    surface = NeuDarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = NeuDarkWell,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = NeuDarkWell,
    surfaceContainerLow = NeuDarkBase,
    surfaceContainer = NeuDarkSurface,
    surfaceContainerHigh = NeuDarkSurfaceHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = ErrorRed,
    onError = Color(0xFF450A0A),
    errorContainer = ErrorContainer,
    onErrorContainer = Color(0xFFFECACA),
)

private val OctoLightColorScheme = lightColorScheme(
    primary = LightCyanPrimary,
    onPrimary = Color.White,
    primaryContainer = LightCyanContainer,
    onPrimaryContainer = Color(0xFF083344),
    secondary = LightCoralSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightCoralContainer,
    onSecondaryContainer = Color(0xFF4C0519),
    tertiary = LightMintTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightMintContainer,
    onTertiaryContainer = Color(0xFF022C22),
    background = NeuLightBase,
    onBackground = LightOnSurface,
    surface = NeuLightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = NeuLightWell,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = NeuLightWell,
    surfaceContainerLow = NeuLightBase,
    surfaceContainer = NeuLightSurface,
    surfaceContainerHigh = NeuLightSurfaceHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val OctoShapes = Shapes(
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

data class SpaceAccentColors(
    val accent: Color,
    val container: Color,
    val onContainer: Color,
    val label: String
)

@Composable
fun spaceAccentColors(accentIndex: Int): SpaceAccentColors {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (((accentIndex % 5) + 5) % 5) {
        1 -> if (isDark) {
            SpaceAccentColors(CoralSecondary, CoralContainer, OnCoralContainer, "Coral")
        } else {
            SpaceAccentColors(LightCoralSecondary, LightCoralContainer, Color(0xFF4C0519), "Coral")
        }
        2 -> if (isDark) {
            SpaceAccentColors(MintTertiary, MintContainer, OnMintContainer, "Mint")
        } else {
            SpaceAccentColors(LightMintTertiary, LightMintContainer, Color(0xFF022C22), "Mint")
        }
        3 -> if (isDark) {
            SpaceAccentColors(AmberAccent, AmberContainerDark, OnAmberContainerDark, "Amber")
        } else {
            SpaceAccentColors(LightAmberAccent, LightAmberContainer, Color(0xFF451A03), "Amber")
        }
        4 -> if (isDark) {
            SpaceAccentColors(VioletAccent, VioletContainerDark, OnVioletContainerDark, "Violet")
        } else {
            SpaceAccentColors(LightVioletAccent, LightVioletContainer, Color(0xFF2E1065), "Violet")
        }
        else -> if (isDark) {
            SpaceAccentColors(CyanPrimary, CyanContainer, OnCyanContainer, "Cyan")
        } else {
            SpaceAccentColors(LightCyanPrimary, LightCyanContainer, Color(0xFF083344), "Cyan")
        }
    }
}

@Composable
fun OctoSpaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> OctoDarkColorScheme
        else -> OctoLightColorScheme
    }
    val neuPalette = if (darkTheme) DarkNeuPalette else LightNeuPalette

    CompositionLocalProvider(LocalNeuPalette provides neuPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = OctoTypography,
            shapes = OctoShapes,
            content = content
        )
    }
}
