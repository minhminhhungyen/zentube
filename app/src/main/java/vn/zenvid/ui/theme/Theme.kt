package vn.zenvid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Màu nhận diện của Zenvid: xanh ngọc (khác hẳn màu đỏ của YouTube). */
val Brand = Color(0xFF12B886)
/** Nhãn "TRỰC TIẾP" vẫn dùng màu đỏ quen thuộc. */
val LiveRed = Color(0xFFE53935)

private val Dark = darkColorScheme(
    primary = Color(0xFFF1F1F1),
    onPrimary = Color(0xFF0F0F0F),
    secondary = Brand,
    onSecondary = Color.White,
    background = Color(0xFF0F0F0F),
    onBackground = Color(0xFFF1F1F1),
    surface = Color(0xFF0F0F0F),
    onSurface = Color(0xFFF1F1F1),
    surfaceVariant = Color(0xFF272727),
    onSurfaceVariant = Color(0xFFAAAAAA),
    surfaceContainer = Color(0xFF212121),
    surfaceContainerHigh = Color(0xFF282828),
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainerHighest = Color(0xFF303030),
    secondaryContainer = Color(0xFF3A3A3A),
    onSecondaryContainer = Color(0xFFF1F1F1),
    outline = Color(0xFF3F3F3F),
    outlineVariant = Color(0xFF303030),
    error = Color(0xFFFF6B6B),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0F0F0F),
    onPrimary = Color.White,
    secondary = Brand,
    onSecondary = Color.White,
    background = Color.White,
    onBackground = Color(0xFF0F0F0F),
    surface = Color.White,
    onSurface = Color(0xFF0F0F0F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF606060),
    surfaceContainer = Color(0xFFF2F2F2),
    surfaceContainerHigh = Color(0xFFECECEC),
    surfaceContainerLow = Color(0xFFF8F8F8),
    surfaceContainerHighest = Color(0xFFE5E5E5),
    secondaryContainer = Color(0xFFE5E5E5),
    onSecondaryContainer = Color(0xFF0F0F0F),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFE5E5E5),
    error = Color(0xFFCC0000),
)

@Composable
fun ZenvidTheme(theme: String, content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
