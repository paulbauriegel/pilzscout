package de.pilzscout.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Forest palette: cream paper surfaces, deep forest green primary, warm brown secondary,
 * amber tertiary for warnings. Used by default; Android dynamic color is an opt-in setting.
 */
val ForestLightColorScheme = lightColorScheme(
    primary = Color(0xFF2F5D3A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E6D0),
    onPrimaryContainer = Color(0xFF10301A),
    secondary = Color(0xFF5C4A3A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DCCB),
    onSecondaryContainer = Color(0xFF2B1F15),
    tertiary = Color(0xFF8A5A14),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBE6C2),
    onTertiaryContainer = Color(0xFF3F2A00),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    background = Color(0xFFF6F2E8),
    onBackground = Color(0xFF1F1D18),
    surface = Color(0xFFF6F2E8),
    onSurface = Color(0xFF1F1D18),
    surfaceVariant = Color(0xFFE2DBCC),
    onSurfaceVariant = Color(0xFF4B473F),
    outline = Color(0xFF7C776C),
    outlineVariant = Color(0xFFCDC6B8),
    inverseSurface = Color(0xFF34312B),
    inverseOnSurface = Color(0xFFF6F2E8),
    inversePrimary = Color(0xFF9CCB9C),
    surfaceDim = Color(0xFFD9D3C6),
    surfaceBright = Color(0xFFFCF9F1),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF8F0),
    surfaceContainer = Color(0xFFEFEADF),
    surfaceContainerHigh = Color(0xFFE7E1D3),
    surfaceContainerHighest = Color(0xFFDFD8C8),
)

val ForestDarkColorScheme = darkColorScheme(
    primary = Color(0xFF9CCB9C),
    onPrimary = Color(0xFF0E2F18),
    primaryContainer = Color(0xFF244A2E),
    onPrimaryContainer = Color(0xFFD7E6D0),
    secondary = Color(0xFFD5C2AE),
    onSecondary = Color(0xFF2B1F15),
    secondaryContainer = Color(0xFF4A3A2C),
    onSecondaryContainer = Color(0xFFE8DCCB),
    tertiary = Color(0xFFF0C27A),
    onTertiary = Color(0xFF3F2A00),
    tertiaryContainer = Color(0xFF5C3F0A),
    onTertiaryContainer = Color(0xFFFBE6C2),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF17140F),
    onBackground = Color(0xFFE9E3D6),
    surface = Color(0xFF17140F),
    onSurface = Color(0xFFE9E3D6),
    surfaceVariant = Color(0xFF4B473F),
    onSurfaceVariant = Color(0xFFCDC6B8),
    outline = Color(0xFF979183),
    outlineVariant = Color(0xFF4B473F),
    inverseSurface = Color(0xFFE9E3D6),
    inverseOnSurface = Color(0xFF34312B),
    inversePrimary = Color(0xFF2F5D3A),
    surfaceDim = Color(0xFF17140F),
    surfaceBright = Color(0xFF3D3931),
    surfaceContainerLowest = Color(0xFF110F0B),
    surfaceContainerLow = Color(0xFF1F1C16),
    surfaceContainer = Color(0xFF24201A),
    surfaceContainerHigh = Color(0xFF2E2A23),
    surfaceContainerHighest = Color(0xFF39352D),
)

/** Extra brand colours outside the M3 scheme: the floating navigation bar and the forest backdrop. */
object ForestColors {
    val navBarLight = Color(0xFF3B2E24)
    val navBarDark = Color(0xFF241C16)
    val navOnBar = Color(0xFFF2E9DA)
    val navSelected = Color(0xFFDCE8D4)
    val navOnSelected = Color(0xFF1E4A2A)
    // The forest backdrop palette lives in tools/src/mushroom_packs/backdrop.py (rendered to drawable-nodpi/forest_*.webp).
}
