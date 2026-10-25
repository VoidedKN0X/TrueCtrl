package com.vknxworks.truectrl.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
 * One Ctrl / Signal palette. There are two, [CtrlColors.Dark] and [CtrlColors.Light]; the app
 * follows the phone's dark / light setting and composables read the current one through [CtrlColors].
 */
@Immutable
data class CtrlPalette(
    val isDark: Boolean,
    /** Accent as a fill: hero cards, main buttons, the selected tab, switch tracks, PIN cells. */
    val Accent: Color,
    /** Content on an accent fill. */
    val OnAccent: Color,
    /** Accent for text and icons on the page or a card (darker in light mode, so it stays readable). */
    val AccentText: Color,
    val Background: Color,
    val Surface: Color,
    val Raised: Color,
    val CardBorder: Color,
    val ControlBorder: Color,
    val Divider: Color,
    val Text: Color,
    val Muted: Color,
    val Inactive: Color,
    val Track: Color,
    val SwitchOff: Color,
    val SwitchKnobOff: Color,
    val PinEmpty: Color,
    val Error: Color,
    val OnError: Color,
    val ErrorContainer: Color,
    val OnErrorContainer: Color,
    val Warn: Color,
    /** The floating bottom bar's fill (used at about 92 % opacity). */
    val Bar: Color,
    val Scrim: Color,
) {
    val CardStroke: BorderStroke get() = BorderStroke(1.dp, CardBorder)
    val ControlStroke: BorderStroke get() = BorderStroke(1.dp, ControlBorder)
}

/** The current palette. Provided by [TrueCtrlTheme]. */
val LocalCtrlColors = staticCompositionLocalOf { CtrlColors.Dark }

/**
 * Ctrl / Signal colours. `CtrlColors.Surface` etc. read the palette of the current theme; the
 * accent is defined once, in [ACCENT], and shared by both palettes.
 */
object CtrlColors {
    /** The one accent colour. Change it here to recolour the whole app. */
    val ACCENT = Color(0xFFB48CFF)
    private val INK = Color(0xFF08080A)

    val Dark = CtrlPalette(
        isDark = true,
        Accent = ACCENT,
        OnAccent = INK,
        AccentText = ACCENT,
        Background = Color(0xFF08080A),
        Surface = Color(0xFF141417),
        Raised = Color(0xFF1C1C21),
        CardBorder = Color(0xFF232328),
        ControlBorder = Color(0xFF2A2A2F),
        Divider = Color(0xFF1E1E23),
        Text = Color(0xFFF4F4F2),
        Muted = Color(0xFFA3A3AB),
        Inactive = Color(0xFF8C8C94),
        Track = Color(0xFF26262C),
        SwitchOff = Color(0xFF2A2A30),
        SwitchKnobOff = Color(0xFF8C8C94),
        PinEmpty = Color(0xFF3A3A41),
        Error = Color(0xFFFF6B6B),
        OnError = INK,
        ErrorContainer = Color(0xFF2A1517),
        OnErrorContainer = Color(0xFFFFD6D6),
        Warn = Color(0xFFF2C94C),
        Bar = Color(0xFF141417),
        Scrim = Color(0x99000000),
    )

    val Light = CtrlPalette(
        isDark = false,
        Accent = ACCENT,
        OnAccent = INK,
        AccentText = Color(0xFF6A3BDB),
        Background = Color(0xFFF5F5F1),
        Surface = Color(0xFFFFFFFF),
        Raised = Color(0xFFECECE7),
        CardBorder = Color(0xFFDEDED7),
        ControlBorder = Color(0xFFD2D2CA),
        Divider = Color(0xFFE6E6E0),
        Text = Color(0xFF0B0B0D),
        Muted = Color(0xFF5C5C64),
        Inactive = Color(0xFF74747C),
        Track = Color(0xFFE2E2DC),
        SwitchOff = Color(0xFFDCDCD5),
        SwitchKnobOff = Color(0xFF74747C),
        PinEmpty = Color(0xFFC9C9C1),
        Error = Color(0xFFD93636),
        OnError = Color(0xFFFFFFFF),
        ErrorContainer = Color(0xFFFBE9E9),
        OnErrorContainer = Color(0xFF5C1111),
        Warn = Color(0xFFA86A00),
        Bar = Color(0xFFFFFFFF),
        Scrim = Color(0x66000000),
    )

    private val current: CtrlPalette @Composable @ReadOnlyComposable get() = LocalCtrlColors.current

