package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DairySkyAccent,
    onPrimary = EarthDarkBackground,
    secondary = ButterGoldSecondary,
    onSecondary = EarthDarkBackground,
    tertiary = DairySkyAccent,
    background = EarthDarkBackground,
    onBackground = MilkLightText,
    surface = CocoaDarkSurface,
    onSurface = MilkLightText,
    primaryContainer = DairyBluePrimary,
    onPrimaryContainer = MilkLightText
)

private val LightColorScheme = lightColorScheme(
    primary = DairyBluePrimary,
    onPrimary = Color.White,
    secondary = ButterGoldSecondary,
    onSecondary = CoffeeDarkText,
    tertiary = DairySkyAccent,
    onTertiary = CoffeeDarkText,
    background = CreamLightBackground,
    onBackground = CoffeeDarkText,
    surface = EggshellLightSurface,
    onSurface = CoffeeDarkText,
    primaryContainer = Color(0xFFE0F2FE), // Super soft milk blue accent container
    onPrimaryContainer = DairyBluePrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // We disable dynamicColor by default to enforce our beautiful cohesive dairy farm identity
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
