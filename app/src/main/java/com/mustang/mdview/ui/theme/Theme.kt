package com.mustang.mdview.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Use the same black and purple theme for the bookshelf, loading states, and reader controls. */
@Composable
fun MustangTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFFB69CFF), onPrimary = Color(0xFF211333),
        secondary = Color(0xFFD3C5EF), background = Color(0xFF121212),
        surface = Color(0xFF121212), surfaceContainer = Color(0xFF211C2B),
        onBackground = Color(0xFFE6E6E6), onSurface = Color(0xFFE6E6E6),
        onSurfaceVariant = Color(0xFFB3B3B3),
    ), content = content)
}
