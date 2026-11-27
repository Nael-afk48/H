package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val HavenLightColorScheme = lightColorScheme(
    primary = ObsidianPrimary,
    onPrimary = IvoryOnPrimary,
    primaryContainer = PlatinumContainer,
    onPrimaryContainer = CharcoalOnContainer,
    secondary = ChampagneBronzeSecondary,
    onSecondary = Color.White,
    secondaryContainer = PaleChampagneContainer,
    onSecondaryContainer = DeepBronzeOnContainer,
    tertiary = PewterMutedText,
    onTertiary = Color.White,
    background = AlabasterBackground,
    onBackground = ObsidianInkText,
    surface = AlabasterBackground,
    onSurface = ObsidianInkText,
    surfaceVariant = LimestoneSurface,
    onSurfaceVariant = PewterMutedText,
    surfaceContainerLowest = GalleryWhiteCard,
    surfaceContainerLow = LimestoneSurface,
    surfaceContainer = LimestoneSurface,
    surfaceContainerHigh = AshStoneSurfaceHigh,
    outline = TravertineOutline,
    outlineVariant = SoftLimestoneOutlineVariant,
    error = LuxuryCrimsonError,
    onError = Color.White,
    errorContainer = LuxuryCrimsonContainer,
    onErrorContainer = Color(0xFF410E0B)
)

private val HavenDarkColorScheme = darkColorScheme(
    primary = ChampagneIvoryPrimaryDark,
    onPrimary = ObsidianOnPrimaryDark,
    primaryContainer = SmokedBronzeContainerDark,
    onPrimaryContainer = IvoryTextContainerDark,
    secondary = BurnishedGoldSecondaryDark,
    onSecondary = DeepGoldOnSecondaryDark,
    secondaryContainer = DarkGoldContainer,
    onSecondaryContainer = WarmGoldTextDark,
    tertiary = TitaniumMutedTextDark,
    onTertiary = Color(0xFF141416),
    background = PitchObsidianBackground,
    onBackground = AlabasterTextDark,
    surface = PitchObsidianBackground,
    onSurface = AlabasterTextDark,
    surfaceVariant = BasaltSurface,
    onSurfaceVariant = TitaniumMutedTextDark,
    surfaceContainerLowest = SmokedOnyxCard,
    surfaceContainerLow = BasaltSurface,
    surfaceContainer = BasaltSurface,
    surfaceContainerHigh = GunmetalSurfaceHigh,
    outline = GraphiteOutlineDark,
    outlineVariant = SubtleBasaltOutlineVariant,
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

val HavenShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun HavenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) HavenDarkColorScheme else HavenLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HavenTypography,
        shapes = HavenShapes,
        content = content
    )
}
