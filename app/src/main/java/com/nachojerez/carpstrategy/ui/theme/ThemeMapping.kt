package com.nachojerez.carpstrategy.ui.theme

import com.nachojerez.carpstrategy.domain.model.AppStyle
import com.nachojerez.carpstrategy.domain.model.AppThemeMode
import com.nachojerez.carpstrategy.domain.model.AppearanceSettings

/** Ajustes guardados → preferencias del tema del diseño. */
fun AppearanceSettings.toThemePrefs(): ThemePrefs = ThemePrefs(
    style = when (style) {
        AppStyle.MATERIAL -> DesignStyle.Material
        AppStyle.APPLE -> DesignStyle.Apple
    },
    mode = when (mode) {
        AppThemeMode.SYSTEM -> ThemeMode.System
        AppThemeMode.LIGHT -> ThemeMode.Light
        AppThemeMode.DARK -> ThemeMode.Dark
    },
    dynamicColor = dynamicColor,
)
