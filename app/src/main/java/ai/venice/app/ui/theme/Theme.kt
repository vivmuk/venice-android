package ai.venice.app.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val VeniceObsidian = Color(0xFF07080C)
val VeniceSurface = Color(0xFF0E1118)
val VeniceSurfaceElevated = Color(0xFF151925)
val VeniceCard = Color(0xFF1A2030)
val VeniceBorder = Color(0xFF252D42)

val VeniceCyan = Color(0xFF00F0FF)
val VeniceViolet = Color(0xFF8A2BE2)
val VeniceMagenta = Color(0xFFFF2A85)
val VeniceMint = Color(0xFF00E676)
val VeniceAmber = Color(0xFFFFB300)
val VeniceRed = Color(0xFFFF4D6D)

val TextPrimary = Color(0xFFF3F6FC)
val TextSecondary = Color(0xFF9BA4B8)
val TextMuted = Color(0xFF636D82)

private val VeniceDarkColorScheme = darkColorScheme(
    primary = VeniceCyan,
    onPrimary = VeniceObsidian,
    primaryContainer = Color(0xFF00363D),
    onPrimaryContainer = VeniceCyan,
    secondary = VeniceMagenta,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF3B122B),
    onSecondaryContainer = VeniceMagenta,
    tertiary = VeniceViolet,
    background = VeniceObsidian,
    onBackground = TextPrimary,
    surface = VeniceSurface,
    onSurface = TextPrimary,
    surfaceVariant = VeniceSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = VeniceBorder,
    error = VeniceRed
)

val VeniceTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = (-0.2).sp,
        color = TextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = TextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = TextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = TextSecondary
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        color = TextSecondary
    )
)

@Composable
fun rememberFuturisticGradientBrush(durationMillis: Int = 4500): Brush {
    val transition = rememberInfiniteTransition(label = "venice_gradient")
    val offset by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradient_offset"
    )
    return Brush.linearGradient(
        colors = listOf(VeniceCyan, VeniceViolet, VeniceMagenta, VeniceCyan),
        start = Offset(offset - 600f, 0f),
        end = Offset(offset + 600f, 400f)
    )
}

@Composable
fun VeniceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VeniceDarkColorScheme,
        typography = VeniceTypography,
        content = content
    )
}
