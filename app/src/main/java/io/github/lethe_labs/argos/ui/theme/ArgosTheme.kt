package io.github.lethe_labs.argos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.lethe_labs.argos.R

/** The palette of the emblem: night blue, watchful cyan, seal gold. */
object ArgosColors {
    val Night = Color(0xFF0E151E)
    val Surface = Color(0xFF162130)
    val Line = Color(0xFF24364A)
    val Watch = Color(0xFF4FD1E0)
    val Gold = Color(0xFFD2B271)
    val OnGold = Color(0xFF1A1408)
    val Warning = Color(0xFFF0A63C)
    val Critical = Color(0xFFEF6A5E)
    val Text = Color(0xFFE8EFF4)
    val Muted = Color(0xFF93A4B5)
    val Asleep = Color(0xFF3A4D61)
}

/**
 * Serif for what the user has to recognise (the words, the dates, the
 * verdict), sans for everything else. Both are bundled, since the app has no
 * network access: static instances of Cormorant Garamond and Manrope (OFL,
 * see assets/licenses), cut down to Latin.
 */
object ArgosType {
    val Serif = FontFamily(Font(R.font.cormorant_garamond_semibold, FontWeight.SemiBold))
    val Sans = FontFamily(
        Font(R.font.manrope_regular, FontWeight.Normal),
        Font(R.font.manrope_medium, FontWeight.Medium),
        Font(R.font.manrope_semibold, FontWeight.SemiBold),
        Font(R.font.manrope_bold, FontWeight.Bold),
    )

    val Brand = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 6.sp)
    val Title = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 36.sp)
    val Display = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 31.sp)
    val Word = TextStyle(fontFamily = Serif, fontWeight = FontWeight.SemiBold, fontSize = 28.sp)
    val Body = TextStyle(fontFamily = Sans, fontSize = 15.sp, lineHeight = 22.sp)
    val Small = TextStyle(fontFamily = Sans, fontSize = 13.sp, lineHeight = 19.sp)
    val Label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 1.5.sp)
    val Strong = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    val Button = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 17.sp)
}

/**
 * Always dark: it matches the emblem, and a dark screen draws less attention
 * when the report is opened in public.
 */
@Composable
fun ArgosTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ArgosColors.Gold,
            onPrimary = ArgosColors.OnGold,
            secondary = ArgosColors.Watch,
            tertiary = ArgosColors.Warning,
            error = ArgosColors.Critical,
            background = ArgosColors.Night,
            onBackground = ArgosColors.Text,
            surface = ArgosColors.Night,
            onSurface = ArgosColors.Text,
            surfaceVariant = ArgosColors.Surface,
            onSurfaceVariant = ArgosColors.Muted,
            outline = ArgosColors.Line,
        ),
        typography = Typography(bodyLarge = ArgosType.Body, bodyMedium = ArgosType.Body),
        content = content,
    )
}
