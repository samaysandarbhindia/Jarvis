package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class JarvisAccentTheme(val id: String, val label: String, val primary: Color, val secondary: Color) {
    ELECTRIC_CYAN("ELECTRIC_CYAN", "Arc Cyan", ElectricCyan, ArcCobalt),
    STARK_COBALT("STARK_COBALT", "Cobalt Core", ArcCobaltGlow, ElectricCyan),
    MARK_GOLD("MARK_GOLD", "Mark Gold", StarkAmber, ElectricCyan);

    companion object {
        fun fromId(id: String): JarvisAccentTheme =
            entries.firstOrNull { it.id == id } ?: ELECTRIC_CYAN
    }
}

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisTheme(
    accentTheme: JarvisAccentTheme = JarvisAccentTheme.ELECTRIC_CYAN,
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = accentTheme.primary,
        onPrimary = JarvisDeepBlack,
        primaryContainer = JarvisPanelElevated,
        onPrimaryContainer = HudTextPrimary,
        secondary = accentTheme.secondary,
        onSecondary = JarvisDeepBlack,
        secondaryContainer = JarvisPanelNavy,
        onSecondaryContainer = HudTextPrimary,
        tertiary = StarkAmber,
        onTertiary = JarvisDeepBlack,
        background = JarvisDeepBlack,
        onBackground = HudTextPrimary,
        surface = JarvisMidnightNavy,
        onSurface = HudTextPrimary,
        surfaceVariant = JarvisPanelNavy,
        onSurfaceVariant = HudTextSecondary,
        surfaceContainerLowest = JarvisDeepBlack,
        surfaceContainerLow = JarvisMidnightNavy,
        surfaceContainer = JarvisPanelNavy,
        surfaceContainerHigh = JarvisPanelElevated,
        surfaceContainerHighest = Color(0xFF192B4D),
        outline = accentTheme.primary.copy(alpha = 0.35f),
        outlineVariant = Color(0xFF1D3254),
        error = CrimsonAlert,
        onError = HudTextPrimary
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
