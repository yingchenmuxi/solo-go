package com.sologo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZenLightColors = lightColorScheme(
    primary = ZenCinnabar,
    onPrimary = ZenPaperSoft,
    primaryContainer = ZenCinnabarSoft,
    onPrimaryContainer = ZenInk,
    secondary = ZenBamboo,
    onSecondary = ZenPaperSoft,
    secondaryContainer = Color(0xFFE4E8DD),
    onSecondaryContainer = ZenInk,
    tertiary = Color(0xFF9A6B3F),
    onTertiary = ZenPaperSoft,
    background = ZenPaper,
    onBackground = ZenInk,
    surface = ZenPaperSoft,
    onSurface = ZenInk,
    surfaceVariant = ZenPaperDeep,
    onSurfaceVariant = ZenInkSoft,
    outline = ZenOutline,
    outlineVariant = Color(0xFFD8CABB)
)

private val ZenDarkColors = darkColorScheme(
    primary = ZenDarkCinnabar,
    onPrimary = Color(0xFF351710),
    primaryContainer = ZenDarkCinnabarSoft,
    onPrimaryContainer = ZenDarkInk,
    secondary = ZenDarkBamboo,
    onSecondary = Color(0xFF20281A),
    secondaryContainer = Color(0xFF3A4332),
    onSecondaryContainer = ZenDarkInk,
    tertiary = ZenWood,
    onTertiary = Color(0xFF2C1D0D),
    background = ZenDarkPaper,
    onBackground = ZenDarkInk,
    surface = ZenDarkSurface,
    onSurface = ZenDarkInk,
    surfaceVariant = ZenDarkSurfaceVariant,
    onSurfaceVariant = ZenDarkInkSoft,
    outline = ZenDarkOutline,
    outlineVariant = Color(0xFF4B3E33)
)

/** 全局使用固定品牌色，不跟随 Android 动态取色。 */
@Composable
fun SoloGoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ZenDarkColors else ZenLightColors,
        typography = Typography,
        content = content
    )
}
