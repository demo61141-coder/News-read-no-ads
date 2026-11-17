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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = InkNavy,
    onPrimary = Color.White,
    primaryContainer = PaperSurface,
    onPrimaryContainer = InkNavy,
    secondary = TerracottaAccent,
    onSecondary = Color.White,
    secondaryContainer = TerracottaSoft,
    onSecondaryContainer = Color(0xFF5A1D0C),
    tertiary = SageBadge,
    onTertiary = Color.White,
    tertiaryContainer = SageSoft,
    onTertiaryContainer = Color(0xFF10382D),
    background = PaperCream,
    onBackground = CharcoalText,
    surface = PaperCream,
    onSurface = CharcoalText,
    surfaceVariant = PaperSurface,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLow = CrispWhite,
    surfaceContainer = PaperSurface,
    outline = HairlineDivider,
    outlineVariant = HairlineDivider
)

private val DarkColorScheme = darkColorScheme(
    primary = NightAccent,
    onPrimary = Color.White,
    primaryContainer = NightSurfaceVariant,
    onPrimaryContainer = NightInk,
    secondary = NightSage,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF19362E),
    onSecondaryContainer = Color(0xFFB5E6D7),
    tertiary = NightAccent,
    onTertiary = Color.White,
    background = NightBg,
    onBackground = NightInk,
    surface = NightBg,
    onSurface = NightInk,
    surfaceVariant = NightSurface,
    onSurfaceVariant = NightMuted,
    surfaceContainerLow = NightSurface,
    surfaceContainer = NightSurfaceVariant,
    outline = Color(0xFF2B3B4D),
    outlineVariant = Color(0xFF243242)
)

val CleanReadShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp)
)

@Composable
fun CleanReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CleanReadTypography,
        shapes = CleanReadShapes,
        content = content
    )
}
