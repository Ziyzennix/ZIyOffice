package org.ziyoffice.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = ZiyPrimaryLight,
    onPrimary = ZiyOnPrimaryLight,
    primaryContainer = ZiyPrimaryContainerLight,
    onPrimaryContainer = ZiyOnPrimaryContainerLight,
    secondary = ZiySecondaryLight,
    onSecondary = ZiyOnSecondaryLight,
    secondaryContainer = ZiySecondaryContainerLight,
    onSecondaryContainer = ZiyOnSecondaryContainerLight,
    tertiary = ZiyTertiaryLight,
    onTertiary = ZiyOnTertiaryLight,
    tertiaryContainer = ZiyTertiaryContainerLight,
    onTertiaryContainer = ZiyOnTertiaryContainerLight,
    background = ZiyBackgroundLight,
    onBackground = ZiyOnBackgroundLight,
    surface = ZiySurfaceLight,
    onSurface = ZiyOnSurfaceLight,
    surfaceVariant = ZiySurfaceVariantLight,
    onSurfaceVariant = ZiyOnSurfaceVariantLight,
    outline = ZiyOutlineLight,
    error = ZiyErrorLight,
    onError = ZiyOnErrorLight,
    surfaceContainerLowest = ZiySurfaceContainerLowestLight,
    surfaceContainerLow = ZiySurfaceContainerLowLight,
    surfaceContainer = ZiySurfaceContainerLight,
    surfaceContainerHigh = ZiySurfaceContainerHighLight,
    surfaceContainerHighest = ZiySurfaceContainerHighestLight,
)

private val DarkColors = darkColorScheme(
    primary = ZiyPrimaryDark,
    onPrimary = ZiyOnPrimaryDark,
    primaryContainer = ZiyPrimaryContainerDark,
    onPrimaryContainer = ZiyOnPrimaryContainerDark,
    secondary = ZiySecondaryDark,
    onSecondary = ZiyOnSecondaryDark,
    secondaryContainer = ZiySecondaryContainerDark,
    onSecondaryContainer = ZiyOnSecondaryContainerDark,
    tertiary = ZiyTertiaryDark,
    onTertiary = ZiyOnTertiaryDark,
    tertiaryContainer = ZiyTertiaryContainerDark,
    onTertiaryContainer = ZiyOnTertiaryContainerDark,
    background = ZiyBackgroundDark,
    onBackground = ZiyOnBackgroundDark,
    surface = ZiySurfaceDark,
    onSurface = ZiyOnSurfaceDark,
    surfaceVariant = ZiySurfaceVariantDark,
    onSurfaceVariant = ZiyOnSurfaceVariantDark,
    outline = ZiyOutlineDark,
    error = ZiyErrorDark,
    onError = ZiyOnErrorDark,
    surfaceContainerLowest = ZiySurfaceContainerLowestDark,
    surfaceContainerLow = ZiySurfaceContainerLowDark,
    surfaceContainer = ZiySurfaceContainerDark,
    surfaceContainerHigh = ZiySurfaceContainerHighDark,
    surfaceContainerHighest = ZiySurfaceContainerHighestDark,
)

@Composable
fun ZiyOfficeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ZiyTypography,
        content = content,
    )
}
