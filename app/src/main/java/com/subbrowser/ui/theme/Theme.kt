package com.subbrowser.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SubDarkColorScheme = darkColorScheme(
    primary = SubSaffron,
    onPrimary = SubBlack,
    background = SubBlack,
    onBackground = SubTextPrimary,
    surface = SubSurface,
    onSurface = SubTextPrimary,
    surfaceVariant = SubSurfaceElevated,
    onSurfaceVariant = SubTextSecondary
)

@Composable
fun SubBrowserTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SubDarkColorScheme,
        typography = SubTypography,
        content = content
    )
}