    val isDark: Boolean @Composable @ReadOnlyComposable get() = current.isDark
    val Accent: Color @Composable @ReadOnlyComposable get() = current.Accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = current.OnAccent
    val AccentText: Color @Composable @ReadOnlyComposable get() = current.AccentText
    val Background: Color @Composable @ReadOnlyComposable get() = current.Background
    val Surface: Color @Composable @ReadOnlyComposable get() = current.Surface
    val Raised: Color @Composable @ReadOnlyComposable get() = current.Raised
    val CardBorder: Color @Composable @ReadOnlyComposable get() = current.CardBorder
    val ControlBorder: Color @Composable @ReadOnlyComposable get() = current.ControlBorder
    val Divider: Color @Composable @ReadOnlyComposable get() = current.Divider
    val Text: Color @Composable @ReadOnlyComposable get() = current.Text
    val Muted: Color @Composable @ReadOnlyComposable get() = current.Muted
    val Inactive: Color @Composable @ReadOnlyComposable get() = current.Inactive
    val Track: Color @Composable @ReadOnlyComposable get() = current.Track
    val SwitchOff: Color @Composable @ReadOnlyComposable get() = current.SwitchOff
    val SwitchKnobOff: Color @Composable @ReadOnlyComposable get() = current.SwitchKnobOff
    val PinEmpty: Color @Composable @ReadOnlyComposable get() = current.PinEmpty
    val Error: Color @Composable @ReadOnlyComposable get() = current.Error
    val Warn: Color @Composable @ReadOnlyComposable get() = current.Warn
    val Bar: Color @Composable @ReadOnlyComposable get() = current.Bar
    val Scrim: Color @Composable @ReadOnlyComposable get() = current.Scrim
    val CardStroke: BorderStroke @Composable @ReadOnlyComposable get() = current.CardStroke
    val ControlStroke: BorderStroke @Composable @ReadOnlyComposable get() = current.ControlStroke
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

private fun scheme(c: CtrlPalette): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        // Material's own text buttons, checkboxes, spinners and selection handles use primary, so it is
        // the readable accent shade. Ctrl fills (hero cards, main buttons, switches) use CtrlColors.Accent.
        primary = c.AccentText,
        onPrimary = if (c.isDark) c.OnAccent else Color.White,
        primaryContainer = c.Accent,
        onPrimaryContainer = c.OnAccent,
        inversePrimary = c.AccentText,
        secondary = c.Text,
        onSecondary = c.Background,
        secondaryContainer = c.Accent,
        onSecondaryContainer = c.OnAccent,
        // Accent for text and icons (links, text buttons): readable on light backgrounds too.
        tertiary = c.AccentText,
        onTertiary = c.Background,
        tertiaryContainer = c.Raised,
        onTertiaryContainer = c.Text,
        background = c.Background,
        onBackground = c.Text,
        surface = c.Surface,
        onSurface = c.Text,
        surfaceVariant = c.Raised,
        onSurfaceVariant = c.Muted,
        surfaceTint = Color.Transparent,
        inverseSurface = c.Text,
        inverseOnSurface = c.Background,
        surfaceBright = c.Raised,
        surfaceDim = c.Background,
        surfaceContainerLowest = c.Background,
        surfaceContainerLow = c.Surface,
        surfaceContainer = c.Surface,
        surfaceContainerHigh = c.Raised,
        surfaceContainerHighest = c.Track,
        outline = c.ControlBorder,
        outlineVariant = c.Divider,
        error = c.Error,
        onError = c.OnError,
        errorContainer = c.ErrorContainer,
        onErrorContainer = c.OnErrorContainer,
        scrim = Color.Black,
    )
}

private val DarkScheme = scheme(CtrlColors.Dark)
private val LightScheme = scheme(CtrlColors.Light)

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
 * The Ctrl / Signal theme. Follows the phone's dark / light setting; there is no in-app theme
 * choice and no wallpaper colours (the old settings are kept in storage but not used).
 */
@Composable
fun TrueCtrlTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) CtrlColors.Dark else CtrlColors.Light
    CompositionLocalProvider(LocalCtrlColors provides palette) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, shapes = AppShapes) {
            CompositionLocalProvider(LocalContentColor provides palette.Text, content = content)
        }
    }
}

/** Forces the dark palette for content that stays dark in both modes (the shell, a terminal). */
@Composable
fun AlwaysDark(content: @Composable () -> Unit) {
    if (LocalCtrlColors.current.isDark) content() else TrueCtrlTheme(dark = true, content = content)
}

/**
 * Health colours for usage bars, status dots and the attention list. Fine is the accent (as text
 * colour, so it reads on both backgrounds), stopped or unknown is grey, so a warning or an error
 * always stands out.
 */
object Health {
    val ok: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.AccentText
    val warn: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Warn
    val critical: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Error
    val idle: Color @Composable @ReadOnlyComposable get() = LocalCtrlColors.current.Inactive
}
