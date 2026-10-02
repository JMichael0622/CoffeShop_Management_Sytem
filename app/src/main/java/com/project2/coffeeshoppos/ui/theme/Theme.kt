package com.project2.coffeeshoppos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = CoffeeBrown,
    onPrimary = Cream,
    primaryContainer = LatteDeep,
    onPrimaryContainer = Espresso,
    secondary = CaramelDark,
    onSecondary = Color.White,
    secondaryContainer = CaramelLight,
    onSecondaryContainer = Espresso,
    tertiary = Caramel,
    onTertiary = Espresso,
    background = Cream,
    onBackground = Espresso,
    surface = Cream,
    onSurface = Espresso,
    surfaceVariant = Latte,
    onSurfaceVariant = Mocha,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = CreamSurface,
    surfaceContainer = CreamSurface,
    surfaceContainerHigh = Latte,
    surfaceContainerHighest = LatteDeep,
    outline = Foam,
    outlineVariant = Latte,
)

private val DarkColorScheme = darkColorScheme(
    primary = CaramelGlow,
    onPrimary = Espresso,
    primaryContainer = RoastHighest,
    onPrimaryContainer = SteamedMilk,
    secondary = CaramelGlow,
    onSecondary = Espresso,
    secondaryContainer = RoastHighest,
    onSecondaryContainer = SteamedMilk,
    tertiary = Caramel,
    onTertiary = Espresso,
    background = Roast,
    onBackground = SteamedMilk,
    surface = Roast,
    onSurface = SteamedMilk,
    surfaceVariant = RoastHigh,
    onSurfaceVariant = LatteMuted,
    surfaceContainerLowest = Roast,
    surfaceContainerLow = RoastSurface,
    surfaceContainer = RoastSurface,
    surfaceContainerHigh = RoastHigh,
    surfaceContainerHighest = RoastHighest,
    outline = RoastOutline,
    outlineVariant = RoastHigh,
)

/** Extra colors that Material 3 does not provide, such as the low-stock warning color. */
data class CoffeeExtraColors(val warning: Color)

val LocalCoffeeExtraColors = staticCompositionLocalOf { CoffeeExtraColors(warning = WarningAmber) }

@Composable
fun CoffeeShopPOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Dynamic (wallpaper-based) color is intentionally not used so the coffee palette is always shown.
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extraColors = CoffeeExtraColors(warning = if (darkTheme) WarningAmberDark else WarningAmber)

    CompositionLocalProvider(LocalCoffeeExtraColors provides extraColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = Shapes,
            content = content
        )
    }
}
