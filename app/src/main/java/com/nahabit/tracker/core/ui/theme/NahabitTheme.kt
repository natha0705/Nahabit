package com.nahabit.tracker.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nahabit.tracker.core.datastore.AppThemeMode

val NahabitCyan = Color(0xFF38BDF8)
val NahabitEmerald = Color(0xFF10B981)
val NahabitAmber = Color(0xFFF59E0B)
val NahabitCoral = Color(0xFFF43F5E)

private val LightColors = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F5F9)
)

private val DarkColors = darkColorScheme(
    primary = NahabitCyan,
    onPrimary = Color(0xFF082F49),
    primaryContainer = Color(0xFF0369A1),
    onPrimaryContainer = Color(0xFFE0F2FE),
    secondary = NahabitEmerald,
    onSecondary = Color(0xFF064E3B),
    secondaryContainer = Color(0xFF047857),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFF334155)
)

val NahabitShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

val NahabitTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    )
)

val LocalCardSpacing = staticCompositionLocalOf { 16.dp }

@Composable
fun NahabitTheme(
    themeMode: AppThemeMode = AppThemeMode.AMOLED,
    dynamicColor: Boolean = true,
    isCompactMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()

    val useDark = when (themeMode) {
        AppThemeMode.SYSTEM -> systemDark
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK, AppThemeMode.AMOLED -> true
    }

    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> DarkColors
        else -> LightColors
    }

    val finalColorScheme = if (themeMode == AppThemeMode.AMOLED) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color(0xFF090D14),
            surfaceVariant = Color(0xFF131A26)
        )
    } else {
        baseScheme
    }

    val cardSpacing: Dp = if (isCompactMode) 10.dp else 16.dp

    CompositionLocalProvider(LocalCardSpacing provides cardSpacing) {
        MaterialTheme(
            colorScheme = finalColorScheme,
            shapes = NahabitShapes,
            typography = NahabitTypography,
            content = content
        )
    }
}
