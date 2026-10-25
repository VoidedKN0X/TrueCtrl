package com.vknxworks.truectrl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.vknxworks.truectrl.R

/**
 * Ctrl / Signal: the shared look of the VKNXWorks Ctrl apps. Dark only, high contrast, one accent.
 * Change [Accent] to recolour the whole app.
 */
object CtrlColors {
    /** The one accent colour. Everything "active" or "selected" uses it. */
    val Accent = Color(0xFFB48CFF)
    val OnAccent = Color(0xFF08080A)

    val Background = Color(0xFF08080A)
    val Surface = Color(0xFF141417)
    val Raised = Color(0xFF1C1C21)
    val CardBorder = Color(0xFF232328)
    val ControlBorder = Color(0xFF2A2A2F)
    val Divider = Color(0xFF1E1E23)
    val Text = Color(0xFFF4F4F2)
    val Muted = Color(0xFFA3A3AB)
    val Inactive = Color(0xFF8C8C94)
    val Track = Color(0xFF26262C)
    val SwitchOff = Color(0xFF2A2A30)
    val PinEmpty = Color(0xFF3A3A41)
    val Error = Color(0xFFFF6B6B)
    val Warn = Color(0xFFF2C94C)

    val CardStroke = BorderStroke(1.dp, CardBorder)
    val ControlStroke = BorderStroke(1.dp, ControlBorder)
}

/** Corner radii of the Ctrl look. */
object CtrlShapes {
    val Card = RoundedCornerShape(22.dp)
    val Hero = RoundedCornerShape(26.dp)
    val Dialog = RoundedCornerShape(26.dp)
    val Sheet = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    val Control = RoundedCornerShape(14.dp)
    val Button = RoundedCornerShape(18.dp)
    val Field = RoundedCornerShape(16.dp)
    val Badge = RoundedCornerShape(10.dp)
    val Dot = RoundedCornerShape(2.dp)
}

/** TrueCtrl's accent (kept for older call sites). */
val TrueAccent = CtrlColors.Accent

private val Scheme = darkColorScheme(
    primary = CtrlColors.Accent,
    onPrimary = CtrlColors.OnAccent,
    primaryContainer = CtrlColors.Accent,
    onPrimaryContainer = CtrlColors.OnAccent,
    inversePrimary = CtrlColors.Accent,
    secondary = CtrlColors.Text,
    onSecondary = CtrlColors.OnAccent,
    secondaryContainer = CtrlColors.Accent,
    onSecondaryContainer = CtrlColors.OnAccent,
    tertiary = CtrlColors.Text,
    onTertiary = CtrlColors.OnAccent,
    tertiaryContainer = CtrlColors.Raised,
    onTertiaryContainer = CtrlColors.Text,
    background = CtrlColors.Background,
    onBackground = CtrlColors.Text,
    surface = CtrlColors.Surface,
    onSurface = CtrlColors.Text,
    surfaceVariant = CtrlColors.Raised,
    onSurfaceVariant = CtrlColors.Muted,
    surfaceTint = Color.Transparent,
    inverseSurface = CtrlColors.Text,
    inverseOnSurface = CtrlColors.Background,
    surfaceBright = CtrlColors.Raised,
    surfaceDim = CtrlColors.Background,
    surfaceContainerLowest = CtrlColors.Background,
    surfaceContainerLow = CtrlColors.Surface,
    surfaceContainer = CtrlColors.Surface,
    surfaceContainerHigh = CtrlColors.Raised,
    surfaceContainerHighest = CtrlColors.Track,
    outline = CtrlColors.ControlBorder,
    outlineVariant = CtrlColors.Divider,
    error = CtrlColors.Error,
    onError = CtrlColors.OnAccent,
    errorContainer = Color(0xFF2A1517),
    onErrorContainer = Color(0xFFFFD6D6),
)

// Space Grotesk for all UI text.
val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_regular, FontWeight.Normal),
    Font(R.font.space_grotesk_medium, FontWeight.Medium),
    Font(R.font.space_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.space_grotesk_bold, FontWeight.Bold),
)

// JetBrains Mono for numbers, data readouts, section labels and the wordmark.
val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = SpaceGrotesk,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
)

private val AppTypography = Typography(
    displayLarge = style(56, 56, FontWeight.Bold, -0.04),
    displayMedium = style(44, 46, FontWeight.Bold, -0.035),
    displaySmall = style(36, 40, FontWeight.Bold, -0.035),
    headlineLarge = style(40, 44, FontWeight.Bold, -0.035),
    headlineMedium = style(28, 34, FontWeight.Bold, -0.02),
    headlineSmall = style(24, 30, FontWeight.Bold, -0.02),
    titleLarge = style(18, 24, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 21, FontWeight.Normal),
    bodySmall = style(12, 17, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.SemiBold),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 14, FontWeight.Medium),
)

/** Text styles outside the Material scale. */
object CtrlType {
    /** Section headers: JetBrains Mono 11sp, upper case (call `.uppercase()`), wide tracking, muted. */
    val Label = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.16.em)
    /** Mono readouts in rows and meta lines. */
    val Mono = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    /** Trailing status words in lists (RUN, ONLINE). */
    val Status = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.em)
    /** The NAME/CTRL wordmark. */
    val Wordmark = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.14.em)
    /** Big numeric readouts on hero cards. */
    val BigNumber = TextStyle(fontFamily = SpaceGrotesk, fontSize = 64.sp, lineHeight = 60.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.05).em)
    /** Values on metric cards. */
    val Metric = TextStyle(fontFamily = SpaceGrotesk, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.03).em)
    /** Lock screen keys. */
    val Key = TextStyle(fontFamily = JetBrainsMono, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(9.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** Horizontal page margin used everywhere. */
val Gutter = 20.dp

/**
 * The Ctrl / Signal theme. Dark only: the old theme and wallpaper-colour settings are kept in storage
 * but no longer change anything.
 */
@Composable
fun TrueCtrlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = AppTypography, shapes = AppShapes, content = content)
}

/**
 * Health colours for usage bars, status dots and the attention list. Fine is the accent,
 * stopped or unknown is grey, so a warning or an error always stands out.
 */
object Health {
    val ok = CtrlColors.Accent
    val warn = CtrlColors.Warn
    val critical = CtrlColors.Error
    val idle = CtrlColors.Inactive
}
