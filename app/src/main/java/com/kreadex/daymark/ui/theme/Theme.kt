package com.kreadex.daymark.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.kreadex.daymark.data.AppPreferences

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Yellow80,

    background = Color(0xFF0F172A),

)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Orange40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun DaymarkTheme(
    context: Context = LocalContext.current,
    prefs: AppPreferences = remember { AppPreferences(context) },
    darkTheme: Boolean = prefs.isActuallyDark(context),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val targetColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val animatedColorScheme = animateColorScheme(targetColorScheme)

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}
@Composable
fun animateColorScheme(targetScheme: ColorScheme): ColorScheme {
    val duration = 500
    val tween = tween<Color>(duration)

    @Composable
    fun animateColor(target: Color) = animateColorAsState(target, tween).value

    return targetScheme.copy(
        primary = animateColor(targetScheme.primary),
        onPrimary = animateColor(targetScheme.onPrimary),
        primaryContainer = animateColor(targetScheme.primaryContainer),
        onPrimaryContainer = animateColor(targetScheme.onPrimaryContainer),
        secondary = animateColor(targetScheme.secondary),
        onSecondary = animateColor(targetScheme.onSecondary),
        background = animateColor(targetScheme.background),
        onBackground = animateColor(targetScheme.onBackground),
        surface = animateColor(targetScheme.surface),
        onSurface = animateColor(targetScheme.onSurface),
        surfaceVariant = animateColor(targetScheme.surfaceVariant),
        onSurfaceVariant = animateColor(targetScheme.onSurfaceVariant),
        surfaceContainer = animateColor(targetScheme.surfaceContainer),
        surfaceTint = animateColor(targetScheme.surfaceTint),
        outline = animateColor(targetScheme.outline)
    )
}