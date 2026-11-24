package com.example.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    background = AbyssalBackground,
    onBackground = DarkOnSurface,
    surface = AbyssalSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = AbyssalSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = AbyssalBackground,
    surfaceContainerLow = AbyssalSurface,
    surfaceContainer = AbyssalSurfaceVariant,
    surfaceContainerHigh = AbyssalSurfaceHighest,
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
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFDCE5F0),
    outline = LightOutline,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

val OctoShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OctoTypography,
        shapes = OctoShapes,
        content = content
    )
}
