package app.flicky.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightTheme = lightColorScheme(
    primary = Color(0xFF6200EA),          // Deep Purple
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE1BEE7),  // Light Purple
    onPrimaryContainer = Color(0xFF3700B3),

    secondary = Color(0xFF03DAC5),        // Teal
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00574B),

    tertiary = Color(0xFFFF6E40),         // Deep Orange
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFCCBC),
    onTertiaryContainer = Color(0xFFBF360C),

    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1C1B1F),

    surface = Color(0xFFFDF7FF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EB),
    onSurfaceVariant = Color(0xFF49454E),
    surfaceTint = Color(0xFF6200EA),

    surfaceBright = Color(0xFFFDF7FF),
    surfaceDim = Color(0xFFDED8E1),

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),

    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),

    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4CF),

    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF),
)

private val DarkTheme = darkColorScheme(
    primary = Color(0xFF82B1FF),          // Light Blue
    onPrimary = Color(0xFF003C8F),
    primaryContainer = Color(0xFF004BA0),
    onPrimaryContainer = Color(0xFFD1E4FF),
    
    secondary = Color(0xFF64FFDA),        // Cyan
    onSecondary = Color(0xFF003E2F),
    secondaryContainer = Color(0xFF005142),
    onSecondaryContainer = Color(0xFF82F7D5),
    
    tertiary = Color(0xFFFF8A65),         // Light Deep Orange
    onTertiary = Color(0xFF5D1F00),
    tertiaryContainer = Color(0xFF863200),
    onTertiaryContainer = Color(0xFFFFDBCF),
    
    background = Color(0xFF0A1929),       // Deep Midnight Blue
    onBackground = Color(0xFFE3F2FD),

    surface = Color(0xFF0A1929),
    onSurface = Color(0xFFE3F2FD),
    surfaceVariant = Color(0xFF1E3A5F),   // Blue-ish surface
    onSurfaceVariant = Color(0xFFBBDEFB),
    surfaceTint = Color(0xFF82B1FF),

    surfaceBright = Color(0xFF364251),
    surfaceDim = Color(0xFF0A1929),

    surfaceContainerLowest = Color(0xFF050E17),
    surfaceContainerLow = Color(0xFF0E1D2E),
    surfaceContainer = Color(0xFF132238),
    surfaceContainerHigh = Color(0xFF1E2D43),
    surfaceContainerHighest = Color(0xFF28384E),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF8FA4C0),
    outlineVariant = Color(0xFF43474E),

    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE3F2FD),
    inverseOnSurface = Color(0xFF0A1929),
    inversePrimary = Color(0xFF0062A2),
)

@Composable
fun FlickyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalView.current.context
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkTheme
        else -> LightTheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            WindowCompat.setDecorFitsSystemWindows(window, false)

            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme

            // For older API levels
            if (Build.VERSION.SDK_INT < 35) {
                @Suppress("DEPRECATION")
                window.statusBarColor = colorScheme.surface.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = colorScheme.surface.toArgb()
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = colorScheme.surface
        ) {
            content()
        }
    }
}