package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val VoxCastDarkColorScheme = darkColorScheme(
    primary = BroadcastOrange,
    onPrimary = OnBroadcastOrange,
    primaryContainer = BroadcastOrangeContainer,
    onPrimaryContainer = BroadcastOrange,
    secondary = ElectricCyan,
    onSecondary = OnElectricCyan,
    secondaryContainer = ElectricCyanContainer,
    onSecondaryContainer = ElectricCyan,
    tertiary = EmeraldSignal,
    onTertiary = ObsidianBackground,
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldSignal,
    background = ObsidianBackground,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = ConsoleSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBackground,
    surfaceContainerLow = ConsoleSurface,
    surfaceContainer = ConsoleSurfaceElevated,
    surfaceContainerHigh = ConsoleSurfaceHighest,
    outline = ConsoleOutline,
    outlineVariant = ConsoleOutline.copy(alpha = 0.5f),
    error = CrimsonLive,
    onError = TextPrimary
)

val VoxCastShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun VoxCastTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VoxCastDarkColorScheme,
        typography = VoxCastTypography,
        shapes = VoxCastShapes,
        content = content
    )
}
