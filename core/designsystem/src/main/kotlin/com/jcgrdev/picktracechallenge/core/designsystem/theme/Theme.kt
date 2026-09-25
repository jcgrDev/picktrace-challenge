package com.jcgrdev.picktracechallenge.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = FieldGreen,
    secondary = SoilBrown,
    secondaryContainer = SoilBrownLight,
)

private val DarkColors = darkColorScheme(
    primary = FieldGreenLight,
    secondary = SoilBrownLight,
)

/** Colors Material 3 has no slot for. */
@Immutable
data class ExtendedColors(val successContainer: Color, val onSuccessContainer: Color)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(SuccessContainerLight, OnSuccessContainerLight)
}

@Composable
fun PicktraceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val extended = if (darkTheme) {
        ExtendedColors(SuccessContainerDark, OnSuccessContainerDark)
    } else {
        ExtendedColors(SuccessContainerLight, OnSuccessContainerLight)
    }
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = PicktraceTypography,
            content = content,
        )
    }
}
