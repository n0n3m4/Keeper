package com.example.keeper.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// One fixed Google Keep-style light theme. No Material You (wallpaper) colors
// and no dark mode, so the app looks identical on every device — predictable
// for an elderly user who relies on familiarity.
private val KeepColors = lightColorScheme(
    primary = KeepInk,
    onPrimary = KeepWhite,
    primaryContainer = KeepYellow,        // FAB
    onPrimaryContainer = KeepInk,
    secondary = KeepInk,
    onSecondary = KeepWhite,
    secondaryContainer = KeepYellow,      // selected drawer item
    onSecondaryContainer = KeepInk,
    background = KeepWhite,
    onBackground = KeepInk,
    surface = KeepWhite,
    onSurface = KeepInk,
    surfaceVariant = KeepGreyBg,
    onSurfaceVariant = KeepGreyText,
    outline = KeepGreyText,
    outlineVariant = KeepBorder,
)

@Composable
fun KeeperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KeepColors, typography = Typography, content = content)
}
