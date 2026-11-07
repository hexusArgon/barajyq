package kz.astana.afisha.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Teal = Color(0xFF0E7C70)
val TealDark = Color(0xFF075A51)
val Amber = Color(0xFFE8863A)

private val scheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFE8),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2CC),
    onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF4A6378),
    tertiaryContainer = Color(0xFFD7E6F5),
    onTertiaryContainer = Color(0xFF0B1D2B),
    background = Color(0xFFF8F6F2),
    onBackground = Color(0xFF1A1C1B),
    surface = Color(0xFFF8F6F2),
    onSurface = Color(0xFF1A1C1B),
    surfaceVariant = Color(0xFFE4E9E6),
    onSurfaceVariant = Color(0xFF5A635F),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF1EFEA),
    surfaceContainerHigh = Color(0xFFEBE9E4),
    surfaceContainerHighest = Color(0xFFE5E3DE),
    outline = Color(0xFF8A938F),
    outlineVariant = Color(0xFFD6DBD8),
    error = Color(0xFFBA1A1A),
)

private val base = Typography()
private val typography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AstanaAfishaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
