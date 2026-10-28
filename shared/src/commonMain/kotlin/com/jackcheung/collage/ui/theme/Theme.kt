package com.jackcheung.collage.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.jackcheung.collage.platform.platformColorScheme

private val LightColors = lightColorScheme(primary = Color(0xFF5B4BDB), secondary = Color(0xFFE0457B))
private val DarkColors = darkColorScheme(primary = Color(0xFFC4BBFF), secondary = Color(0xFFFFB1C8))

@Composable
fun CollageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = platformColorScheme(dark) ?: if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
